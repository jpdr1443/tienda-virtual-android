package com.tiendavirtual.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.Timestamp
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Order
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.repository.OrderRepository
import com.tiendavirtual.app.databinding.ActivityAdminSalesBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.util.Formatters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Reporte de ventas del Administrador. Toma las órdenes pagadas del periodo
 * elegido (semana, mes o año), calcula totales, dibuja un gráfico de barras
 * por día y permite exportar el detalle en CSV con el menú Compartir.
 */
class AdminSalesActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminSalesBinding
    private var sold: List<Order> = emptyList()
    private val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminSalesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.admin_sales_title)

        binding.periodGroup.setOnCheckedStateChangeListener { _, _ -> load() }
        binding.btnExport.setOnClickListener { exportCsv() }
        load()
    }

    /** Fecha de inicio según el chip seleccionado. */
    private fun periodStart(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        when (binding.periodGroup.checkedChipId) {
            R.id.chip_week -> add(Calendar.DAY_OF_YEAR, -6)
            R.id.chip_year -> set(Calendar.DAY_OF_YEAR, 1)
            else -> set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun load() = launchUi(binding.progress) {
        sold = OrderRepository().getSince(Timestamp(periodStart().time))
            .filter { it.statusEnum in OrderStatus.SOLD }

        binding.textTotal.text = Formatters.money(sold.sumOf { it.total })
        binding.textOrders.text = getString(R.string.sales_orders_count, sold.size)
        binding.textUnits.text = getString(R.string.sales_units_count, sold.sumOf { it.unitCount() })

        val byDay = sold.groupBy { dayKey.format(it.createdAt!!.toDate()) }.toSortedMap(reverseOrder())
        drawChart(byDay)
        drawTable(byDay)
        binding.textEmpty.visibility = if (sold.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Gráfico de barras construido con vistas: la altura de cada barra es proporcional al total del día. */
    private fun drawChart(byDay: Map<String, List<Order>>) {
        binding.chart.removeAllViews()
        val days = byDay.entries.take(MAX_BARS).reversed()
        val max = days.maxOfOrNull { e -> e.value.sumOf { it.total } } ?: return
        val maxHeight = resources.getDimensionPixelSize(R.dimen.bar_chart_height)

        days.forEach { (key, orders) ->
            val bar = LayoutInflater.from(this).inflate(R.layout.item_chart_bar, binding.chart, false)
            val total = orders.sumOf { it.total }
            bar.findViewById<View>(R.id.bar).layoutParams.height =
                (maxHeight * total / max.toDouble()).toInt().coerceAtLeast(MIN_BAR_PX)
            bar.findViewById<TextView>(R.id.bar_label).text = key.substring(8) // día del mes
            binding.chart.addView(bar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Tabla con una fila por día: fecha · pedidos · total. */
    private fun drawTable(byDay: Map<String, List<Order>>) {
        binding.table.removeAllViews()
        byDay.forEach { (_, orders) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_sales_row, binding.table, false) as TextView
            row.text = getString(
                R.string.sales_row, Formatters.day(orders.first().createdAt),
                orders.size, Formatters.money(orders.sumOf { it.total })
            )
            binding.table.addView(row)
        }
    }

    /** Genera un CSV con las órdenes del periodo y abre el menú Compartir (correo, Drive, WhatsApp…). */
    private fun exportCsv() {
        if (sold.isEmpty()) {
            toast(R.string.sales_empty)
            return
        }
        val csv = buildString {
            appendLine("codigo,fecha,comprador,ciudad,unidades,total,estado,referencia_epayco")
            sold.forEach {
                appendLine(listOf(
                    it.code, Formatters.dateTime(it.createdAt), it.buyerName, it.city,
                    it.unitCount(), it.total, it.status, it.paymentRef
                ).joinToString(",") { v -> "\"$v\"" })
            }
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sales_export_subject))
                .putExtra(Intent.EXTRA_TEXT, csv),
            getString(R.string.btn_export)
        ))
    }

    private companion object {
        const val MAX_BARS = 7
        const val MIN_BAR_PX = 6
    }
}
