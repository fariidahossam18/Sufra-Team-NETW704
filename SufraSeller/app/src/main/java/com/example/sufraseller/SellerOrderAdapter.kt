package com.example.sufraseller

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sufraseller.databinding.ItemSellerOrderBinding

/**
 * Adapter for the seller's orders (Seller app, Milestones 3 and 4).
 *
 * Each card: order number, coloured status chip, time, Delivery / Pick up, items, buyer,
 * address, subtotal + fee, total, and the buttons that move the order forward:
 *
 *   Waiting    -> [Reject]  [Accept]
 *   Preparing  -> [Out for delivery]  or  [Ready for pickup]
 *   On the way / Ready -> [Mark as delivered]
 *   Delivered / Rejected / Cancelled -> no buttons
 */
class SellerOrderAdapter(
    private val onNext: (Order) -> Unit,
    private val onReject: (Order) -> Unit
) : RecyclerView.Adapter<SellerOrderAdapter.OrderViewHolder>() {

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
        val context = b.root.context

        b.tvOrderId.text = "Order ${shortOrderId(order.id)}"
        b.tvStatus.text = "${Order.statusEmoji(order.status)} ${Order.shortLabel(order.status)}"
        b.tvStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, Order.statusColor(order.status)))
        b.tvTime.text = formatTimestamp(order.createdAt)
        b.tvMethod.text = when {
            !order.isDelivery -> "🛍️ Pick up"
            order.hasDriver -> "🛵 Delivery · driver ${order.driverName}"     // Delivery app (bonus)
            else -> "🛵 Delivery"
        }

        b.tvItems.text = order.items.joinToString("\n") { item ->
            "${item.quantity} × ${item.name}    ${formatPrice(item.subtotal)}"
        }
        b.tvBuyer.text = "👤 ${order.buyerName}   📞 ${order.buyerPhone}"
        b.tvAddress.text = if (order.isDelivery) "📍 ${order.address}" else "📍 Buyer picks it up from the shop"
        val fee = if (order.deliveryFee == 0.0) "free" else formatPrice(order.deliveryFee)
        b.tvPrices.text = if (order.isDelivery) "Items ${formatPrice(order.subtotal)} · delivery $fee"
        else "Items ${formatPrice(order.subtotal)}"
        b.tvTotal.text = formatPrice(order.totalPrice)

        // ---- buttons ----
        val next = order.nextStatus()
        b.actions.visibility = if (next == null) View.GONE else View.VISIBLE
        b.btnReject.visibility = if (order.status == Order.STATUS_WAITING) View.VISIBLE else View.GONE
        b.btnNext.text = when (next) {
            Order.STATUS_PREPARING -> "Accept"
            Order.STATUS_DELIVERING -> "Out for delivery"
            Order.STATUS_READY -> "Ready for pickup"
            Order.STATUS_DELIVERED -> "Mark as delivered"
            else -> ""
        }
        b.btnNext.setOnClickListener { onNext(order) }
        b.btnReject.setOnClickListener { onReject(order) }
    }
}
