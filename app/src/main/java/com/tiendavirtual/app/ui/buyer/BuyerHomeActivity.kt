package com.tiendavirtual.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityBuyerHomeBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.ui.common.ProductGridAdapter

/** Inicio del Comprador: buscador, categorías y productos destacados (los más recientes). */
class BuyerHomeActivity : BaseActivity() {

    private lateinit var binding: ActivityBuyerHomeBinding
    private val adapter = ProductGridAdapter { ProductDetailActivity.open(this, it.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.buyer_home_title, showBack = false)
        Navigator.setupBuyerNav(this, binding.bottomNav, R.id.nav_home)

        binding.recycler.layoutManager = GridLayoutManager(this, 2)
        binding.recycler.adapter = adapter
        binding.recycler.isNestedScrollingEnabled = false

        // Buscar abre el catálogo con el texto escrito.
        binding.inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openCatalog(query = binding.inputSearch.text?.toString(), category = null); true
            } else false
        }
        binding.btnSeeCatalog.setOnClickListener { openCatalog(null, null) }

        // Un chip por categoría; al tocarlo se abre el catálogo filtrado.
        resources.getStringArray(R.array.categories).forEach { category ->
            binding.categoryGroup.addView(Chip(this).apply {
                text = category
                setOnClickListener { openCatalog(null, category) }
            })
        }
        load()
    }

    private fun load() = launchUi(binding.progress) {
        val me = UserRepository().currentProfile()
        binding.textGreeting.text = getString(R.string.home_greeting_name, me.name.substringBefore(" "))
        val featured = ProductRepository().getActive().filter { it.stock > 0 }.take(FEATURED)
        adapter.submit(featured)
        binding.textEmpty.visibility = if (featured.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openCatalog(query: String?, category: String?) {
        startActivity(Intent(this, CatalogActivity::class.java)
            .putExtra(CatalogActivity.EXTRA_QUERY, query)
            .putExtra(CatalogActivity.EXTRA_CATEGORY, category))
    }

    private companion object {
        const val FEATURED = 6
    }
}
