package com.example.sufraseller

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sufraseller.databinding.ItemSellerProductBinding
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter for the seller's products grid (Seller app, Milestone 3).
 *
 * Each card shows the photo, name, price and stock, plus a  -  qty  +  stepper that changes
 * the stock straight away. Tapping the card opens the full edit screen.
 */
class SellerProductAdapter(
    private val scope: CoroutineScope,
    private val onClick: (Product) -> Unit,
    private val onChangeQuantity: (Product, Int) -> Unit
) : RecyclerView.Adapter<SellerProductAdapter.SellerProductViewHolder>() {

    private val products = mutableListOf<Product>()

    class SellerProductViewHolder(val binding: ItemSellerProductBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Product>) {
        products.clear()
        products.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SellerProductViewHolder {
        val binding = ItemSellerProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SellerProductViewHolder(binding)
    }

    override fun getItemCount(): Int = products.size

    override fun onBindViewHolder(holder: SellerProductViewHolder, position: Int) {
        val product = products[position]
        val b = holder.binding
        val context = b.root.context

        b.tvName.text = product.name
        b.tvPrice.text = formatPrice(product.price)
        b.tvQty.text = product.quantity.toString()
        b.tvStockBadge.text = if (product.inStock) "${product.quantity} in stock" else "Out of stock"
        b.tvStockBadge.setTextColor(
            ContextCompat.getColor(context, if (product.inStock) R.color.brand_green else R.color.brand_red)
        )
        b.btnMinus.alpha = if (product.quantity <= 0) 0.35f else 1f
        ImageLoader.load(b.ivProduct, product.imageUrl, scope)

        b.root.setOnClickListener { onClick(product) }
        b.btnMinus.setOnClickListener { onChangeQuantity(product, -1) }
        b.btnPlus.setOnClickListener { onChangeQuantity(product, +1) }
    }
}
