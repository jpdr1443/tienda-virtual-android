package com.tiendavirtual.app.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Ítem del carrito de un comprador, guardado en "carts/{uid}/items/{productId}".
 * Usar el id del producto como id del documento evita duplicados: si el
 * comprador agrega el mismo producto dos veces solo se suma la cantidad.
 */
data class CartItem(
    @DocumentId var productId: String = "",
    var name: String = "",
    var price: Long = 0,
    var quantity: Long = 1,
    var imageUrl: String = "",
    var sellerId: String = ""
) {
    /** Subtotal de la línea (precio × cantidad). */
    fun subtotal(): Long = price * quantity
}
