package com.example.sufradelivery

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sufradelivery.databinding.ItemDeliveryOrderBinding

/**
 * Adapter for delivery jobs (Delivery app, Milestone 4 bonus).
 * Card: order number + status, From (shop + address), To (buyer address), buyer + phone,
 * money to collect, points to earn, and ONE action button that depends on the state:
 *   Available            -> "Take this order"
 *   Mine, Preparing      -> "I picked it up"
 *   Mine, Out for delivery -> "Delivered to the buyer"
 *   Mine, Delivered      -> disabled "Delivered · +N points"
 */
class DeliveryOrderAdapter(
    private val myUid: String,
    private val shopAddress: (String) -> String,
    private val onAction: (Order) -> Unit
) : RecyclerView.Adapter<DeliveryOrderAdapter.ViewHolder>() {

    private val orders = mutableListOf<Order>()

    class ViewHolder(val binding: ItemDeliveryOrderBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Order>) {
        orders.clear()
        orders.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemDeliveryOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = orders.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        val b = holder.binding
        val context = b.root.context

        b.tvOrderId.text = "Order ${shortOrderId(order.id)}"
        b.tvStatus.text = "${Order.statusEmoji(order.status)} ${Order.shortLabel(order.status)}"
        b.tvStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(context, Order.statusColor(order.status)))

        val from = shopAddress(order.sellerId)
        b.tvFrom.text = "🏪 From: ${order.shopName}" + if (from.isNotBlank()) " · $from" else ""
        b.tvTo.text = "🏠 To: ${order.address}"
        b.tvBuyer.text = "👤 ${order.buyerName}   📞 ${order.buyerPhone}"
        b.tvCollect.text = "${order.itemCount} items · collect ${formatPrice(order.totalPrice)}"
        b.tvReward.text = "+${DeliveryRepository.pointsFor(order)} ⭐"

        val mine = order.driverId == myUid
        b.btnAction.isEnabled = true
        b.btnAction.text = when {
            !mine -> "Take this order"
            order.status == Order.STATUS_PREPARING -> "I picked it up"
            order.status == Order.STATUS_DELIVERING -> "Delivered to the buyer"
            order.status == Order.STATUS_DELIVERED -> {
                b.btnAction.isEnabled = false
                "Delivered · +${DeliveryRepository.pointsFor(order)} points"
            }
            else -> {
                b.btnAction.isEnabled = false
                Order.statusLabel(order.status)
            }
        }
        b.btnAction.setOnClickListener { onAction(order) }
    }
}
