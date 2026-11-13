package com.tiendavirtual.app.ui.buyer

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.databinding.ActivityBuyerCatalogBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.ui.common.ProductGridAdapter

/** Catálogo completo de productos activos con búsqueda y filtro por categoría. */
class CatalogActivity : BaseActivity() {

    private lateinit var binding: ActivityBuyerCatalogBinding
    private var all: List<Product> = emptyList()
    private var category: String? = null
    private val adapter = ProductGridAdapter { ProductDetailActivity.open(this, it.id) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerCatalogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.catalog_title, showBack = false)
        Navigator.setupBuyerNav(this, binding.bottomNav, R.id.nav_catalog)

        binding.recycler.layoutManager = GridLayoutManager(this, 2)
        binding.recycler.adapter = adapter
        binding.inputSearch.setText(intent.getStringExtra(EXTRA_QUERY))
        binding.inputSearch.doAfterTextChanged { applyFilter() }
        category = intent.getStringExtra(EXTRA_CATEGORY)
        buildCategoryChips()
        load()
    }

    /** Chip "Todos" más uno por categoría; selección única. */
    private fun buildCategoryChips() {
        val names = listOf(getString(R.string.filter_all)) + resources.getStringArray(R.array.categories)
        names.forEachIndexed { index, name ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = name
                isCheckable = true
                isChecked = if (index == 0) category == null else name == category
                setOnClickListener {
                    category = if (index == 0) null else name
                    applyFilter()
                }
            }
            binding.categoryGroup.addView(chip)
        }
    }

    private fun load() = launchUi(binding.progress) {
        all = ProductRepository().getActive()
        applyFilter()
    }

    private fun applyFilter() {
        val q = binding.inputSearch.text?.toString().orEmpty().trim().lowercase()
        val list = all.filter { p ->
            (category == null || p.category == category) &&
                (q.isEmpty() || p.name.lowercase().contains(q) || p.description.lowercase().contains(q))
        }
        adapter.submit(list)
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_CATEGORY = "category"
    }
}
