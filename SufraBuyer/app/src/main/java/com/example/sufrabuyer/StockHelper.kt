package com.example.sufrabuyer

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * Gives stock back to a shop when the buyer cancels an order (Milestone 4: inventory update).
 * Uses a transaction, so it adds to the real value on the server even if others are ordering
 * the same product at the same moment.
 */
object StockHelper {

    fun addBack(sellerId: String, productId: String, amount: Int) {
        FirebaseRefs.productRef(sellerId, productId).child("quantity")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    val value = (current.value as? Number)?.toInt() ?: return Transaction.success(current)
                    current.value = (value + amount).coerceAtMost(Product.QUANTITY_MAX)
                    return Transaction.success(current)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    // nothing to do: if the product was deleted there is nothing to give back
                }
            })
    }
}
