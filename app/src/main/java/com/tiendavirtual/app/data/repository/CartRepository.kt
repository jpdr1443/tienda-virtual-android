package com.tiendavirtual.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tiendavirtual.app.data.model.CartItem
import com.tiendavirtual.app.data.model.Product
import kotlinx.coroutines.tasks.await

/**
 * CRUD del carrito de compras. Cada comprador tiene su carrito en
 * "carts/{uid}/items", así persiste entre sesiones y dispositivos.
 */
class CartRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun items(uid: String) = db.collection("carts").document(uid).collection("items")

    /** Lee los ítems del carrito. */
    suspend fun getItems(uid: String): List<CartItem> =
        items(uid).get().await().toObjects(CartItem::class.java).sortedBy { it.name }

    /**
     * Agrega un producto. Si ya estaba en el carrito, FieldValue.increment suma
     * la cantidad en el servidor sin tener que leer el documento antes.
     */
    suspend fun add(uid: String, product: Product, quantity: Long) {
        items(uid).document(product.id).set(
            mapOf(
                "name" to product.name,
                "price" to product.price,
                "imageUrl" to product.imageUrl,
                "sellerId" to product.sellerId,
                "quantity" to FieldValue.increment(quantity)
            ),
            SetOptions.merge()
        ).await()
    }

    /** Cambia la cantidad de un ítem; si llega a cero lo elimina. */
    suspend fun updateQuantity(uid: String, productId: String, quantity: Long) {
        if (quantity <= 0) remove(uid, productId)
        else items(uid).document(productId).update("quantity", quantity).await()
    }

    /** Quita un ítem del carrito. */
    suspend fun remove(uid: String, productId: String) {
        items(uid).document(productId).delete().await()
    }

    /** Vacía el carrito con una escritura por lotes (después de un pago aprobado). */
    suspend fun clear(uid: String) {
        val snapshot = items(uid).get().await()
        if (snapshot.isEmpty) return
        db.runBatch { batch -> snapshot.documents.forEach { batch.delete(it.reference) } }.await()
    }
}
