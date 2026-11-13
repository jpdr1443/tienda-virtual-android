package com.tiendavirtual.app.data.payment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Estados de transacción que devuelve ePayco en x_cod_response. */
enum class PaymentStatus { ACCEPTED, REJECTED, PENDING, FAILED }

/**
 * Resultado validado de una transacción.
 * @property invoice número de factura enviado al checkout (el id de la orden).
 */
data class PaymentResult(
    val status: PaymentStatus,
    val reference: String,
    val transactionId: String,
    val invoice: String,
    val amount: Double,
    val message: String
)

/**
 * Cliente de la pasarela ePayco.
 *
 * El pago se hace con el Checkout estándar de ePayco dentro de un WebView
 * (ver PaymentActivity). Al terminar, ePayco redirige a la URL de respuesta
 * con el parámetro "ref_payco". Con esa referencia este servicio consulta el
 * endpoint público de validación para confirmar el estado real de la
 * transacción, en lugar de confiar en lo que diga la URL.
 */
object EpaycoService {

    /** URL de respuesta configurada en el checkout. La app la intercepta; nunca se abre. */
    const val RESPONSE_URL = "https://tiendavirtual.app/pago/respuesta"

    private const val VALIDATION_URL = "https://secure.epayco.co/validation/v1/reference/"

    /** Consulta el estado de la transacción en ePayco (llamada de red en hilo de E/S). */
    suspend fun validate(refPayco: String): PaymentResult = withContext(Dispatchers.IO) {
        val connection = (URL(VALIDATION_URL + refPayco).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (!json.optBoolean("success")) {
                return@withContext PaymentResult(
                    PaymentStatus.FAILED, refPayco, "", "", 0.0,
                    json.optString("text_response", "Transacción no encontrada")
                )
            }
            val data = json.getJSONObject("data")
            PaymentResult(
                status = when (data.optInt("x_cod_response")) {
                    1 -> PaymentStatus.ACCEPTED
                    2 -> PaymentStatus.REJECTED
                    3 -> PaymentStatus.PENDING
                    else -> PaymentStatus.FAILED
                },
                reference = data.optString("x_ref_payco", refPayco),
                transactionId = data.optString("x_transaction_id"),
                invoice = data.optString("x_id_invoice"),
                amount = data.optDouble("x_amount", 0.0),
                message = data.optString("x_response_reason_text", data.optString("x_response"))
            )
        } finally {
            connection.disconnect()
        }
    }
}
