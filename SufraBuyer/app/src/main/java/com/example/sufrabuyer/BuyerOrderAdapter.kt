package com.example.sufrabuyer

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sufrabuyer.databinding.ItemBuyerOrderBinding

/**
 * Adapter for the buyer's orders (Buyer app, Milestone 4).
 *
 * Each card: shop, order number and time, a coloured status chip, a progress bar through the
 * states (Waiting -> Preparing -> On the way / Ready -> Delivered) with the current step in bold,
 * the items, delivery or pick up with the fee, the total, and "Cancel order" while waiting.
 */
class BuyerOrderAdapter(
    private val onCancel: (Order) -> Unit
) : RecyclerView.Adapter<BuyerOrderAdapter.OrderViewHolder>() {

    private val orders = mutableListOf<Order>()

    class OrderViewHolder(val binding: ItemBuyerOrderBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemBuyerOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        val b = holder.binding
        val context = b.root.context

        b.tvShop.text = "🏪 ${order.shopName}"
        b.tvOrderInfo.text = "Order ${shortOrderId(order.id)} · ${formatTimestamp(order.createdAt)}"

        // ---- status chip ----
        b.tvStatus.text = "${Order.statusEmoji(order.status)} ${Order.shortLabel(order.status)}"
        b.tvStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, Order.statusColor(order.status)))

        // ---- progress through the steps ----
        val steps = order.steps()
        val index = steps.indexOf(order.status)
        if (index >= 0) {
            b.progressStatus.visibility = View.VISIBLE
            b.progressStatus.setProgressCompat((index + 1) * 100 / steps.size, false)
            b.tvSteps.text = stepsText(steps, index, ContextCompat.getColor(context, R.color.brand_primary))
        } else {
            // rejected or cancelled: no progress, just the explanation
            b.progressStatus.visibility = View.GONE
            b.tvSteps.text = Order.statusLabel(order.status)
        }

        // ---- items, method, total ----
        b.tvItems.text = order.items.joinToString("\n") { "${it.quantity} × ${it.name}    ${formatPrice(it.subtotal)}" }
        val method = if (order.isDelivery) {
            val fee = if (order.deliveryFee == 0.0) "free" else formatPrice(order.deliveryFee)
            "🛵 Delivery to ${order.address} · fee $fee"
        } else {
            "🛍️ Pick up from the shop · free"
        }
        val extras = listOfNotNull(
            if (order.hasDriver) "Driver: ${order.driverName}" else null,             // Delivery app (bonus)
            if (order.creditUsed > 0.0) "Credit used: ${formatPrice(order.creditUsed)}" else null
        )
        b.tvMethod.text = (listOf(method) + extras).joinToString("\n")
        b.tvTotal.text = formatPrice(order.totalPrice)

        // ---- cancel only while the seller hasn't accepted ----
        b.btnCancel.visibility = if (order.status == Order.STATUS_WAITING) View.VISIBLE else View.GONE
        b.btnCancel.setOnClickListener { onCancel(order) }
    }

    /** "Waiting → Preparing → On the way → Delivered" with the current step bold and coloured. */
    private fun stepsText(steps: List<String>, current: Int, color: Int): CharSequence {
        val text = SpannableStringBuilder()
        steps.forEachIndexed { i, step ->
            if (i > 0) text.append("  →  ")
            val start = text.length
            text.append(Order.shortLabel(step))
            if (i == current) {
                text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(color), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }
}
