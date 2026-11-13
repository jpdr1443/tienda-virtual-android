package com.tiendavirtual.app.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.tiendavirtual.app.R
import com.tiendavirtual.app.data.model.Product
import com.tiendavirtual.app.data.repository.CartRepository
import com.tiendavirtual.app.data.repository.ProductRepository
import com.tiendavirtual.app.data.repository.UserRepository
import com.tiendavirtual.app.databinding.ActivityProductDetailBinding
import com.tiendavirtual.app.ui.common.BaseActivity
import com.tiendavirtual.app.ui.common.loadProductImage
import com.tiendavirtual.app.util.Formatters

/**
 * Detalle de un producto: imagen, descripción, stock y selector de cantidad.
 * "Agregar" lo pone en el carrito; "Comprar ahora" lo agrega y abre el carrito.
 */
class ProductDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private var product: Product? = null
    private var quantity = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, R.string.detail_title)

        binding.btnMinus.setOnClickListener { changeQuantity(-1) }
        binding.btnPlus.setOnClickListener { changeQuantity(+1) }
        binding.btnAddCart.setOnClickListener { addToCart(goToCart = false) }
        binding.btnBuyNow.setOnClickListener { addToCart(goToCart = true) }
        load(intent.getStringExtra(EXTRA_ID).orEmpty())
    }

    private fun load(id: String) = launchUi(binding.progress) {
        val p = ProductRepository().get(id)
        if (p == null || !p.active) {
            toast(R.string.error_product_missing)
            finish()
            return@launchUi
        }
        product = p
        binding.imageProduct.loadProductImage(p.imageUrl)
        binding.textName.text = p.name
        binding.textPrice.text = Formatters.money(p.price)
        binding.textCategory.text = p.category
        binding.textDescription.text = p.description
        binding.textSeller.text = getString(R.string.sold_by, p.sellerName)
        binding.textStock.text = getString(R.string.detail_stock_value, p.stock)
        val available = p.stock > 0
        binding.btnAddCart.isEnabled = available
        binding.btnBuyNow.isEnabled = available
        if (!available) binding.textStock.setText(R.string.status_out_of_stock)
        updateQuantity()
    }

    /** No permite bajar de 1 ni superar el stock disponible. */
    private fun changeQuantity(delta: Int) {
        val max = product?.stock ?: return
        quantity = (quantity + delta).coerceIn(1L, maxOf(1L, max))
        updateQuantity()
    }

    private fun updateQuantity() {
        binding.textQuantity.text = quantity.toString()
        binding.textTotal.text = Formatters.money((product?.price ?: 0L) * quantity)
    }

    /** Create del CRUD del carrito. */
    private fun addToCart(goToCart: Boolean) {
        val p = product ?: return
        launchUi(binding.progress, binding.btnAddCart, binding.btnBuyNow) {
            val uid = UserRepository().currentProfile().uid
            val inCart = CartRepository().getItems(uid).firstOrNull { it.productId == p.id }?.quantity ?: 0L
            if (inCart + quantity > p.stock) {
                toast(getString(R.string.error_stock, p.name))
                return@launchUi
            }
            CartRepository().add(uid, p, quantity)
            toast(R.string.msg_added_cart)
            if (goToCart) startActivity(Intent(this, CartActivity::class.java))
        }
    }

    companion object {
        private const val EXTRA_ID = "product_id"

        fun open(context: Context, productId: String) {
            context.startActivity(Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_ID, productId))
        }
    }
}
