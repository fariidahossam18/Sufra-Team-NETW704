package com.example.sufrabuyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sufrabuyer.databinding.ItemProductBinding
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter for the products grid in a shop (Buyer app, Milestone 3).
 *
 * Each card shows the photo, name, price and stock. If the product is not in the cart yet
 * it shows "Add to cart", otherwise it shows a  -  qty  +  stepper.
 * The fragment passes in what should happen on each button (lambdas).
 */
class ProductAdapter(
    private val scope: CoroutineScope,
    private val quantityInCart: (Product) -> Int,
    private val onAdd: (Product) -> Unit,
    private val onIncrease: (Product) -> Unit,
    private val onDecrease: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val products = mutableListOf<Product>()

    /** Holds the views of one card, so we don't search for them again (no findViewById). */
    class ProductViewHolder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Product>) {
        products.clear()
        products.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val b = holder.binding

        b.tvName.text = product.name
        b.tvDescription.text = product.description
        b.tvDescription.visibility = if (product.description.isBlank()) View.GONE else View.VISIBLE
        b.tvPrice.text = formatPrice(product.price)
        b.tvStock.text = when {
            !product.inStock -> "Sold out"
            product.quantity <= 5 -> "Only ${product.quantity} left"
            else -> "${product.quantity} in stock"
        }
        b.tvSoldOut.visibility = if (product.inStock) View.GONE else View.VISIBLE
        b.root.alpha = if (product.inStock) 1f else 0.6f
        ImageLoader.load(b.ivProduct, product.imageUrl, scope)

        // Add button or stepper, depending on how many are in the cart
        val inCart = quantityInCart(product)
        b.btnAdd.visibility = if (inCart == 0) View.VISIBLE else View.GONE
        b.stepper.visibility = if (inCart == 0) View.GONE else View.VISIBLE
        b.btnAdd.isEnabled = product.inStock
        b.btnAdd.text = if (product.inStock) "Add to cart" else "Sold out"
        b.tvQty.text = inCart.toString()
        b.btnPlus.alpha = if (inCart >= product.quantity) 0.35f else 1f   // looks disabled at max stock

        b.btnAdd.setOnClickListener { onAdd(product) }
        b.btnPlus.setOnClickListener { onIncrease(product) }
        b.btnMinus.setOnClickListener { onDecrease(product) }
    }
}
