package com.tiendavirtual.app.util

import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/** Utilidades de formato para precios y fechas con la configuración regional de Colombia. */
object Formatters {

    private val colombia = Locale("es", "CO")
    private val number = NumberFormat.getNumberInstance(colombia)
    private val dateTime = SimpleDateFormat("dd MMM yyyy · HH:mm", colombia)
    private val day = SimpleDateFormat("dd MMM", colombia)

    /** 189900 → "$ 189.900" */
    fun money(value: Long): String = "$ " + number.format(value)

    /** Fecha y hora completas de un Timestamp de Firestore. */
    fun dateTime(value: Timestamp?): String = value?.let { dateTime.format(it.toDate()) } ?: "—"

    /** Solo día y mes, usado en el reporte de ventas. */
    fun day(value: Timestamp?): String = value?.let { day.format(it.toDate()) } ?: "—"
}
