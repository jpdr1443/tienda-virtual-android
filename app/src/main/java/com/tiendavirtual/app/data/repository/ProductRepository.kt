package com.tiendavirtual.app.data.repository

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.tiendavirtual.app.data.model.Product
import kotlinx.coroutines.tasks.await

/**
 * CRUD de productos (colección "products") y manejo de sus imágenes en
 * Firebase Storage (carpeta "products/{idProducto}/").
 *
 * Los filtros por categoría y texto se hacen en memoria para no requerir
 * índices compuestos en Firestore.
 */
class ProductRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
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
     * Crea o actualiza un producto. Si se eligió una imagen nueva (cámara o
     * galería) se sube primero a Storage, se obtiene su URL de descarga y se
     * borra la imagen anterior para no dejar archivos huérfanos.
     *
     * @return el producto guardado, con su id definitivo.
     */
    suspend fun save(product: Product, newImage: Uri?): Product {
        val doc = if (product.id.isBlank()) products.document() else products.document(product.id)
        var saved = product.copy(id = doc.id)

        if (newImage != null) {
            val path = "products/${doc.id}/${System.currentTimeMillis()}.jpg"
            val ref = storage.reference.child(path)
            ref.putFile(newImage).await()
            val url = ref.downloadUrl.await().toString()
            deleteImage(product.imagePath)
            saved = saved.copy(imageUrl = url, imagePath = path)
        }

        doc.set(saved).await()
        return saved
    }

    /** Pausa o reactiva la publicación de un producto. */
    suspend fun setActive(id: String, active: Boolean) {
        products.document(id).update("active", active).await()
    }

    /** Elimina el documento y su imagen en Storage. */
    suspend fun delete(product: Product) {
        products.document(product.id).delete().await()
        deleteImage(product.imagePath)
    }

    /** Borra un archivo de Storage; si ya no existe se ignora el error. */
    private suspend fun deleteImage(path: String) {
        if (path.isBlank()) return
        runCatching { storage.reference.child(path).delete().await() }
    }

    companion object {
        const val COLLECTION = "products"
    }
}
