package com.tiendavirtual.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp

/**
 * Perfil de usuario guardado en la colección "users" de Firestore.
 * El id del documento es el mismo UID que asigna Firebase Authentication,
 * de modo que la cuenta y el perfil quedan enlazados.
 *
 * Todos los campos son "var" con valor por defecto porque Firestore necesita
 * un constructor vacío y setters para convertir el documento en objeto.
 */
data class User(
    @DocumentId var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var role: String = Role.BUYER.name,
    var active: Boolean = true,
    var storeName: String = "",
    var address: String = "",
    var city: String = "",
    @ServerTimestamp var createdAt: Timestamp? = null
) {
    /** Rol tipado a partir del texto guardado. No se persiste en Firestore. */
    @get:Exclude
    val roleEnum: Role get() = Role.from(role)

    /** Iniciales para el avatar circular (máximo dos letras). */
    @get:Exclude
    val initials: String
        get() = name.trim().split(" ").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
