package com.example.sufraseller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufraseller.databinding.FragmentSellerOrdersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Orders tab (Seller app, Milestones 3 and 4).
 *
 *  - Firebase query: orders ordered by "sellerId" equal to my uid, with a ValueEventListener,
 *    so a new order (or a buyer cancelling) appears instantly.
 *  - Milestone 4: "To do" shows only orders that still need work, "All orders" shows everything.
 *  - Milestone 4: buttons move each order through its states (OrderStatusUpdater).
 *    The buyer gets a notification for every change.
 */
class SellerOrdersFragment : Fragment() {

    private var _binding: FragmentSellerOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SellerOrderAdapter
    private var ordersQuery: Query? = null
    private val allOrders = mutableListOf<Order>()

    private val ordersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            allOrders.clear()
            allOrders.addAll(
                snapshot.children
                    .mapNotNull { Order.fromSnapshot(it) }
                    .sortedByDescending { it.createdAt }          // newest first
            )
            b.progress.visibility = View.GONE
            showOrders()
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load orders: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSellerOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SellerOrderAdapter(
            onNext = { order -> moveToNext(order) },
            onReject = { order -> confirmReject(order) }
        )
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter
        binding.chipFilter.setOnCheckedStateChangeListener { _, _ -> showOrders() }

        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        ordersQuery = FirebaseRefs.ordersRef().orderByChild("sellerId").equalTo(uid)
            .also { it.addValueEventListener(ordersListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersQuery?.removeEventListener(ordersListener)
        _binding = null
    }

    /** Applies the "To do / All orders" filter and updates the summary line. */
    private fun showOrders() {
        val b = _binding ?: return
        val onlyActive = b.chipActive.isChecked
        val shown = if (onlyActive) allOrders.filter { !it.isFinished } else allOrders
        adapter.submitList(shown)

        val waiting = allOrders.count { it.status == Order.STATUS_WAITING }
        val earned = allOrders.filter { it.status == Order.STATUS_DELIVERED }.sumOf { it.totalPrice }
        b.tvSummary.text = "$waiting waiting for approval · ${formatPrice(earned)} delivered"
        b.emptyState.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        b.tvEmpty.text = if (onlyActive && allOrders.isNotEmpty()) "All caught up! No orders need your attention."
        else "New orders from buyers will appear here instantly"
    }

    private fun moveToNext(order: Order) {
        OrderStatusUpdater.moveToNext(order) { error ->
            if (error == null) {
                toast("Order ${shortOrderId(order.id)}: ${Order.statusLabel(order.nextStatus() ?: order.status)}")
            } else {
                toast("Couldn't update the order: $error")
            }
        }
    }

    /** Alert dialog before rejecting (Milestone 4: alert for the seller). */
    private fun confirmReject(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reject order ${shortOrderId(order.id)}?")
            .setMessage(
                "${order.buyerName} will be told that you couldn't accept it, " +
                        "and the ${order.itemCount} items go back to your stock."
            )
            .setNegativeButton("Keep", null)
            .setPositiveButton("Reject") { _, _ ->
                OrderStatusUpdater.reject(order) { error ->
                    toast(if (error == null) "Order rejected, stock restored" else "Couldn't reject: $error")
                }
            }
            .show()
    }
}
