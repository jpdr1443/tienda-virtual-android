package com.tiendavirtual.app.ui.common

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.ui.admin.AdminDashboardActivity
import com.tiendavirtual.app.ui.auth.LoginActivity
import com.tiendavirtual.app.ui.buyer.BuyerHomeActivity
import com.tiendavirtual.app.ui.buyer.CartActivity
import com.tiendavirtual.app.ui.buyer.CatalogActivity
import com.tiendavirtual.app.ui.seller.SellerOrdersActivity
import com.tiendavirtual.app.ui.seller.SellerProductsActivity

/**
 * Navegación entre pantallas principales: inicio según el rol, barras de
 * navegación inferior de comprador y vendedor, y cierre de sesión.
 */
object Navigator {

    /** Abre la pantalla de inicio del rol y limpia la pila (no se puede volver al login). */
    fun goHome(activity: Activity, role: Role) {
        val target = when (role) {
            Role.ADMIN -> AdminDashboardActivity::class.java
            Role.SELLER -> SellerProductsActivity::class.java
            Role.BUYER -> BuyerHomeActivity::class.java
        }
        activity.startActivity(
            Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    /** Cierra la sesión de Firebase y vuelve al login. */
    fun logout(activity: Activity) {
        AuthRepository().logout()
        activity.startActivity(
            Intent(activity, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_FROM_LOGOUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        activity.finish()
    }

    /** Barra inferior del comprador: Inicio · Catálogo · Carrito · Perfil. */
    fun setupBuyerNav(activity: Activity, nav: BottomNavigationView, selected: Int) {
        nav.menu.clear()
        nav.inflateMenu(R.menu.menu_buyer_bottom)
        bind(activity, nav, selected) { id ->
            when (id) {
                R.id.nav_home -> BuyerHomeActivity::class.java
                R.id.nav_catalog -> CatalogActivity::class.java
                R.id.nav_cart -> CartActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> null
            }
        }
    }

    /** Barra inferior del vendedor: Productos · Pedidos · Perfil. */
    fun setupSellerNav(activity: Activity, nav: BottomNavigationView, selected: Int) {
        nav.menu.clear()
        nav.inflateMenu(R.menu.menu_seller_bottom)
        bind(activity, nav, selected) { id ->
            when (id) {
                R.id.nav_products -> SellerProductsActivity::class.java
                R.id.nav_orders -> SellerOrdersActivity::class.java
                R.id.nav_profile -> ProfileActivity::class.java
                else -> null
            }
        }
    }

    /** Marca el ítem actual y reemplaza la pantalla al tocar otro ítem (sin animación). */
    private fun bind(
        activity: Activity,
        nav: BottomNavigationView,
        selected: Int,
        target: (Int) -> Class<out Activity>?
    ) {
        nav.selectedItemId = selected
        nav.setOnItemSelectedListener { item ->
            if (item.itemId == selected) return@setOnItemSelectedListener true
            val next = target(item.itemId) ?: return@setOnItemSelectedListener false
            activity.startActivity(Intent(activity, next).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
            activity.overridePendingTransition(0, 0)
            activity.finish()
            true
        }
    }
}
