package com.example.sufraseller

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction

/**
 * Moves an order through its states (Seller app, Milestone 4).
 *
 *   waiting -> preparing -> delivering / ready -> delivered
 *   waiting -> rejected   (the reserved stock goes back to the shop)
 *
 * Only the seller of the order can do this (security rules), and the rules check that the
 * new status is one of the allowed values.
 */
object OrderStatusUpdater {

    /** Moves [order] to its next state. Calls [onDone] with null, or an error message. */
    fun moveToNext(order: Order, onDone: (String?) -> Unit) {
        val next = order.nextStatus() ?: return onDone("This order is already finished")
        setStatus(order, next, onDone)
    }

    /** Rejects a waiting order and gives its products back to the stock. */
    fun reject(order: Order, onDone: (String?) -> Unit) {
        if (order.status != Order.STATUS_WAITING) return onDone("Only waiting orders can be rejected")
        setStatus(order, Order.STATUS_REJECTED) { error ->
            if (error == null) order.items.forEach { restock(order.sellerId, it) }
            onDone(error)
        }
    }

    private fun setStatus(order: Order, status: String, onDone: (String?) -> Unit) {
        val updates = mapOf<String, Any>(
            "status" to status,
            "statusUpdatedAt" to ServerValue.TIMESTAMP
        )
        FirebaseRefs.ordersRef().child(order.id).updateChildren(updates)
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { e -> onDone(e.message ?: "Unknown error") }
    }

    /** quantity + ordered, in a transaction (safe even if buyers order at the same time). */
    private fun restock(sellerId: String, item: OrderItem) {
        FirebaseRefs.productRef(sellerId, item.productId).child("quantity")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    val value = (current.value as? Number)?.toInt() ?: return Transaction.success(current)
                    current.value = (value + item.quantity).coerceAtMost(Product.QUANTITY_MAX)
                    return Transaction.success(current)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
            })
    }
}
