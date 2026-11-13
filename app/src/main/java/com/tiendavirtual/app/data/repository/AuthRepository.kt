package com.tiendavirtual.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.util.AppException
import com.tiendavirtual.app.util.Session
import kotlinx.coroutines.tasks.await

/**
 * Autenticación con Firebase Authentication (correo y contraseña).
 * Después de autenticar, el rol y el estado del usuario se leen de su
 * documento en Firestore ("users/{uid}"), que es la fuente de verdad de los roles.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /** UID del usuario con sesión activa, o null si no hay sesión. */
    val currentUid: String? get() = auth.currentUser?.uid

    /**
     * Inicia sesión y valida el perfil. Si el perfil no existe (fue eliminado
     * por un administrador) o está inactivo, se cierra la sesión y se lanza error.
     */
    suspend fun login(email: String, password: String): User {
        val uid = auth.signInWithEmailAndPassword(email, password).await().user?.uid
            ?: throw AppException(R.string.error_bad_credentials)
        val user = db.collection(UserRepository.COLLECTION).document(uid).get().await()
            .toObject(User::class.java)

        when {
            user == null -> {
                auth.signOut()
                throw AppException(R.string.error_profile_missing)
            }
            !user.active -> {
                auth.signOut()
                throw AppException(R.string.error_user_inactive)
            }
        }
        Session.user = user
        return user!!
    }

    /**
     * Registro desde la pantalla pública: crea la cuenta en Authentication
     * y el documento de perfil con el rol elegido.
     */
    suspend fun register(
        name: String, email: String, phone: String, password: String, role: Role, storeName: String = ""
    ): User {
        val uid = auth.createUserWithEmailAndPassword(email, password).await().user?.uid
            ?: throw AppException(R.string.error_register)
        val user = User(uid = uid, name = name, email = email, phone = phone, role = role.name, storeName = storeName)
        db.collection(UserRepository.COLLECTION).document(uid).set(user).await()
        Session.user = user
        return user
    }

    /**
     * Creación de usuarios por el Administrador. Se usa una segunda instancia
     * de FirebaseApp para crear la cuenta: si se usara la instancia principal,
     * Firebase cambiaría la sesión al usuario nuevo y el administrador
     * quedaría desconectado.
     */
    suspend fun createUserAsAdmin(user: User, password: String): User {
        val helperAuth = FirebaseAuth.getInstance(secondaryApp())
        try {
            val uid = helperAuth.createUserWithEmailAndPassword(user.email, password).await().user?.uid
                ?: throw AppException(R.string.error_register)
            val created = user.copy(uid = uid)
            db.collection(UserRepository.COLLECTION).document(uid).set(created).await()
            return created
        } finally {
            helperAuth.signOut()
        }
    }

    /** Envía el correo de restablecimiento de contraseña de Firebase. */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /**
     * Vuelve a validar la contraseña del usuario actual. Se usa antes de
     * activar la huella para no guardar una contraseña equivocada.
     */
    suspend fun verifyPassword(password: String) {
        val user = auth.currentUser ?: throw AppException(R.string.error_session_expired)
        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
    }

    /** Cierra la sesión de Firebase y limpia el perfil en memoria. */
    fun logout() {
        auth.signOut()
        Session.clear()
    }

    private fun secondaryApp(): FirebaseApp {
        val main = FirebaseApp.getInstance()
        return FirebaseApp.getApps(main.applicationContext).firstOrNull { it.name == HELPER_APP }
            ?: FirebaseApp.initializeApp(main.applicationContext, main.options, HELPER_APP)
    }

    private companion object {
        const val HELPER_APP = "admin-user-creator"
    }
}
