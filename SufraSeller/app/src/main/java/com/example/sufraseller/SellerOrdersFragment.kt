package com.example.sufraseller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufraseller.databinding.FragmentSellerOrdersBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Orders tab (Seller app, Milestone 3).
 *
 * Shows the orders that buyers placed at THIS shop, newest first. It uses a Firebase query:
 *     orders  ordered by "sellerId"  equal to  my uid
 * with a ValueEventListener, so a new order appears here the moment the buyer places it.
 * (Accepting / delivering orders is Milestone 4.)
 */
class SellerOrdersFragment : Fragment() {

    private var _binding: FragmentSellerOrdersBinding? = null
    private val binding get() = _binding!!

    private val adapter = SellerOrderAdapter()
    private var ordersQuery: Query? = null

    private val ordersListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val orders = snapshot.children
                .mapNotNull { Order.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }          // newest first
            adapter.submitList(orders)

            b.progress.visibility = View.GONE
            b.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            val total = orders.sumOf { it.totalPrice }
            b.tvSummary.text = if (orders.isEmpty()) ""
            else "${orders.size} orders · ${formatPrice(total)} in total"
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
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter

        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        ordersQuery = FirebaseRefs.ordersRef().orderByChild("sellerId").equalTo(uid)
            .also { it.addValueEventListener(ordersListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersQuery?.removeEventListener(ordersListener)
        _binding = null
    }
}
