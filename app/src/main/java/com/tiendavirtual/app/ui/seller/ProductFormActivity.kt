package com.tiendavirtual.app.ui.seller

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityProductFormBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.loadProductImage
import com.tiendavirtual.app.util.ImageCompressor
import java.io.File

/**
 * Crear / editar producto (Create y Update del CRUD). Lo usan el Vendedor y
 * el Administrador. La imagen se toma con la cámara o se elige de la galería;
 * al guardar se comprime (ImageCompressor) y se guarda en Firestore.
 */
class ProductFormActivity : BaseActivity() {

    private lateinit var binding: ActivityProductFormBinding
    private val repo = ProductRepository()
    private var editing: Product? = null

    /** Imagen elegida y aún no subida (null = conservar la actual). */
    private var pendingImage: Uri? = null
    /** Archivo temporal donde la cámara escribe la foto. */
    private var cameraUri: Uri? = null

    // Contratos de Activity Result: reemplazan a startActivityForResult.
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::showSelectedImage)
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::showSelectedImage)
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast(R.string.error_camera_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inputCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.categories))
        )
        binding.btnImage.setOnClickListener { chooseImageSource() }
        binding.imageProduct.setOnClickListener { chooseImageSource() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { delete() }

        val id = intent.getStringExtra(EXTRA_PRODUCT_ID)
        if (id == null) {
            setupToolbar(binding.toolbar, R.string.product_new_title)
            binding.btnDelete.visibility = View.GONE
        } else {
            setupToolbar(binding.toolbar, R.string.product_form_title)
            load(id)
        }
    }

    private fun load(id: String) = launchUi(binding.progress) {
        val product = repo.get(id) ?: run { toast(R.string.error_product_missing); finish(); return@launchUi }
        editing = product
        binding.inputName.setText(product.name)
        binding.inputDescription.setText(product.description)
        binding.inputPrice.setText(product.price.toString())
        binding.inputStock.setText(product.stock.toString())
        binding.inputCategory.setText(product.category, false)
        binding.switchActive.isChecked = product.active
        binding.imageProduct.loadProductImage(product.imageUrl)
    }

    /** Diálogo para elegir entre cámara y galería. */
    private fun chooseImageSource() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.image_source_title)
            .setItems(arrayOf(getString(R.string.image_camera), getString(R.string.image_gallery))) { _, which ->
                if (which == 0) requestCamera()
                else pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .show()
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Crea un archivo temporal en caché y se lo entrega a la cámara mediante un FileProvider. */
    private fun launchCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "product_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        takePicture.launch(uri)
    }

    private fun showSelectedImage(uri: Uri) {
        pendingImage = uri
        binding.imageProduct.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        binding.imageProduct.setImageURI(uri)
    }

    /** Valida los campos y guarda el producto (con su imagen comprimida si cambió) en Firestore. */
    private fun save() {
        val name = binding.inputName.text?.toString().orEmpty().trim()
        val description = binding.inputDescription.text?.toString().orEmpty().trim()
        val price = binding.inputPrice.text?.toString()?.toLongOrNull()
        val stock = binding.inputStock.text?.toString()?.toLongOrNull()
        val category = binding.inputCategory.text?.toString().orEmpty()

        binding.layoutName.error = if (name.length >= 3) null else getString(R.string.error_product_name)
        binding.layoutPrice.error = if (price != null && price > 0) null else getString(R.string.error_price)
        binding.layoutStock.error = if (stock != null && stock >= 0) null else getString(R.string.error_stock_value)
        binding.layoutCategory.error = if (category.isNotBlank()) null else getString(R.string.error_category)
        if (listOf(binding.layoutName, binding.layoutPrice, binding.layoutStock, binding.layoutCategory)
                .any { it.error != null }) return
        if (editing == null && pendingImage == null) {
            toast(R.string.error_image_required)
            return
        }

        launchUi(binding.progress, binding.btnSave, binding.btnDelete) {
            val me = UserRepository().currentProfile()
            // Un producto nuevo pertenece a quien lo crea; al editar se conserva el vendedor original.
            val base = editing ?: Product(
                sellerId = me.uid,
                sellerName = me.storeName.ifBlank { me.name }
            )
            if (editing == null && me.roleEnum != Role.SELLER && me.roleEnum != Role.ADMIN) {
                toast(R.string.error_permission)
                return@launchUi
            }
            // La foto nueva se reduce y comprime antes de guardarla en el documento.
            val image = pendingImage?.let { ImageCompressor.toDataUri(this, it) }
            repo.save(
                base.copy(
                    name = name, description = description, price = price!!, stock = stock!!,
                    category = category, active = binding.switchActive.isChecked
                ),
                image
            )
            toast(R.string.msg_product_saved)
            finish()
        }
    }

    private fun delete() {
        val product = editing ?: return
        confirm(R.string.delete_product_title, getString(R.string.delete_product_message, product.name)) {
            launchUi(binding.progress, binding.btnSave, binding.btnDelete) {
                repo.delete(product)
                toast(R.string.msg_deleted)
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "product_id"
    }
}
