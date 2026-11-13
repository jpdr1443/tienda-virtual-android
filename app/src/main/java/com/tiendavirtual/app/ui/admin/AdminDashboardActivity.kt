package com.tiendavirtual.app.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.core.view.GravityCompat
import com.google.firebase.Timestamp
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityAdminDashboardBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.Navigator
import com.tiendavirtual.app.ui.common.ProfileActivity
import com.tiendavirtual.app.util.Formatters
import java.util.Calendar

/**
 * Panel principal del Administrador con menú lateral (Navigation Drawer).
 * Los indicadores se calculan en tiempo real con los datos de Firestore.
 */
class AdminDashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.admin_nav_users -> openScreen(AdminUsersActivity::class.java)
                R.id.admin_nav_products -> openScreen(AdminProductsActivity::class.java)
                R.id.admin_nav_sales -> openScreen(AdminSalesActivity::class.java)
                R.id.admin_nav_profile -> openScreen(ProfileActivity::class.java)
                R.id.admin_nav_logout -> Navigator.logout(this)
            }
            true
        }
        binding.cardUsers.setOnClickListener { openScreen(AdminUsersActivity::class.java) }
        binding.cardProducts.setOnClickListener { openScreen(AdminProductsActivity::class.java) }
        binding.cardSales.setOnClickListener { openScreen(AdminSalesActivity::class.java) }

        // El botón Atrás primero cierra el menú lateral si está abierto.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }

    /** Se recalculan los indicadores cada vez que se vuelve al panel. */
    override fun onResume() {
        super.onResume()
        loadKpis()
    }

    private fun openScreen(target: Class<*>) = startActivity(Intent(this, target))

    /** Calcula usuarios activos, productos publicados, pedidos de hoy y ventas del mes. */
    private fun loadKpis() = launchUi(binding.progress) {
        val admin = UserRepository().currentProfile()
        binding.textGreeting.text = getString(R.string.admin_greeting_name, admin.name)

        val users = UserRepository().getAll()
        val products = ProductRepository().getAll()
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val monthOrders = OrderRepository().getSince(Timestamp(monthStart.time))
            .filter { it.statusEnum in OrderStatus.SOLD }

        binding.kpiUsers.text = users.count { it.active }.toString()
        binding.kpiProducts.text = products.count { it.active }.toString()
        binding.kpiOrders.text = monthOrders.count {
            (it.createdAt?.toDate()?.time ?: 0L) >= todayStart.timeInMillis
        }.toString()
        binding.kpiSales.text = Formatters.money(monthOrders.sumOf { it.total })

        // Actividad reciente: los últimos usuarios registrados y pedidos pagados.
        val recentUsers = users.sortedByDescending { it.createdAt }.take(2)
            .map { getString(R.string.recent_user, it.name, getString(it.roleEnum.label)) }
        val recentOrders = monthOrders.take(3)
            .map { getString(R.string.recent_order, it.code, Formatters.money(it.total)) }
        binding.textRecent.text = (recentOrders + recentUsers).joinToString("\n\n")
            .ifEmpty { getString(R.string.recent_empty) }
    }
}
