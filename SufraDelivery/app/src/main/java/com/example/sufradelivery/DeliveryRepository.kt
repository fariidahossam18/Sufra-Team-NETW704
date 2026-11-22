package com.example.sufradelivery

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction

/**
 * Everything the Delivery app writes to Firebase (Milestone 4 bonus).
 *
 *  - take():      a volunteer takes an order. A TRANSACTION on orders/{id}/driverId makes sure
 *                 two volunteers can never take the same order.
 *  - pickedUp():  status -> "delivering" (Out for delivery)
 *  - delivered(): status -> "delivered" and the volunteer gets points (only once per order)
 *  - convert():   100 points -> EGP 10 credit, usable at checkout in the Sufra (Buyer) app
 *
 * Points and credit are saved in the volunteer's own profile:
 *     users/{uid}/deliveryPoints, users/{uid}/deliveriesDone, users/{uid}/credit
 */
object DeliveryRepository {

    const val POINTS_PER_DELIVERY = 10
    const val MAX_PIECE_BONUS = 10
    const val POINTS_FOR_REWARD = 100
    const val REWARD_CREDIT = 10.0

    /** 10 points + 1 per piece (up to +10). */
    fun pointsFor(order: Order): Int = POINTS_PER_DELIVERY + minOf(order.itemCount, MAX_PIECE_BONUS)

    /** Takes [order] for the signed-in volunteer. [onDone] gets null on success or an error message. */
    fun take(order: Order, uid: String, driverName: String, onDone: (String?) -> Unit) {
        val ref = FirebaseRefs.ordersRef().child(order.id)
        ref.child("driverId").runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                val taken = current.value as? String
                if (!taken.isNullOrEmpty() && taken != uid) return Transaction.abort()   // someone was faster
                current.value = uid
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (error != null || !committed || snapshot?.value != uid) {
                    onDone(error?.message ?: "Another volunteer already took this order")
                    return
                }
                ref.updateChildren(mapOf<String, Any>("driverName" to driverName, "driverTakenAt" to ServerValue.TIMESTAMP))
                    .addOnSuccessListener { onDone(null) }
                    .addOnFailureListener { e -> onDone(e.message) }
            }
        })
    }

    /** The volunteer collected the order from the shop. */
    fun pickedUp(order: Order, onDone: (String?) -> Unit) = setStatus(order, Order.STATUS_DELIVERING, onDone)

    /** The volunteer handed the order to the buyer: status delivered + points. */
    fun delivered(order: Order, uid: String, onDone: (String?) -> Unit) {
        setStatus(order, Order.STATUS_DELIVERED) { error ->
            if (error == null) awardPoints(order, uid)
            onDone(error)
        }
    }

    private fun setStatus(order: Order, status: String, onDone: (String?) -> Unit) {
        FirebaseRefs.ordersRef().child(order.id)
            .updateChildren(mapOf<String, Any>("status" to status, "statusUpdatedAt" to ServerValue.TIMESTAMP))
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { e -> onDone(e.message ?: "Unknown error") }
    }

    /** Marks the order pointsAwarded (transaction, so only once), then adds the points to the profile. */
    private fun awardPoints(order: Order, uid: String) {
        FirebaseRefs.ordersRef().child(order.id).child("pointsAwarded")
            .runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    if (current.value == true) return Transaction.abort()
                    current.value = true
                    return Transaction.success(current)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (error != null || !committed) return
                    FirebaseRefs.userRef(uid).runTransaction(object : Transaction.Handler {
                        override fun doTransaction(current: MutableData): Transaction.Result {
                            if (current.value == null) return Transaction.success(current)
                            val points = (current.child("deliveryPoints").value as? Number)?.toLong() ?: 0L
                            val done = (current.child("deliveriesDone").value as? Number)?.toLong() ?: 0L
                            current.child("deliveryPoints").value = points + pointsFor(order)
                            current.child("deliveriesDone").value = done + 1
                            return Transaction.success(current)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                    })
                }
            })
    }

    /** 100 points -> EGP 10 credit. [onDone] gets null on success or an error message. */
    fun convert(uid: String, onDone: (String?) -> Unit) {
        var converted = false
        FirebaseRefs.userRef(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(current: MutableData): Transaction.Result {
                converted = false
                if (current.value == null) return Transaction.success(current)
                val points = (current.child("deliveryPoints").value as? Number)?.toLong() ?: 0L
                if (points < POINTS_FOR_REWARD) return Transaction.abort()
                val credit = (current.child("credit").value as? Number)?.toDouble() ?: 0.0
                current.child("deliveryPoints").value = points - POINTS_FOR_REWARD
                current.child("credit").value = credit + REWARD_CREDIT
                converted = true
                return Transaction.success(current)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onDone(
                    when {
                        error != null -> error.message
                        committed && converted -> null
                        else -> "You need $POINTS_FOR_REWARD points"
                    }
                )
            }
        })
    }
}
