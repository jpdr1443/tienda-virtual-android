package com.tiendavirtual.app.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.CartItem
import com.tiendavirtual.app.data.model.Order
import com.tiendavirtual.app.data.model.OrderStatus
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.model.User
import com.tiendavirtual.app.databinding.ItemCartBinding
import com.tiendavirtual.app.databinding.ItemOrderBinding
import com.tiendavirtual.app.databinding.ItemProductGridBinding
import com.tiendavirtual.app.databinding.ItemProductManageBinding
import com.tiendavirtual.app.databinding.ItemUserBinding
import com.tiendavirtual.app.util.Formatters

/*
 * Adaptadores de RecyclerView. Reemplazan las tarjetas fijas en XML de la
 * entrega anterior: ahora cada lista se dibuja con los datos de Firestore.
 */

/** Carga la imagen de un producto desde su URL de Storage, con marcador mientras llega. */
fun ImageView.loadProductImage(url: String) {
    if (url.isBlank()) {
        setImageResource(R.drawable.ic_store)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    } else {
        scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(this).load(url).placeholder(R.drawable.ic_store).into(this)
    }
}

/** Píldora de color para estados (activo, pausado, pagado, etc.). */
fun TextView.showPill(text: Int, color: Int, soft: Int) {
    setText(text)
    setTextColor(ContextCompat.getColor(context, color))
    backgroundTintList = ContextCompat.getColorStateList(context, soft)
}

/** Base con lista mutable que se reemplaza completa con [submit]. */
abstract class SimpleAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    protected var items: List<T> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged") // Listas pequeñas: se redibuja todo.
    fun submit(list: List<T>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}

/** Lista de usuarios del panel del Administrador. */
class UserAdapter(
    private val onEdit: (User) -> Unit,
    private val onDelete: (User) -> Unit
) : SimpleAdapter<User, UserAdapter.VH>() {

    class VH(val b: ItemUserBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.b) {
        val user = items[position]
        textInitials.text = user.initials
        textName.text = user.name
        textEmail.text = user.email
        textRole.setText(user.roleEnum.label)
        textStatus.showPill(
            if (user.active) R.string.status_active else R.string.status_inactive,
            if (user.active) R.color.success else R.color.danger,
            if (user.active) R.color.success_soft else R.color.danger_soft
        )
        btnEdit.setOnClickListener { onEdit(user) }
        btnDelete.setOnClickListener { onDelete(user) }
        root.setOnClickListener { onEdit(user) }
    }
}

/** Lista de gestión de productos (Vendedor y Administrador) con editar, pausar y eliminar. */
class ProductManageAdapter(
    private val showSeller: Boolean,
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : SimpleAdapter<Product, ProductManageAdapter.VH>() {

    class VH(val b: ItemProductManageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductManageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.b) {
        val product = items[position]
        val context = root.context
        imageProduct.loadProductImage(product.imageUrl)
        textName.text = product.name
        textPrice.text = Formatters.money(product.price)
        textStock.text = context.getString(R.string.stock_units, product.stock)
        textSeller.visibility = if (showSeller) View.VISIBLE else View.GONE
        textSeller.text = context.getString(R.string.sold_by, product.sellerName)
        when {
            !product.active -> textStatus.showPill(R.string.status_paused, R.color.warning, R.color.warning_soft)
            product.stock <= 0 -> textStatus.showPill(R.string.status_out_of_stock, R.color.danger, R.color.danger_soft)
            else -> textStatus.showPill(R.string.status_active, R.color.success, R.color.success_soft)
        }
        btnEdit.setOnClickListener { onEdit(product) }
        btnDelete.setOnClickListener { onDelete(product) }
        root.setOnClickListener { onEdit(product) }
    }
}

/** Tarjetas en cuadrícula del catálogo y destacados del comprador. */
class ProductGridAdapter(
    private val onClick: (Product) -> Unit
) : SimpleAdapter<Product, ProductGridAdapter.VH>() {

    class VH(val b: ItemProductGridBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.b) {
        val product = items[position]
        imageProduct.loadProductImage(product.imageUrl)
        textName.text = product.name
        textPrice.text = Formatters.money(product.price)
        textCategory.text = product.category
        textOutOfStock.visibility = if (product.stock <= 0) View.VISIBLE else View.GONE
        root.setOnClickListener { onClick(product) }
    }
}

/** Ítems del carrito con controles de cantidad. */
class CartAdapter(
    private val onQuantity: (CartItem, Long) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : SimpleAdapter<CartItem, CartAdapter.VH>() {

    class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.b) {
        val item = items[position]
        imageProduct.loadProductImage(item.imageUrl)
        textName.text = item.name
        textPrice.text = Formatters.money(item.price)
        textQuantity.text = item.quantity.toString()
        textSubtotal.text = Formatters.money(item.subtotal())
        btnMinus.setOnClickListener { onQuantity(item, item.quantity - 1) }
        btnPlus.setOnClickListener { onQuantity(item, item.quantity + 1) }
        btnRemove.setOnClickListener { onRemove(item) }
    }
}

/**
 * Tarjetas de órdenes. Con [sellerId] muestra solo las líneas de ese vendedor
 * y el botón para avanzar el estado (pagada → enviada → entregada).
 */
class OrderAdapter(
    private val sellerId: String? = null,
    private val onAdvance: ((Order, OrderStatus) -> Unit)? = null,
    private val onClick: ((Order) -> Unit)? = null
) : SimpleAdapter<Order, OrderAdapter.VH>() {

    class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = with(holder.b) {
        val order = items[position]
        val context = root.context
        val lines = if (sellerId == null) order.items else order.items.filter { it.sellerId == sellerId }

        textCode.text = context.getString(R.string.order_code, order.code)
        textDate.text = Formatters.dateTime(order.createdAt)
        textBuyer.text = context.getString(R.string.order_buyer_line, order.buyerName, order.city)
        textItems.text = lines.joinToString("\n") { "${it.quantity} × ${it.name}" }
        textTotal.text = Formatters.money(lines.sumOf { it.price * it.quantity })
        textAddress.text = order.address
        val status = order.statusEnum
        textStatus.showPill(status.label, status.color, status.softColor)

        // Siguiente estado logístico que puede asignar el vendedor.
        val next = when (status) {
            OrderStatus.PAID -> OrderStatus.SHIPPED
            OrderStatus.SHIPPED -> OrderStatus.DELIVERED
            else -> null
        }
        if (onAdvance != null && next != null) {
            btnAdvance.visibility = View.VISIBLE
            btnAdvance.text = context.getString(R.string.order_mark_as, context.getString(next.label))
            btnAdvance.setOnClickListener { onAdvance.invoke(order, next) }
        } else {
            btnAdvance.visibility = View.GONE
        }
        root.setOnClickListener { onClick?.invoke(order) }
    }
}
