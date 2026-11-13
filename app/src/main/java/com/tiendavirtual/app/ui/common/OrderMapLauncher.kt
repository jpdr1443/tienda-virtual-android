package com.tiendavirtual.app.ui.common

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Order

/**
 * Abre la ubicación de entrega de una orden en Google Maps (o en cualquier
 * app de mapas instalada) usando las coordenadas capturadas en el checkout.
 */
object OrderMapLauncher {

    fun open(activity: Activity, order: Order) {
        val lat = order.latitude
        val lng = order.longitude
        val uri = if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(order.code)})")
        } else {
            Uri.parse("geo:0,0?q=" + Uri.encode("${order.address}, ${order.city}"))
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, R.string.error_no_maps, Toast.LENGTH_SHORT).show()
        }
    }
}
