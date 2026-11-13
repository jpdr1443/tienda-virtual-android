package com.tiendavirtual.app.util

import com.tiendavirtual.app.data.model.User

/**
 * Caché en memoria del perfil del usuario autenticado. Evita consultar
 * Firestore en cada pantalla; se llena al iniciar sesión y se limpia al salir.
 * Si Android destruye el proceso, UserRepository.currentProfile() lo recarga.
 */
object Session {
    @Volatile
    var user: User? = null

    fun clear() {
        user = null
    }
}
