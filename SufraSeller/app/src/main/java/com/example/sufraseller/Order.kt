package com.example.sufraseller

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
 * An order placed by a buyer (Milestone 3).
 *
 * Saved in Firebase under   orders/{orderId}
 * The buyer creates it, the seller of that shop can read it.
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
    var totalPrice: Double = 0.0,
    var status: String = STATUS_PENDING,
    var createdAt: Long = 0L
) {

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
        "totalPrice" to totalPrice,
        "status" to status,
        "createdAt" to ServerValue.TIMESTAMP
    )

    companion object {
        // Order states (accept / deliver are Milestone 4)
        const val STATUS_PENDING = "pending"

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
                totalPrice = number(snapshot, "totalPrice"),
                status = text(snapshot, "status").ifEmpty { STATUS_PENDING },
                createdAt = number(snapshot, "createdAt").toLong()
            )
        }
    }
}
