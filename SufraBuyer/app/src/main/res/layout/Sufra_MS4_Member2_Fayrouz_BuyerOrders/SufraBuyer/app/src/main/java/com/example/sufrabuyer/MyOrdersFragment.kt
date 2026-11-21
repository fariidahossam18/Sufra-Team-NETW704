package com.example.sufrabuyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufrabuyer.databinding.FragmentMyOrdersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Orders tab (Buyer app, Milestone 4): order tracking.
 *
 *  - Query: orders ordered by "buyerId" equal to my uid, with a ValueEventListener,
 *    so when the seller changes a status the card updates instantly.
 *  - Newest first; active orders are counted in the summary line.
 *  - Cancel: only while the order is "Waiting for seller approval". The status becomes
 *    "cancelled", the reserved stock is given back to the shop and any delivery credit used
 *    is given back to the buyer.
 */
class MyOrdersFragment : Fragment() {

    private var _binding: FragmentMyOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BuyerOrderAdapter
    private var ordersQuery: Query? = null

    private val ordersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val orders = snapshot.children
                .mapNotNull { Order.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }          // newest first
            adapter.submitList(orders)
            orders.forEach { CreditHelper.refundIfNeeded(it) }   // bonus: credit back for rejected/cancelled

            b.progress.visibility = View.GONE
            b.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            val active = orders.count { !it.isFinished }
            b.tvSummary.text = if (orders.isEmpty()) "" else "${orders.size} orders · $active in progress"
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load your orders: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = BuyerOrderAdapter(onCancel = { order -> confirmCancel(order) })
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter

        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        ordersQuery = FirebaseRefs.ordersRef().orderByChild("buyerId").equalTo(uid)
            .also { it.addValueEventListener(ordersListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersQuery?.removeEventListener(ordersListener)
        _binding = null
    }

    /** Alert dialog before cancelling (Milestone 4: alert for the buyer). */
    private fun confirmCancel(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cancel order ${shortOrderId(order.id)}?")
            .setMessage("${order.shopName} hasn't accepted it yet, so you can still cancel. The products go back to the shop.")
            .setNegativeButton("Keep order", null)
            .setPositiveButton("Cancel order") { _, _ -> cancelOrder(order) }
            .show()
    }

    /** status -> cancelled, then the stock is given back (each item: quantity + ordered, in a transaction). */
    private fun cancelOrder(order: Order) {
        val updates = mapOf<String, Any>(
            "status" to Order.STATUS_CANCELLED,
            "statusUpdatedAt" to ServerValue.TIMESTAMP
        )
        FirebaseRefs.ordersRef().child(order.id).updateChildren(updates)
            .addOnSuccessListener {
                order.items.forEach { item ->
                    StockHelper.addBack(order.sellerId, item.productId, item.quantity)
                }
                CreditHelper.refundIfNeeded(order.copy(status = Order.STATUS_CANCELLED))
                toast("Order ${shortOrderId(order.id)} cancelled")
            }
            .addOnFailureListener { e ->
                // e.g. the seller accepted it a second ago (the rules only allow cancelling while waiting)
                toast("Couldn't cancel: ${e.message}")
            }
    }
}
