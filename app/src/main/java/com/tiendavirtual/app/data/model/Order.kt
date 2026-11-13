package com.tiendavirtual.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.tiendavirtual.app.R

/** Línea de una orden: copia del producto en el momento de la compra. */
data class OrderItem(
    var productId: String = "",
    var name: String = "",
    var price: Long = 0,
    var quantity: Long = 1,
    var sellerId: String = ""
)

/**
 * Estados por los que pasa una orden. El nombre se guarda en Firestore.
 * @property label texto visible; @property color color de la píldora de estado.
 */
enum class OrderStatus(val label: Int, val color: Int, val softColor: Int) {
    PENDING_PAYMENT(R.string.status_pending_payment, R.color.warning, R.color.warning_soft),
    PAID(R.string.status_paid, R.color.success, R.color.success_soft),
    SHIPPED(R.string.status_shipped, R.color.primary, R.color.primary_soft),
    DELIVERED(R.string.status_delivered, R.color.teal, R.color.success_soft),
    REJECTED(R.string.status_rejected, R.color.danger, R.color.danger_soft);

    companion object {
        fun from(value: String?): OrderStatus =
            entries.firstOrNull { it.name == value } ?: PENDING_PAYMENT

        /** Estados que cuentan como venta efectiva en los reportes. */
        val SOLD = setOf(PAID, SHIPPED, DELIVERED)
    }
}

/**
 * Orden de compra (colección "orders"). Queda como historial permanente
 * del comprador y de cada vendedor involucrado.
 *
 * @property sellerIds lista de vendedores de los productos, para que cada
 *  vendedor consulte sus pedidos con una sola consulta "array-contains".
 * @property latitude/longitude coordenadas capturadas con el GPS en el checkout.
 * @property paymentRef referencia ePayco (ref_payco) de la transacción.
 */
data class Order(
    @DocumentId var id: String = "",
    var buyerId: String = "",
    var buyerName: String = "",
    var buyerEmail: String = "",
    var items: List<OrderItem> = emptyList(),
    var sellerIds: List<String> = emptyList(),
    var total: Long = 0,
    var address: String = "",
    var city: String = "",
    var latitude: Double? = null,
    var longitude: Double? = null,
    var status: String = OrderStatus.PENDING_PAYMENT.name,
    var paymentRef: String = "",
    var paymentTransactionId: String = "",
    var paymentMessage: String = "",
    var createdAt: Timestamp? = null,
    var paidAt: Timestamp? = null
) {
    @get:Exclude
    val statusEnum: OrderStatus get() = OrderStatus.from(status)

    /** Código corto y legible para mostrar (ej. PD-3F9A2C). */
    @get:Exclude
    val code: String get() = "PD-" + id.take(6).uppercase()

    /** Cantidad total de unidades en la orden. */
    fun unitCount(): Long = items.sumOf { it.quantity }
}
