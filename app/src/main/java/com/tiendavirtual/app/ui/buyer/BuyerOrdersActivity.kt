package com.tiendavirtual.app.ui.buyer

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityBuyerOrdersBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.OrderAdapter
import com.tiendavirtual.app.ui.common.OrderMapLauncher

/** Historial de compras del comprador con el estado de cada orden y del pago. */
class BuyerOrdersActivity : BaseActivity() {

    private lateinit var binding: ActivityBuyerOrdersBinding
    private val adapter = OrderAdapter(onClick = { OrderMapLauncher.open(this, it) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuyerOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.profile_orders)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        load()
    }

    private fun load() = launchUi(binding.progress) {
        val orders = OrderRepository().getByBuyer(UserRepository().currentProfile().uid)
        adapter.submit(orders)
        binding.textEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
    }
}
