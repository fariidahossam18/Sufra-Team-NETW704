package com.example.sufrabuyer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Order status notifications for the BUYER (Milestone 4).
 *
 * Listens to the buyer's orders (query on buyerId) while the app is running, also in the
 * background. It remembers the last status of every order; when the seller changes it
 * (Preparing, Out for delivery, Ready for pickup, Delivered, Rejected) a notification is shown.
 *
 *  - Channel "Order updates" (IMPORTANCE_HIGH)
 *  - BigTextStyle with the full explanation
 *  - Tapping it opens the home screen on the Orders tab (PendingIntent + EXTRA_OPEN_TAB)
 */
class BuyerOrderNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "order_updates"
    }

    private var query: Query? = null
    private val lastStatus = mutableMapOf<String, String>()   // orderId -> status we already know
    private var firstLoadDone = false

    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            snapshot.children.mapNotNull { Order.fromSnapshot(it) }.forEach { order ->
                val before = lastStatus[order.id]
                lastStatus[order.id] = order.status
                // Notify only real changes made after the app opened (not old orders, not new ones we placed)
                if (firstLoadDone && before != null && before != order.status &&
                    order.status != Order.STATUS_CANCELLED       // the buyer cancelled it himself
                ) {
                    showStatusNotification(order)
                }
                // bonus: give back the delivery credit of a rejected order (only once)
                CreditHelper.refundIfNeeded(order)
            }
            firstLoadDone = true
        }

        override fun onCancelled(error: DatabaseError) {}
    }

    /** Starts listening to the orders of [buyerId]. */
    fun start(buyerId: String) {
        createChannel()
        stop()
        firstLoadDone = false
        query = FirebaseRefs.ordersRef().orderByChild("buyerId").equalTo(buyerId).also {
            it.addValueEventListener(listener)
        }
    }

    fun stop() {
        query?.removeEventListener(listener)
        query = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Order updates", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when your order is accepted, ready, on the way or delivered" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun canNotify(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The sentence shown for each new status. */
    private fun messageFor(order: Order): String = when (order.status) {
        Order.STATUS_PREPARING -> "${order.shopName} accepted your order and is preparing it."
        Order.STATUS_DELIVERING -> "Your order is on the way to ${order.address}."
        Order.STATUS_READY -> "Your order is ready. You can pick it up from ${order.shopName} now."
        Order.STATUS_DELIVERED -> "Your order was delivered. Enjoy! 😋"
        Order.STATUS_REJECTED -> "Sorry, ${order.shopName} couldn't accept your order. Nothing was charged."
        else -> Order.statusLabel(order.status)
    }

    private fun showStatusNotification(order: Order) {
        if (!canNotify()) return

        val open = Intent(context, BuyerHomeActivity::class.java)
            .putExtra(BuyerHomeActivity.EXTRA_OPEN_TAB, BuyerHomeActivity.TAB_ORDERS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context, order.id.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "${Order.statusEmoji(order.status)} Order ${shortOrderId(order.id)}: ${Order.shortLabel(order.status)}"
        val text = messageFor(order)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_orders)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n${order.itemCount} items · ${formatPrice(order.totalPrice)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            // same id for the same order: a new status replaces the old notification of that order
            NotificationManagerCompat.from(context).notify(order.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission removed: nothing to show
        }
    }
}
