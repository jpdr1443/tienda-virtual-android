package com.tiendavirtual.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.databinding.ActivityAdminProductsBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.ProductManageAdapter
import com.tiendavirtual.app.ui.seller.ProductFormActivity

/**
 * Gestión de todos los productos de la tienda por parte del Administrador:
 * puede crear, editar, pausar o eliminar publicaciones de cualquier vendedor.
 */
class AdminProductsActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminProductsBinding
    private val repo = ProductRepository()
    private var all: List<Product> = emptyList()
    private val adapter = ProductManageAdapter(showSeller = true, onEdit = ::edit, onDelete = ::confirmDelete)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.admin_products_title)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.inputSearch.doAfterTextChanged { applyFilter() }
        binding.btnNew.setOnClickListener { startActivity(Intent(this, ProductFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = launchUi(binding.progress) {
        all = repo.getAll()
        applyFilter()
    }

    /** Filtra por nombre, categoría o vendedor. */
    private fun applyFilter() {
        val q = binding.inputSearch.text?.toString().orEmpty().trim().lowercase()
        val list = if (q.isEmpty()) all else all.filter {
            it.name.lowercase().contains(q) || it.category.lowercase().contains(q) ||
                it.sellerName.lowercase().contains(q)
        }
        adapter.submit(list)
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.textCount.text = getString(R.string.products_count, list.size)
    }

    private fun edit(product: Product) {
        startActivity(Intent(this, ProductFormActivity::class.java)
            .putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, product.id))
    }

    private fun confirmDelete(product: Product) {
        confirm(R.string.delete_product_title, getString(R.string.delete_product_message, product.name)) {
            launchUi(binding.progress) {
                repo.delete(product)
                toast(R.string.msg_deleted)
                load()
            }
        }
    }
}
