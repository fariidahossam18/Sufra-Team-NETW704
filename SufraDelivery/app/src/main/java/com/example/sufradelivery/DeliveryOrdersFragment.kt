package com.example.sufradelivery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufradelivery.databinding.FragmentDeliveryListBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * A list of delivery jobs (Delivery app, Milestone 4 bonus). Used for two tabs:
 *
 *   AVAILABLE: orders that need delivery, already accepted by the shop (Preparing) and not taken
 *   MINE:      orders this volunteer took (active ones first, then the delivered ones)
 *
 * Firebase query: orders ordered by "deliveryMethod" equal to "delivery" (only delivery orders,
 * pick up orders never appear here) + a ValueEventListener, so the list is always live.
 */
class DeliveryOrdersFragment : Fragment() {

    companion object {
        const val MODE_AVAILABLE = "available"
        const val MODE_MINE = "mine"
        private const val ARG_MODE = "mode"

        fun newInstance(mode: String) = DeliveryOrdersFragment().apply { arguments = bundleOf(ARG_MODE to mode) }
    }

    private var _binding: FragmentDeliveryListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: DeliveryOrderAdapter
    private var query: Query? = null
    private var uid = ""
    private var myName = ""
    private val shopAddresses = mutableMapOf<String, String>()   // sellerId -> address

    private val mode: String get() = requireArguments().getString(ARG_MODE) ?: MODE_AVAILABLE

    private val ordersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val all = snapshot.children.mapNotNull { Order.fromSnapshot(it) }
            val shown = if (mode == MODE_AVAILABLE) {
                all.filter { it.status == Order.STATUS_PREPARING && !it.hasDriver }
                    .sortedBy { it.createdAt }                                   // oldest waits longest
            } else {
                all.filter { it.driverId == uid }
                    .sortedWith(compareBy<Order> { it.isFinished }.thenByDescending { it.createdAt })
            }
            adapter.submitList(shown)
            b.progress.visibility = View.GONE
            b.emptyState.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            b.tvSummary.text = if (mode == MODE_AVAILABLE) "${shown.size} orders waiting for a volunteer"
            else "${shown.count { !it.isFinished }} active · ${shown.count { it.status == Order.STATUS_DELIVERED }} delivered"
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load orders: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val user = FirebaseRefs.auth.currentUser ?: return
        uid = user.uid
        myName = user.displayName?.ifBlank { null } ?: user.email?.substringBefore('@') ?: "Volunteer"

        binding.tvEmptyEmoji.text = if (mode == MODE_AVAILABLE) "🛵" else "📦"
        binding.tvEmpty.text = if (mode == MODE_AVAILABLE)
            "No orders need a driver right now.\nOrders appear here when a shop accepts a delivery order."
        else "You haven't taken any orders yet.\nTake one from the Available tab."

        adapter = DeliveryOrderAdapter(uid, { sellerId -> shopAddresses[sellerId].orEmpty() }) { order -> onAction(order) }
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter

        loadShopAddresses()
        query = FirebaseRefs.ordersRef().orderByChild("deliveryMethod").equalTo(Order.METHOD_DELIVERY)
            .also { it.addValueEventListener(ordersListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        query?.removeEventListener(ordersListener)
        _binding = null
    }

    /** Reads every shop once, to show the pick-up address on each card. */
    private fun loadShopAddresses() {
        FirebaseRefs.shopsRef().get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { shop ->
                shopAddresses[shop.key.orEmpty()] = shop.child("address").value?.toString().orEmpty()
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun onAction(order: Order) {
        when {
            order.driverId != uid -> confirmTake(order)
            order.status == Order.STATUS_PREPARING -> DeliveryRepository.pickedUp(order) { error ->
                toast(error?.let { "Couldn't update: $it" } ?: "Out for delivery 🛵 The buyer was notified")
            }
            order.status == Order.STATUS_DELIVERING -> confirmDelivered(order)
        }
    }

    /** Alert dialog before taking a job. */
    private fun confirmTake(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Take order ${shortOrderId(order.id)}?")
            .setMessage(
                "Pick it up from ${order.shopName} and deliver it to ${order.address}.\n\n" +
                        "Collect ${formatPrice(order.totalPrice)} from ${order.buyerName}. " +
                        "You'll earn ${DeliveryRepository.pointsFor(order)} points."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("Take it") { _, _ ->
                DeliveryRepository.take(order, uid, myName) { error ->
                    toast(error ?: "Order taken. Find it in My deliveries 📦")
                    if (error == null) (activity as? DeliveryHomeActivity)?.openTab(DeliveryHomeActivity.TAB_MINE)
                }
            }
            .show()
    }

    private fun confirmDelivered(order: Order) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delivered?")
            .setMessage("Confirm that ${order.buyerName} received order ${shortOrderId(order.id)}.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Yes, delivered") { _, _ ->
                DeliveryRepository.delivered(order, uid) { error ->
                    toast(error?.let { "Couldn't update: $it" } ?: "Great job! +${DeliveryRepository.pointsFor(order)} points ⭐")
                }
            }
            .show()
    }
}
