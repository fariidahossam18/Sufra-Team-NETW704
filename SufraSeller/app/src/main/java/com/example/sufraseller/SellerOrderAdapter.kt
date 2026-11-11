package com.example.sufraseller

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sufraseller.databinding.ItemSellerOrderBinding

/**
 * Adapter for the seller's incoming orders (Seller app, Milestone 3).
 * Each card: order number, status, time, the items with quantities, the buyer's details and the total.
 */
class SellerOrderAdapter : RecyclerView.Adapter<SellerOrderAdapter.OrderViewHolder>() {

    private val orders = mutableListOf<Order>()

    class OrderViewHolder(val binding: ItemSellerOrderBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemSellerOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        val b = holder.binding

        b.tvOrderId.text = "Order ${shortOrderId(order.id)}"
        b.tvStatus.text = order.status.replaceFirstChar { it.uppercase() }
        b.tvTime.text = formatTimestamp(order.createdAt)
        b.tvItems.text = order.items.joinToString("\n") { item ->
            "${item.quantity} × ${item.name}    ${formatPrice(item.subtotal)}"
        }
        b.tvBuyer.text = "👤 ${order.buyerName}   📞 ${order.buyerPhone}"
        b.tvAddress.text = "📍 ${order.address}"
        b.tvTotal.text = formatPrice(order.totalPrice)
    }
}
