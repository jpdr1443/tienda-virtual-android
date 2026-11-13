package com.tiendavirtual.app.data.model

import com.tiendavirtual.app.R

/**
 * Roles de la tienda virtual. El nombre del enum es el valor que se guarda
 * en el campo "role" del documento del usuario en Firestore.
 *
 * @property label recurso de texto que se muestra en la interfaz.
 */
enum class Role(val label: Int) {
    ADMIN(R.string.role_admin),
    SELLER(R.string.role_seller),
    BUYER(R.string.role_buyer);

    companion object {
        /** Convierte el texto guardado en Firestore a [Role]; si no es válido devuelve BUYER. */
        fun from(value: String?): Role = entries.firstOrNull { it.name == value } ?: BUYER
    }
}
