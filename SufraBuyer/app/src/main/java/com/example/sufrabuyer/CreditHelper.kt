package com.example.sufrabuyer

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Delivery credit (Milestone 4 bonus).
 *
 * Volunteers earn points in the Sufra Delivery app and convert them to credit (EGP), saved in
 *     users/{uid}/credit
 * The buyer can spend it at checkout. If the order is then rejected or cancelled, the credit
 * is given back once (the order is marked creditRefunded = true, inside a transaction, so it
 * can never be refunded twice).
 */
object CreditHelper {

    fun creditRef(uid: String): DatabaseReference = FirebaseRefs.userRef(uid).child("credit")

    /** Takes [amount] from the buyer's credit. Returns false if there isn't enough. */
    suspend fun take(uid: String, amount: Double): Boolean = suspendCancellableCoroutine { continuation ->
        var took = false
        creditRef(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                took = false
                val value = (current.value as? Number)?.toDouble() ?: return Transaction.success(current)
                if (value + 0.001 < amount) return Transaction.abort()        // not enough credit
                current.value = Math.round((value - amount) * 100) / 100.0
                took = true
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (continuation.isActive) continuation.resume(error == null && committed && took)
            }
        })
    }

    /** Adds [amount] back to the buyer's credit. */
    fun giveBack(uid: String, amount: Double) {
        if (amount <= 0.0) return
        creditRef(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val value = (current.value as? Number)?.toDouble() ?: 0.0
                current.value = Math.round((value + amount) * 100) / 100.0
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    /** If [order] used credit and was rejected or cancelled, give the credit back (only once). */
    fun refundIfNeeded(order: Order) {
        val ended = order.status == Order.STATUS_REJECTED || order.status == Order.STATUS_CANCELLED
        if (!ended || order.creditUsed <= 0.0 || order.creditRefunded) return
        FirebaseRefs.ordersRef().child(order.id).child("creditRefunded")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    if (current.value == true) return Transaction.abort()   // already refunded
                    current.value = true
                    return Transaction.success(current)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error == null && committed) giveBack(order.buyerId, order.creditUsed)
                }
            })
    }
}
