package com.example.sufrabuyer

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Places an order safely (Milestone 4: quantity check + inventory update).
 *
 * For every product in the order we run a Firebase TRANSACTION on
 *     products/{sellerId}/{productId}/quantity
 * A transaction reads the value ON THE SERVER, checks it and writes the new value in one step.
 * If two buyers order the last piece at the same time, Firebase runs one transaction first and
 * re-runs the other with the new value, which then fails the check. So nobody can buy more
 * than the stock, and the stock never goes below 0 (the security rules also refuse < 0).
 *
 *   1) take the stock of every item (quantity - ordered); if one is not enough -> stop
 *   2) if something failed, give back what we already took (rollback)
 *   3) bonus: take the delivery credit the buyer chose to use (also a transaction)
 *   4) save the order; if saving fails, give the stock (and credit) back too
 */
object OrderPlacer {

    sealed class Result {
        object Success : Result()
        data class OutOfStock(val productName: String) : Result()
        data class Failed(val message: String) : Result()
    }

    /** Runs on its own scope, so the order finishes even if the user leaves the screen. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun place(orderRef: DatabaseReference, order: Order, onResult: (Result) -> Unit) {
        scope.launch {
            val taken = mutableListOf<OrderItem>()

            // 1) take the stock, one product at a time
            for (item in order.items) {
                val quantityRef = FirebaseRefs.productRef(order.sellerId, item.productId).child("quantity")
                val ok = changeQuantity(quantityRef, -item.quantity)
                if (!ok) {
                    giveBack(order.sellerId, taken)            // 2) rollback
                    onResult(Result.OutOfStock(item.name))
                    return@launch
                }
                taken.add(item)
            }

            // 3) delivery credit (Milestone 4 bonus)
            if (order.creditUsed > 0.0 && !CreditHelper.take(order.buyerId, order.creditUsed)) {
                giveBack(order.sellerId, taken)
                onResult(Result.Failed("You don't have enough credit any more"))
                return@launch
            }

            // 4) save the order
            val error = setValue(orderRef, order.toMap())
            if (error != null) {
                giveBack(order.sellerId, taken)
                CreditHelper.giveBack(order.buyerId, order.creditUsed)
                onResult(Result.Failed(error))
            } else {
                onResult(Result.Success)
            }
        }
    }

    /** Adds the ordered quantities back (used for rollback; the seller uses the same idea on reject). */
    private suspend fun giveBack(sellerId: String, items: List<OrderItem>) {
        for (item in items) {
            changeQuantity(FirebaseRefs.productRef(sellerId, item.productId).child("quantity"), item.quantity)
        }
    }

    /**
     * Adds [change] to the number at [ref] inside a transaction.
     * Returns false if the product doesn't exist or the result would be below 0.
     */
    private suspend fun changeQuantity(ref: DatabaseReference, change: Int): Boolean =
        suspendCancellableCoroutine { continuation ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    // The first run can see "null" before the real server value arrives;
                    // returning success here makes Firebase run it again with the real value.
                    val value = (current.value as? Number)?.toInt() ?: return Transaction.success(current)
                    val newValue = value + change
                    if (newValue < 0) return Transaction.abort()       // not enough stock
                    current.value = newValue
                    return Transaction.success(current)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    val stillExists = snapshot?.value != null
                    if (continuation.isActive) continuation.resume(error == null && committed && stillExists)
                }
            })
        }

    /** setValue() as a suspend function: returns null on success or the error message. */
    private suspend fun setValue(ref: DatabaseReference, value: Any): String? =
        suspendCancellableCoroutine { continuation ->
            ref.setValue(value)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(null) }
                .addOnFailureListener { e -> if (continuation.isActive) continuation.resume(e.message ?: "Unknown error") }
        }
}
