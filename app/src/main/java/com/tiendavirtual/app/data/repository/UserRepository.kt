package com.tiendavirtual.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.util.AppException
import com.tiendavirtual.app.util.Session
import kotlinx.coroutines.tasks.await

/** CRUD de perfiles de usuario sobre la colección "users" de Firestore. */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val users get() = db.collection(COLLECTION)

    /**
     * Perfil del usuario con sesión activa. Usa la caché de [Session] y solo
     * consulta Firestore si está vacía (por ejemplo, tras reiniciarse la app).
     */
    suspend fun currentProfile(forceRefresh: Boolean = false): User {
        if (!forceRefresh) Session.user?.let { return it }
        val uid = auth.currentUser?.uid ?: throw AppException(R.string.error_session_expired)
        val user = get(uid) ?: throw AppException(R.string.error_profile_missing)
        Session.user = user
        return user
    }

    /** Lee un usuario por su UID. */
    suspend fun get(uid: String): User? =
        users.document(uid).get().await().toObject(User::class.java)

    /** Lista todos los usuarios ordenados por nombre (vista del Administrador). */
    suspend fun getAll(): List<User> =
        users.get().await().toObjects(User::class.java).sortedBy { it.name.lowercase() }

    /**
     * Actualiza los datos editables de un perfil. El correo no se modifica
     * porque está ligado a la cuenta de Authentication.
     */
    suspend fun update(user: User) {
        users.document(user.uid).update(
            mapOf(
                "name" to user.name,
                "phone" to user.phone,
                "role" to user.role,
                "active" to user.active,
                "storeName" to user.storeName,
                "address" to user.address,
                "city" to user.city
            )
        ).await()
        if (user.uid == auth.currentUser?.uid) Session.user = user
    }

    /**
     * Elimina el perfil. Sin perfil, AuthRepository.login() rechaza el acceso,
     * por lo que el usuario queda bloqueado. (Borrar la cuenta de Authentication
     * de otra persona requiere el Admin SDK en un servidor.)
     */
    suspend fun delete(uid: String) {
        users.document(uid).delete().await()
    }

    /** Guarda la última dirección usada para precargarla en la próxima compra. */
    suspend fun saveDefaultAddress(uid: String, address: String, city: String) {
        users.document(uid).update(mapOf("address" to address, "city" to city)).await()
        Session.user = Session.user?.copy(address = address, city = city)
    }

    companion object {
        const val COLLECTION = "users"
    }
}
