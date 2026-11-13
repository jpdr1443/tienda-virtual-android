package com.tiendavirtual.app.ui.buyer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.tiendavirtual.app.BuildConfig
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.CartItem
import com.tiendavirtual.app.data.repository.CartRepository
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityCheckoutBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.util.DeliveryLocation
import com.tiendavirtual.app.util.Formatters
import com.tiendavirtual.app.util.LocationHelper

/**
 * Checkout: dirección de entrega (manual o con GPS), resumen y pago.
 * Al pagar se crea la orden "pendiente de pago" y se abre la pasarela
 * ePayco en [PaymentActivity].
 */
class CheckoutActivity : BaseActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var cart: List<CartItem> = emptyList()
    private var location: DeliveryLocation? = null

    /** Solicitud del permiso de ubicación en tiempo de ejecución. */
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) captureLocation() else toast(R.string.error_location_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.checkout_title)

        binding.btnLocation.setOnClickListener { requestLocation() }
        binding.btnPay.setOnClickListener { pay() }
        load()
    }

    /** Carga el carrito y la última dirección guardada del comprador. */
    private fun load() = launchUi(binding.progress) {
        val me = UserRepository().currentProfile()
        cart = CartRepository().getItems(me.uid)
        if (cart.isEmpty()) {
            toast(R.string.cart_empty)
            finish()
            return@launchUi
        }
        if (binding.inputAddress.text.isNullOrBlank()) binding.inputAddress.setText(me.address)
        if (binding.inputCity.text.isNullOrBlank()) binding.inputCity.setText(me.city)
        binding.textItems.text = cart.joinToString("\n") {
            "${it.quantity} × ${it.name}  ·  ${Formatters.money(it.subtotal())}"
        }
        binding.textTotal.text = Formatters.money(cart.sumOf { it.subtotal() })
    }

    private fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) captureLocation()
        else locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    /** Obtiene la ubicación con GPS y llena la dirección con la geocodificación inversa. */
    private fun captureLocation() = launchUi(binding.progressLocation, binding.btnLocation) {
        val result = LocationHelper(this).current()
        location = result
        if (result.address.isNotBlank()) binding.inputAddress.setText(result.address)
        if (result.city.isNotBlank()) binding.inputCity.setText(result.city)
        binding.textCoordinates.visibility = View.VISIBLE
        binding.textCoordinates.text = getString(
            R.string.checkout_coordinates, result.latitude, result.longitude
        )
        toast(R.string.msg_location_ok)
    }

    /** Valida la dirección, crea la orden pendiente y abre la pasarela de pagos. */
    private fun pay() {
        val address = binding.inputAddress.text?.toString().orEmpty().trim()
        val city = binding.inputCity.text?.toString().orEmpty().trim()
        binding.layoutAddress.error = if (address.length >= 5) null else getString(R.string.error_address)
        binding.layoutCity.error = if (city.isNotBlank()) null else getString(R.string.error_city)
        if (binding.layoutAddress.error != null || binding.layoutCity.error != null) return

        if (BuildConfig.EPAYCO_PUBLIC_KEY.isBlank()) {
            toast(R.string.error_epayco_key)
            return
        }

        launchUi(binding.progress, binding.btnPay) {
            val me = UserRepository().currentProfile()
            val order = OrderRepository().createPending(me, cart, address, city, location)
            UserRepository().saveDefaultAddress(me.uid, address, city)
            PaymentActivity.start(this, order)
            finish()
        }
    }
}
