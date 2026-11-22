package com.example.sufradelivery

import androidx.annotation.ColorRes
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

/**
 * One line of an order: which product, how many and at what price (Milestone 3).
 */
data class OrderItem(
    var productId: String = "",
    var name: String = "",
    var price: Double = 0.0,
    var quantity: Int = 0,
    var imageUrl: String = ""
) {
    val subtotal: Double
        get() = price * quantity

    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "name" to name,
        "price" to price,
        "quantity" to quantity,
        "imageUrl" to imageUrl
    )
}

/**
 * An order placed by a buyer.
 *
 * Saved in Firebase under   orders/{orderId}
 * The buyer creates it, the seller of that shop moves it through its states (Milestone 4):
 *
 *   Delivery:  waiting -> preparing -> delivering -> delivered
 *   Pickup:    waiting -> preparing -> ready      -> delivered
 *   The seller can reject while waiting, the buyer can cancel while waiting.
 */
data class Order(
    var id: String = "",
    var buyerId: String = "",
    var buyerName: String = "",
    var buyerPhone: String = "",
    var address: String = "",
    var sellerId: String = "",
    var shopName: String = "",
    var items: List<OrderItem> = emptyList(),
    var itemCount: Int = 0,
    var subtotal: Double = 0.0,                 // Milestone 4: price of the items only
    var deliveryMethod: String = METHOD_DELIVERY, // Milestone 4: "delivery" or "pickup"
    var deliveryFee: Double = 0.0,              // Milestone 4: 0 for pickup
    var creditUsed: Double = 0.0,               // Milestone 4 bonus: delivery credit spent on this order
    var totalPrice: Double = 0.0,               // subtotal + deliveryFee - creditUsed
    var status: String = STATUS_WAITING,
    var createdAt: Long = 0L,
    var statusUpdatedAt: Long = 0L,             // Milestone 4: last status change
    var driverId: String = "",                  // Milestone 4 bonus: volunteer driver (Delivery app)
    var driverName: String = "",
    var creditRefunded: Boolean = false         // credit given back after reject / cancel
) {

    /** True when a volunteer driver took this order in the Delivery app. */
    val hasDriver: Boolean
        get() = driverId.isNotEmpty()

    val isDelivery: Boolean
        get() = deliveryMethod != METHOD_PICKUP

    /** Delivered, rejected or cancelled: nothing more can happen to it. */
    val isFinished: Boolean
        get() = status == STATUS_DELIVERED || status == STATUS_REJECTED || status == STATUS_CANCELLED

    /** The next state the SELLER can move this order to, or null if there is none. */
    fun nextStatus(): String? = when (status) {
        STATUS_WAITING -> STATUS_PREPARING
        STATUS_PREPARING -> if (isDelivery) STATUS_DELIVERING else STATUS_READY
        STATUS_READY, STATUS_DELIVERING -> STATUS_DELIVERED
        else -> null
    }

    /** The steps of this order's journey (used to draw the progress). */
    fun steps(): List<String> = if (isDelivery) FLOW_DELIVERY else FLOW_PICKUP

    /** The JSON we save in Firebase. Items are saved as a map: productId -> item. */
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "buyerId" to buyerId,
        "buyerName" to buyerName,
        "buyerPhone" to buyerPhone,
        "address" to address,
        "sellerId" to sellerId,
        "shopName" to shopName,
        "items" to items.associate { it.productId to it.toMap() },
        "itemCount" to itemCount,
        "subtotal" to subtotal,
        "deliveryMethod" to deliveryMethod,
        "deliveryFee" to deliveryFee,
        "creditUsed" to creditUsed,
        "totalPrice" to totalPrice,
        "status" to status,
        "createdAt" to ServerValue.TIMESTAMP,
        "statusUpdatedAt" to ServerValue.TIMESTAMP
    )

    companion object {
        // ---- Order states (Milestone 4) ----
        const val STATUS_WAITING = "waiting"        // Waiting for seller approval
        const val STATUS_PREPARING = "preparing"
        const val STATUS_READY = "ready"            // Ready for pickup
        const val STATUS_DELIVERING = "delivering"  // Out for delivery
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_REJECTED = "rejected"      // by the seller
        const val STATUS_CANCELLED = "cancelled"    // by the buyer

        /** Old name from Milestone 3 (orders saved then have "pending"). */
        const val STATUS_PENDING = STATUS_WAITING

        // ---- Delivery options (Milestone 4) ----
        const val METHOD_DELIVERY = "delivery"
        const val METHOD_PICKUP = "pickup"

        val FLOW_DELIVERY = listOf(STATUS_WAITING, STATUS_PREPARING, STATUS_DELIVERING, STATUS_DELIVERED)
        val FLOW_PICKUP = listOf(STATUS_WAITING, STATUS_PREPARING, STATUS_READY, STATUS_DELIVERED)

        /** Text shown to people for each state. */
        fun statusLabel(status: String): String = when (status) {
            STATUS_WAITING -> "Waiting for seller approval"
            STATUS_PREPARING -> "Preparing"
            STATUS_READY -> "Ready for pickup"
            STATUS_DELIVERING -> "Out for delivery"
            STATUS_DELIVERED -> "Delivered"
            STATUS_REJECTED -> "Rejected by the shop"
            STATUS_CANCELLED -> "Cancelled"
            else -> status
        }

        /** Short label for small chips and step names. */
        fun shortLabel(status: String): String = when (status) {
            STATUS_WAITING -> "Waiting"
            STATUS_PREPARING -> "Preparing"
            STATUS_READY -> "Ready"
            STATUS_DELIVERING -> "On the way"
            STATUS_DELIVERED -> "Delivered"
            STATUS_REJECTED -> "Rejected"
            STATUS_CANCELLED -> "Cancelled"
            else -> status
        }

        fun statusEmoji(status: String): String = when (status) {
            STATUS_WAITING -> "⏳"
            STATUS_PREPARING -> "👩‍🍳"
            STATUS_READY -> "🛍️"
            STATUS_DELIVERING -> "🛵"
            STATUS_DELIVERED -> "✅"
            STATUS_REJECTED -> "❌"
            STATUS_CANCELLED -> "🚫"
            else -> "•"
        }

        /** Background colour of the status chip. */
        @ColorRes
        fun statusColor(status: String): Int = when (status) {
            STATUS_WAITING -> R.color.tile_yellow
            STATUS_PREPARING -> R.color.tile_blue
            STATUS_READY, STATUS_DELIVERING -> R.color.tile_purple
            STATUS_DELIVERED -> R.color.tile_green
            else -> R.color.tile_red
        }

        fun fromSnapshot(snapshot: DataSnapshot): Order? {
            if (!snapshot.exists()) return null
            fun text(s: DataSnapshot, key: String) = s.child(key).value?.toString().orEmpty()
            fun number(s: DataSnapshot, key: String): Double = when (val v = s.child(key).value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val items = snapshot.child("items").children.map { item ->
                OrderItem(
                    productId = item.key.orEmpty(),
                    name = text(item, "name"),
                    price = number(item, "price"),
                    quantity = number(item, "quantity").toInt(),
                    imageUrl = text(item, "imageUrl")
                )
            }
            val total = number(snapshot, "totalPrice")
            val fee = number(snapshot, "deliveryFee")
            // Milestone 3 orders were saved as "pending" and had no subtotal/method
            val savedStatus = text(snapshot, "status")
            return Order(
                id = snapshot.key.orEmpty(),
                buyerId = text(snapshot, "buyerId"),
                buyerName = text(snapshot, "buyerName"),
                buyerPhone = text(snapshot, "buyerPhone"),
                address = text(snapshot, "address"),
                sellerId = text(snapshot, "sellerId"),
                shopName = text(snapshot, "shopName"),
                items = items,
                itemCount = number(snapshot, "itemCount").toInt(),
                subtotal = if (snapshot.hasChild("subtotal")) number(snapshot, "subtotal")
                else total - fee,
                deliveryMethod = text(snapshot, "deliveryMethod").ifEmpty { METHOD_DELIVERY },
                deliveryFee = fee,
                creditUsed = number(snapshot, "creditUsed"),
                totalPrice = total,
                status = if (savedStatus.isEmpty() || savedStatus == "pending") STATUS_WAITING else savedStatus,
                createdAt = number(snapshot, "createdAt").toLong(),
                statusUpdatedAt = number(snapshot, "statusUpdatedAt").toLong(),
                driverId = text(snapshot, "driverId"),
                driverName = text(snapshot, "driverName"),
                creditRefunded = snapshot.child("creditRefunded").value == true
            )
        }
    }
}
