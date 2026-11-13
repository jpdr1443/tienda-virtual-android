package com.tiendavirtual.app.ui.buyer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiendavirtual.app.BuildConfig
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Order
import com.tiendavirtual.app.data.payment.EpaycoService
import com.tiendavirtual.app.data.payment.PaymentStatus
import com.tiendavirtual.app.data.repository.CartRepository
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.databinding.ActivityPaymentBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import org.json.JSONObject

/**
 * Pasarela de pagos ePayco.
 *
 * 1. Carga en un WebView el Checkout estándar de ePayco (checkout.js) con los
 *    datos de la orden: valor, factura (= id de la orden), correo y nombre.
 * 2. El comprador paga con tarjeta, PSE, Nequi, Daviplata, etc. dentro de ePayco;
 *    la app nunca ve ni guarda datos de tarjetas.
 * 3. ePayco redirige a [EpaycoService.RESPONSE_URL]?ref_payco=…; la app intercepta
 *    esa URL, valida la referencia contra la API de ePayco y actualiza la orden.
 */
class PaymentActivity : BaseActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var handled = false
    private lateinit var orderId: String

    @SuppressLint("SetJavaScriptEnabled") // checkout.js de ePayco requiere JavaScript.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.payment_title)

        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        binding.toolbar.setNavigationOnClickListener { confirmExit() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else confirmExit()
            }
        })

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false) // Las ventanas emergentes (bancos PSE) se abren en el mismo WebView.
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                interceptResponse(request.url.toString())

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (interceptResponse(url)) view.stopLoading()
                binding.progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.progress.visibility = View.GONE
            }
        }

        binding.webView.loadDataWithBaseURL(
            "https://tiendavirtual.app/", buildCheckoutHtml(), "text/html", "UTF-8", null
        )
    }

    /** HTML que configura y abre el checkout de ePayco con los datos de la orden. */
    private fun buildCheckoutHtml(): String {
        val data = JSONObject()
            .put("name", getString(R.string.payment_item_name))
            .put("description", intent.getStringExtra(EXTRA_DESCRIPTION))
            .put("invoice", orderId)
            .put("currency", "cop")
            .put("amount", intent.getLongExtra(EXTRA_AMOUNT, 0).toString())
            .put("tax_base", "0")
            .put("tax", "0")
            .put("country", "co")
            .put("lang", "es")
            .put("external", "false")
            .put("response", EpaycoService.RESPONSE_URL)
            .put("email_billing", intent.getStringExtra(EXTRA_EMAIL))
            .put("name_billing", intent.getStringExtra(EXTRA_NAME))
            .put("extra1", orderId)
        val config = JSONObject()
            .put("key", BuildConfig.EPAYCO_PUBLIC_KEY)
            .put("test", BuildConfig.EPAYCO_TEST)

        return """
            <!DOCTYPE html>
            <html><head>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <script src="https://checkout.epayco.co/checkout.js"></script>
              <style>body{font-family:sans-serif;text-align:center;padding:48px 16px;color:#0F172A}</style>
            </head><body>
              <p>${getString(R.string.payment_loading)}</p>
              <script>
                window.onload = function () {
                  var cfg = $config;
                  var handler = ePayco.checkout.configure({ key: cfg.key, test: cfg.test });
                  handler.open($data);
                };
              </script>
            </body></html>
        """.trimIndent()
    }

    /**
     * Si la URL es la de respuesta de ePayco, toma ref_payco y valida el pago.
     * @return true si la URL fue interceptada (no se debe cargar).
     */
    private fun interceptResponse(url: String): Boolean {
        if (!url.startsWith(EpaycoService.RESPONSE_URL)) return false
        if (handled) return true
        handled = true
        val ref = Uri.parse(url).getQueryParameter("ref_payco")
        if (ref.isNullOrBlank()) {
            showResult(PaymentStatus.FAILED, getString(R.string.payment_no_reference))
        } else {
            validate(ref)
        }
        return true
    }

    /** Confirma el estado con la API de ePayco y actualiza la orden en Firestore. */
    private fun validate(ref: String) = launchUi(binding.progress) {
        binding.webView.visibility = View.GONE
        binding.textValidating.visibility = View.VISIBLE
        val result = EpaycoService.validate(ref)
        val orders = OrderRepository()

        // Defensa: la transacción debe corresponder a esta orden.
        if (result.invoice.isNotBlank() && result.invoice != orderId) {
            orders.reject(orderId, ref, getString(R.string.payment_invoice_mismatch))
            showResult(PaymentStatus.FAILED, getString(R.string.payment_invoice_mismatch))
            return@launchUi
        }

        when (result.status) {
            PaymentStatus.ACCEPTED -> {
                orders.confirmPayment(orderId, result.reference, result.transactionId, result.message)
                orders.get(orderId)?.let { CartRepository().clear(it.buyerId) }
            }
            PaymentStatus.PENDING -> orders.markPending(orderId, result.reference, result.message)
            else -> orders.reject(orderId, result.reference, result.message)
        }
        showResult(result.status, result.message)
    }

    /** Muestra el resultado y lleva al historial de pedidos del comprador. */
    private fun showResult(status: PaymentStatus, detail: String) {
        val (title, message) = when (status) {
            PaymentStatus.ACCEPTED -> R.string.payment_accepted to R.string.payment_accepted_message
            PaymentStatus.PENDING -> R.string.payment_pending to R.string.payment_pending_message
            PaymentStatus.REJECTED -> R.string.payment_rejected to R.string.payment_rejected_message
            PaymentStatus.FAILED -> R.string.payment_failed to R.string.payment_rejected_message
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(getString(message) + if (detail.isNotBlank()) "\n\n$detail" else "")
            .setCancelable(false)
            .setPositiveButton(R.string.see_my_orders) { _, _ ->
                startActivity(Intent(this, BuyerOrdersActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .show()
    }

    /** Salir sin pagar deja la orden pendiente en el historial. */
    private fun confirmExit() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.payment_exit_title)
            .setMessage(R.string.payment_exit_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.exit) { _, _ -> finish() }
            .show()
    }

    companion object {
        private const val EXTRA_ORDER_ID = "order_id"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_DESCRIPTION = "description"
        private const val EXTRA_EMAIL = "email"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, order: Order) {
            context.startActivity(Intent(context, PaymentActivity::class.java)
                .putExtra(EXTRA_ORDER_ID, order.id)
                .putExtra(EXTRA_AMOUNT, order.total)
                .putExtra(EXTRA_DESCRIPTION, order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }.take(200))
                .putExtra(EXTRA_EMAIL, order.buyerEmail)
                .putExtra(EXTRA_NAME, order.buyerName))
        }
    }
}
