package com.tiendavirtual.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tiendavirtual.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

/** Resultado de la geolocalización: coordenadas y dirección legible. */
data class DeliveryLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String
)

/**
 * Obtiene la ubicación del comprador con el Fused Location Provider de
 * Google Play Services y la convierte en dirección con Geocoder
 * (geocodificación inversa). Se usa para llenar la dirección de entrega.
 *
 * Antes de llamar a [current] la Activity debe haber obtenido el permiso
 * ACCESS_FINE_LOCATION o ACCESS_COARSE_LOCATION.
 */
class LocationHelper(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // El permiso se verifica en CheckoutActivity.
    suspend fun current(): DeliveryLocation {
        // 1. Pide una lectura fresca de alta precisión (GPS); si no llega, usa la última conocida.
        val location = client.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token
        ).await() ?: client.lastLocation.await()
            ?: throw AppException(R.string.error_location_unavailable)

        // 2. Geocodificación inversa en un hilo de E/S (la llamada es bloqueante).
        val address = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("es", "CO"))
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
            }.getOrNull()
        }

        return DeliveryLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            address = address?.let {
                listOfNotNull(it.thoroughfare, it.subThoroughfare).joinToString(" # ")
                    .ifBlank { it.getAddressLine(0).orEmpty() }
            }.orEmpty(),
            city = address?.locality ?: address?.subAdminArea.orEmpty()
        )
    }
}
