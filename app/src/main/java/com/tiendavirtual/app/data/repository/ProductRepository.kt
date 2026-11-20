package com.tiendavirtual.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.tiendavirtual.app.data.model.Product
import kotlinx.coroutines.tasks.await

/**
 * CRUD de productos (colección "products"). La imagen de cada producto se
 * guarda en el mismo documento como JPEG comprimido en base64 (campo imageUrl
 * con formato data URI), generado por util/ImageCompressor.
 *
 * Los filtros por categoría y texto se hacen en memoria para no requerir
 * índices compuestos en Firestore.
 */
class ProductRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val products get() = db.collection(COLLECTION)

    /** Productos visibles en el catálogo del comprador (activos), más recientes primero. */
    suspend fun getActive(): List<Product> =
        products.whereEqualTo("active", true).get().await()
            .toObjects(Product::class.java).sortedByDescending { it.createdAt }

    /** Productos de un vendedor (incluye pausados). */
    suspend fun getBySeller(sellerId: String): List<Product> =
        products.whereEqualTo("sellerId", sellerId).get().await()
            .toObjects(Product::class.java).sortedByDescending { it.createdAt }

    /** Todos los productos de la tienda (vista del Administrador). */
    suspend fun getAll(): List<Product> =
        products.get().await().toObjects(Product::class.java).sortedByDescending { it.createdAt }

    suspend fun get(id: String): Product? =
        products.document(id).get().await().toObject(Product::class.java)

    /**
     * Crea o actualiza un producto (Create / Update del CRUD).
     *
     * @param newImage imagen nueva ya comprimida como data URI, o null para
     *  conservar la imagen actual del producto.
     * @return el producto guardado, con su id definitivo.
     */
    suspend fun save(product: Product, newImage: String?): Product {
        val doc = if (product.id.isBlank()) products.document() else products.document(product.id)
        var saved = product.copy(id = doc.id)
        if (newImage != null) saved = saved.copy(imageUrl = newImage, imagePath = "")
        doc.set(saved).await()
        return saved
    }

    /** Pausa o reactiva la publicación de un producto. */
    suspend fun setActive(id: String, active: Boolean) {
        products.document(id).update("active", active).await()
    }

    /** Elimina el producto; la imagen se borra con él porque vive en el mismo documento. */
    suspend fun delete(product: Product) {
        products.document(product.id).delete().await()
    }

    companion object {
        const val COLLECTION = "products"
    }
}
