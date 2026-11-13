package com.tiendavirtual.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.CartItem
import com.tiendavirtual.app.data.model.Order
import com.tiendavirtual.app.data.model.OrderItem
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.util.AppException
import com.tiendavirtual.app.util.DeliveryLocation
import kotlinx.coroutines.tasks.await

/**
 * Órdenes de compra (colección "orders"). Implementa el flujo:
 *  1. [createPending]: el comprador confirma el carrito → orden "pendiente de pago".
 *  2. [confirmPayment] o [reject]: según la respuesta validada de ePayco.
 *  3. [updateStatus]: el vendedor marca la orden como enviada / entregada.
 */
class OrderRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val orders get() = db.collection(COLLECTION)
    private val products get() = db.collection(ProductRepository.COLLECTION)

    /**
     * Crea la orden con estado PENDING_PAYMENT. Antes verifica que haya stock
     * suficiente de cada producto para no cobrar algo que no se puede entregar.
     */
    suspend fun createPending(
        buyer: User,
        cart: List<CartItem>,
        address: String,
        city: String,
        location: DeliveryLocation?
    ): Order {
        if (cart.isEmpty()) throw AppException(R.string.cart_empty)

        cart.forEach { item ->
            val stock = products.document(item.productId).get().await().getLong("stock") ?: 0L
            if (stock < item.quantity) throw AppException(R.string.error_stock, item.name)
        }

        val doc = orders.document()
        val order = Order(
            id = doc.id,
            buyerId = buyer.uid,
            buyerName = buyer.name,
            buyerEmail = buyer.email,
            items = cart.map { OrderItem(it.productId, it.name, it.price, it.quantity, it.sellerId) },
            sellerIds = cart.map { it.sellerId }.distinct(),
            total = cart.sumOf { it.subtotal() },
            address = address,
            city = city,
            latitude = location?.latitude,
            longitude = location?.longitude,
            createdAt = Timestamp.now()
        )
        doc.set(order).await()
        return order
    }

    /**
     * Marca la orden como pagada y descuenta el inventario en una transacción:
     * todas las lecturas y escrituras se aplican juntas o ninguna. Si la orden
     * ya estaba pagada no hace nada (evita descontar stock dos veces).
     */
    suspend fun confirmPayment(orderId: String, reference: String, transactionId: String, message: String) {
        db.runTransaction<Unit> { tx ->
            val orderRef = orders.document(orderId)
            val order = tx.get(orderRef).toObject(Order::class.java)
                ?: throw IllegalStateException("Orden no encontrada")
            if (order.statusEnum != OrderStatus.PENDING_PAYMENT) return@runTransaction

            // En una transacción todas las lecturas deben ir antes de las escrituras.
            val stocks = order.items.associate { item ->
                item.productId to (tx.get(products.document(item.productId)).getLong("stock") ?: 0L)
            }
            order.items.forEach { item ->
                val remaining = (stocks[item.productId] ?: 0L) - item.quantity
                tx.update(products.document(item.productId), "stock", remaining.coerceAtLeast(0))
            }
            tx.update(
                orderRef, mapOf(
                    "status" to OrderStatus.PAID.name,
                    "paymentRef" to reference,
                    "paymentTransactionId" to transactionId,
                    "paymentMessage" to message,
                    "paidAt" to FieldValue.serverTimestamp()
                )
            )
            Unit
        }.await()
    }

    /** Registra un pago rechazado o fallido. La orden queda en el historial. */
    suspend fun reject(orderId: String, reference: String, message: String) {
        orders.document(orderId).update(
            mapOf(
                "status" to OrderStatus.REJECTED.name,
                "paymentRef" to reference,
                "paymentMessage" to message
            )
        ).await()
    }

    /** Guarda la referencia de un pago que ePayco reporta como pendiente. */
    suspend fun markPending(orderId: String, reference: String, message: String) {
        orders.document(orderId).update(
            mapOf("paymentRef" to reference, "paymentMessage" to message)
        ).await()
    }

    /** Cambia el estado logístico (enviado, entregado) desde el panel del vendedor. */
    suspend fun updateStatus(orderId: String, status: OrderStatus) {
        orders.document(orderId).update("status", status.name).await()
    }

    suspend fun get(orderId: String): Order? =
        orders.document(orderId).get().await().toObject(Order::class.java)

    /** Historial de compras de un comprador, más reciente primero. */
    suspend fun getByBuyer(buyerId: String): List<Order> =
        orders.whereEqualTo("buyerId", buyerId).get().await()
            .toObjects(Order::class.java).sortedByDescending { it.createdAt }

    /** Pedidos que incluyen al menos un producto del vendedor. */
    suspend fun getBySeller(sellerId: String): List<Order> =
        orders.whereArrayContains("sellerIds", sellerId).get().await()
            .toObjects(Order::class.java).sortedByDescending { it.createdAt }

    /** Órdenes creadas desde una fecha (reportes del Administrador). */
    suspend fun getSince(from: Timestamp): List<Order> =
        orders.whereGreaterThanOrEqualTo("createdAt", from).get().await()
            .toObjects(Order::class.java).sortedByDescending { it.createdAt }

    companion object {
        const val COLLECTION = "orders"
    }
}
