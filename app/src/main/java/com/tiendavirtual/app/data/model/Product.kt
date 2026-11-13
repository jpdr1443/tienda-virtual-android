package com.tiendavirtual.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Producto publicado por un vendedor (colección "products").
 *
 * @property price precio en pesos colombianos, sin decimales.
 * @property imageUrl URL pública de descarga de la imagen en Firebase Storage.
 * @property imagePath ruta interna del archivo en Storage; se usa para borrarlo.
 * @property active si es false el producto queda pausado y no aparece en el catálogo.
 */
data class Product(
    @DocumentId var id: String = "",
    var name: String = "",
    var description: String = "",
    var price: Long = 0,
    var stock: Long = 0,
    var category: String = "",
    var imageUrl: String = "",
    var imagePath: String = "",
    var sellerId: String = "",
    var sellerName: String = "",
    var active: Boolean = true,
    @ServerTimestamp var createdAt: Timestamp? = null
)
