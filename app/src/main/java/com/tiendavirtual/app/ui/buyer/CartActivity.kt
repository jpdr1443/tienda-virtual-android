package com.tiendavirtual.app.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.CartItem
import com.tiendavirtual.app.data.repository.CartRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityCartBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.CartAdapter
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.util.Formatters

/** Carrito del comprador: leer, cambiar cantidades, eliminar ítems y pasar al pago. */
class CartActivity : BaseActivity() {

    private lateinit var binding: ActivityCartBinding
    private val repo = CartRepository()
    private var uid = ""
    private var items: List<CartItem> = emptyList()
    private val adapter = CartAdapter(onQuantity = ::changeQuantity, onRemove = ::remove)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.cart_title, showBack = false)
        Navigator.setupBuyerNav(this, binding.bottomNav, R.id.nav_cart)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.btnCheckout.setOnClickListener {
            startActivity(Intent(this, CheckoutActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = launchUi(binding.progress) {
        uid = UserRepository().currentProfile().uid
        items = repo.getItems(uid)
        render()
    }

    private fun render() {
        adapter.submit(items)
        val total = items.sumOf { it.subtotal() }
        binding.textSubtotal.text = Formatters.money(total)
        binding.textTotal.text = Formatters.money(total)
        val empty = items.isEmpty()
        binding.textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.summary.visibility = if (empty) View.GONE else View.VISIBLE
    }

    /** Update del CRUD del carrito; con cantidad 0 se elimina el ítem. */
    private fun changeQuantity(item: CartItem, quantity: Long) {
        launchUi(binding.progress) {
            repo.updateQuantity(uid, item.productId, quantity)
            items = repo.getItems(uid)
            render()
        }
    }

    /** Delete del CRUD del carrito. */
    private fun remove(item: CartItem) {
        confirm(R.string.cart_remove_title, getString(R.string.cart_remove_message, item.name)) {
            launchUi(binding.progress) {
                repo.remove(uid, item.productId)
                items = repo.getItems(uid)
                render()
            }
        }
    }
}
