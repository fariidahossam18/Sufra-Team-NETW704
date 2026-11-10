package com.example.sufraseller

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.sufraseller.databinding.FragmentSellerProductsBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * My products tab (Seller app, Milestone 3).
 *
 *  - Reads products/{myUid} with a ValueEventListener (updates by itself when something changes)
 *  - Shows them in a grid RecyclerView (2 columns) with photo, name, price and stock
 *  - The  -  /  +  buttons change the stock instantly in Firebase (buyers see it at once)
 *  - Tapping a product opens EditProductActivity (the product id is sent with putExtra)
 */
class SellerProductsFragment : Fragment() {

    private var _binding: FragmentSellerProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SellerProductAdapter
    private var productsRef: DatabaseReference? = null
    private var uid = ""

    private val productsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val products = snapshot.children
                .mapNotNull { Product.fromSnapshot(it) }
                .sortedByDescending { it.createdAt }          // newest first
            adapter.submitList(products)
            b.progress.visibility = View.GONE
            b.emptyState.visibility = if (products.isEmpty()) View.VISIBLE else View.GONE
            b.tvHint.visibility = if (products.isEmpty()) View.GONE else View.VISIBLE
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load your products: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSellerProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        uid = FirebaseRefs.auth.currentUser?.uid ?: return

        adapter = SellerProductAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onClick = { product -> openEditor(product) },
            onChangeQuantity = { product, change -> changeQuantity(product, change) }
        )
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        binding.btnAddFirst.setOnClickListener {
            (activity as? SellerHomeActivity)?.openTab(SellerHomeActivity.TAB_ADD)
        }

        productsRef = FirebaseRefs.sellerProductsRef(uid).also { it.addValueEventListener(productsListener) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsRef?.removeEventListener(productsListener)
        _binding = null
    }

    /** Opens the edit screen. The product id is sent with putExtra. */
    private fun openEditor(product: Product) {
        val intent = Intent(requireContext(), EditProductActivity::class.java)
        intent.putExtra(EditProductActivity.EXTRA_PRODUCT_ID, product.id)
        startActivity(intent)
    }

    /** Quick stock change from the card: saves only the quantity (updateChildren). */
    private fun changeQuantity(product: Product, change: Int) {
        val newQuantity = (product.quantity + change).coerceIn(0, Product.QUANTITY_MAX)
        if (newQuantity == product.quantity) return
        val updates = mapOf<String, Any>(
            "quantity" to newQuantity,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        FirebaseRefs.productRef(uid, product.id).updateChildren(updates)
            .addOnFailureListener { e -> toast("Couldn't update the stock: ${e.message}") }
    }
}
