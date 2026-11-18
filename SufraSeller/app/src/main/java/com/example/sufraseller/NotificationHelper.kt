package com.example.sufraseller

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

/**
 * System notifications of the Seller app (Tutorial 5).
 *
 *  1. Channel:     "New orders" (IMPORTANCE_HIGH -> sound + pop-up). Required on Android 8+.
 *  2. Permission:  POST_NOTIFICATIONS is asked at runtime on Android 13+ (SellerHomeActivity).
 *  3. Builder:     NotificationCompat.Builder with icon, title, text and InboxStyle (one line per item).
 *  4. PendingIntent: tapping it (or the "View orders" action button) opens the dashboard
 *                  on the Orders tab.
 */
object NotificationHelper {

    const val CHANNEL_ORDERS = "new_orders"

    /** Creates the channel (safe to call many times; Android ignores duplicates). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ORDERS,
                "New orders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a buyer places an order at your shop"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** True if the app is allowed to post notifications. */
    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Shows "New order #XXXX" with the items, the buyer and the total. */
    fun showNewOrder(context: Context, order: Order) {
        if (!canNotify(context)) return

        // What happens on tap: open the dashboard on the Orders tab
        val openOrders = Intent(context, SellerHomeActivity::class.java)
            .putExtra(SellerHomeActivity.EXTRA_OPEN_TAB, SellerHomeActivity.TAB_ORDERS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            order.id.hashCode(),
            openOrders,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Expandable style: one line per item
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("New order ${shortOrderId(order.id)}")
            .setSummaryText("Total ${formatPrice(order.totalPrice)}")
        order.items.take(5).forEach { style.addLine("${it.quantity} × ${it.name}") }

        val notification = NotificationCompat.Builder(context, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_orders)
            .setContentTitle("New order ${shortOrderId(order.id)}")
            .setContentText("${order.buyerName} · ${order.itemCount} items · ${formatPrice(order.totalPrice)}")
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)                                         // closes when tapped
            .addAction(R.drawable.ic_orders, "View orders", pendingIntent) // action button
            .build()

        try {
            // The id makes each order its own notification (a second order doesn't replace the first)
            NotificationManagerCompat.from(context).notify(order.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission was removed in the meantime: nothing to show
        }
    }
}
