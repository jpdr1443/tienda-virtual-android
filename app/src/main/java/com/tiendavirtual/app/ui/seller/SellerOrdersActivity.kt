package com.tiendavirtual.app.ui.seller

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Order
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivitySellerOrdersBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.ui.common.OrderAdapter
import com.tiendavirtual.app.ui.common.OrderMapLauncher

/**
 * Historial de pedidos que incluyen productos del vendedor. Permite filtrar
 * por estado y avanzar el despacho: Pagada → Enviada → Entregada.
 */
class SellerOrdersActivity : BaseActivity() {

    private lateinit var binding: ActivitySellerOrdersBinding
    private val repo = OrderRepository()
    private var all: List<Order> = emptyList()
    private lateinit var adapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.seller_orders_title, showBack = false)
        Navigator.setupSellerNav(this, binding.bottomNav, R.id.nav_orders)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.filterGroup.setOnCheckedStateChangeListener { _, _ -> applyFilter() }
        load()
    }

    private fun load() = launchUi(binding.progress) {
        val me = UserRepository().currentProfile()
        if (!::adapter.isInitialized) {
            adapter = OrderAdapter(
                sellerId = me.uid,
                onAdvance = ::advance,
                onClick = { order -> OrderMapLauncher.open(this, order) }
            )
            binding.recycler.adapter = adapter
        }
        // Las órdenes sin pagar no se muestran al vendedor: aún no hay nada que despachar.
        all = repo.getBySeller(me.uid).filter { it.statusEnum != OrderStatus.PENDING_PAYMENT }
        applyFilter()
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized) return
        val list = when (binding.filterGroup.checkedChipId) {
            R.id.chip_paid -> all.filter { it.statusEnum == OrderStatus.PAID }
            R.id.chip_shipped -> all.filter { it.statusEnum == OrderStatus.SHIPPED }
            R.id.chip_delivered -> all.filter { it.statusEnum == OrderStatus.DELIVERED }
            else -> all
        }
        adapter.submit(list)
        binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Cambia el estado del pedido (Update del CRUD de órdenes). */
    private fun advance(order: Order, next: OrderStatus) {
        confirm(R.string.order_update_title,
            getString(R.string.order_update_message, order.code, getString(next.label)),
            positive = R.string.confirm) {
            launchUi(binding.progress) {
                repo.updateStatus(order.id, next)
                toast(R.string.msg_saved)
                load()
            }
        }
    }
}
