package com.tiendavirtual.app.ui.common

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.model.Role
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.data.repository.AuthRepository
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityProfileBinding
import com.tiendavirtual.app.ui.buyer.BuyerOrdersActivity
import com.tiendavirtual.app.util.BiometricAuth

/**
 * Perfil del usuario, compartido por los tres roles. Lo que cambia según el rol:
 *  - Vendedor: nombre de la tienda, estadísticas de productos y ventas, barra inferior de vendedor.
 *  - Comprador: acceso a "Mis pedidos" y barra inferior de comprador.
 *  - Administrador: se abre desde el menú lateral, sin barra inferior.
 * Todos pueden editar sus datos, activar/desactivar la huella y cerrar sesión.
 */
class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var biometric: BiometricAuth
    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        biometric = BiometricAuth(this)
        // Barra provisional mientras carga el perfil: el botón de cerrar sesión queda disponible de inmediato.
        setupToolbar(binding.toolbar, R.string.buyer_profile_title, showBack = false, showLogout = true)

        binding.btnSave.setOnClickListener { save() }
        binding.btnLogout.setOnClickListener { Navigator.logout(this) }
        binding.rowOrders.setOnClickListener { startActivity(Intent(this, BuyerOrdersActivity::class.java)) }
        setupBiometricSwitch()
        load()
    }

    private fun load() = launchUi(binding.progress) {
        val me = UserRepository().currentProfile(forceRefresh = true)
        user = me
        val role = me.roleEnum

        when (role) {
            Role.BUYER -> {
                setupToolbar(binding.toolbar, R.string.buyer_profile_title, showBack = false, showLogout = true)
                Navigator.setupBuyerNav(this, binding.bottomNav, R.id.nav_profile)
            }
            Role.SELLER -> {
                setupToolbar(binding.toolbar, R.string.seller_profile_title, showBack = false, showLogout = true)
                Navigator.setupSellerNav(this, binding.bottomNav, R.id.nav_profile)
            }
            Role.ADMIN -> {
                setupToolbar(binding.toolbar, R.string.admin_profile_title, showLogout = true)
                binding.bottomNav.visibility = View.GONE
            }
        }

        binding.textInitials.text = me.initials
        binding.textName.text = me.name
        binding.textEmail.text = me.email
        binding.textRole.setText(role.label)
        binding.inputName.setText(me.name)
        binding.inputPhone.setText(me.phone)
        binding.inputStore.setText(me.storeName)
        binding.inputAddress.setText(me.address)
        binding.inputCity.setText(me.city)

        binding.layoutStore.visibility = if (role == Role.SELLER) View.VISIBLE else View.GONE
        binding.rowOrders.visibility = if (role == Role.BUYER) View.VISIBLE else View.GONE
        binding.stats.visibility = if (role == Role.SELLER) View.VISIBLE else View.GONE

        if (role == Role.SELLER) {
            val products = ProductRepository().getBySeller(me.uid)
            val orders = OrderRepository().getBySeller(me.uid).filter { it.statusEnum in OrderStatus.SOLD }
            binding.statProducts.text = products.size.toString()
            binding.statSales.text = orders.size.toString()
            binding.statRevenue.text = com.tiendavirtual.app.util.Formatters.money(
                orders.sumOf { o -> o.items.filter { it.sellerId == me.uid }.sumOf { it.price * it.quantity } }
            )
        }
    }

    /** Update del perfil propio. */
    private fun save() {
        val current = user ?: return
        val name = binding.inputName.text?.toString().orEmpty().trim()
        binding.layoutName.error = if (name.length >= 3) null else getString(R.string.error_name)
        if (binding.layoutName.error != null) return

        val updated = current.copy(
            name = name,
            phone = binding.inputPhone.text?.toString().orEmpty().trim(),
            storeName = binding.inputStore.text?.toString().orEmpty().trim(),
            address = binding.inputAddress.text?.toString().orEmpty().trim(),
            city = binding.inputCity.text?.toString().orEmpty().trim()
        )
        launchUi(binding.progress, binding.btnSave) {
            UserRepository().update(updated)
            user = updated
            binding.textName.text = updated.name
            binding.textInitials.text = updated.initials
            toast(R.string.msg_saved)
        }
    }

    /** Interruptor de la huella: al activarlo pide la contraseña para cifrarla con la huella. */
    private fun setupBiometricSwitch() {
        if (!biometric.isAvailable()) {
            binding.switchBiometric.isEnabled = false
            binding.textBiometricHint.setText(R.string.biometric_unavailable)
            return
        }
        binding.switchBiometric.isChecked = biometric.isEnabled()
        binding.switchBiometric.setOnCheckedChangeListener { _, checked ->
            if (checked && !biometric.isEnabled()) askPasswordAndEnable()
            if (!checked && biometric.isEnabled()) {
                biometric.disable()
                toast(R.string.biometric_disabled)
            }
        }
    }

    private fun askPasswordAndEnable() {
        val input = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = TextInputLayout(this).apply {
            hint = getString(R.string.hint_password)
            addView(input)
        }
        val container = FrameLayout(this).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.spacing_l)
            setPadding(pad, pad / 2, pad, 0)
            addView(layout)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.biometric_enable_title)
            .setMessage(R.string.biometric_password_message)
            .setView(container)
            .setNegativeButton(R.string.cancel) { _, _ -> binding.switchBiometric.isChecked = false }
            .setPositiveButton(R.string.activate) { _, _ ->
                val password = input.text?.toString().orEmpty()
                launchUi(binding.progress) {
                    try {
                        AuthRepository().verifyPassword(password)
                    } catch (e: Exception) {
                        binding.switchBiometric.isChecked = false
                        throw e
                    }
                    biometric.enable(user?.email.orEmpty(), password) { ok ->
                        binding.switchBiometric.isChecked = ok
                        toast(if (ok) R.string.biometric_enabled else R.string.biometric_not_activated)
                    }
                }
            }
            .setCancelable(false)
            .show()
    }
}
