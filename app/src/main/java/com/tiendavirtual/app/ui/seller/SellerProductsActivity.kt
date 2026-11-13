package com.tiendavirtual.app.ui.seller

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivitySellerProductsBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.ui.common.ProductManageAdapter

/** Inicio del Vendedor: listado (Read) de sus productos con accesos a crear, editar y eliminar. */
class SellerProductsActivity : BaseActivity() {

    private lateinit var binding: ActivitySellerProductsBinding
    private val repo = ProductRepository()
    private val adapter = ProductManageAdapter(showSeller = false, onEdit = ::edit, onDelete = ::confirmDelete)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerProductsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.seller_products_title, showBack = false)
        Navigator.setupSellerNav(this, binding.bottomNav, R.id.nav_products)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnNew.setOnClickListener { startActivity(Intent(this, ProductFormActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = launchUi(binding.progress) {
        val seller = UserRepository().currentProfile()
        val list = repo.getBySeller(seller.uid)
        adapter.submit(list)
        binding.textSummary.text = getString(
            R.string.seller_products_summary, list.size, list.count { it.active && it.stock > 0 }
        )
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
