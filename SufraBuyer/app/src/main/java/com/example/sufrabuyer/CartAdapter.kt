package com.example.sufrabuyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sufrabuyer.databinding.ItemCartBinding
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter for the list of products in the cart (Buyer app, Milestone 3).
 * Each row: photo, name, unit price, subtotal, a  -  qty  +  stepper and a remove button.
 */
class CartAdapter(
    private val scope: CoroutineScope,
    private val onIncrease: (CartItem) -> Unit,
    private val onDecrease: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItem>()

    class CartViewHolder(val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<CartItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.tvName.text = item.name
        b.tvUnitPrice.text = "${formatPrice(item.price)} each"
        b.tvSubtotal.text = formatPrice(item.subtotal)
        b.tvQty.text = item.quantity.toString()
        b.btnPlus.alpha = if (item.quantity >= item.stock) 0.35f else 1f
        ImageLoader.load(b.ivProduct, item.imageUrl, scope)

        b.btnPlus.setOnClickListener { onIncrease(item) }
        b.btnMinus.setOnClickListener { onDecrease(item) }
        b.btnRemove.setOnClickListener { onRemove(item) }
    }
}
