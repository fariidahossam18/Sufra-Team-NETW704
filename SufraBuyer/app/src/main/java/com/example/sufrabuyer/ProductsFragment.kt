package com.example.sufrabuyer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.sufrabuyer.databinding.FragmentProductsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * Products of one shop (Buyer app, Milestone 3). Lives inside ShopActivity.
 *
 *  - Reads products/{shopId} from Firebase with a ValueEventListener, so when the seller
 *    adds or edits a product it appears here instantly.
 *  - Shows them in a grid RecyclerView (2 columns).
 *  - "Add to cart" and the  -  /  +  buttons change the cart through CartManager.
 */
class ProductsFragment : Fragment() {

    companion object {
        private const val ARG_SHOP_ID = "shop_id"
        private const val ARG_SHOP_NAME = "shop_name"

        /** Creates the fragment and gives it the shop (fragments get their data through arguments). */
        fun newInstance(shopId: String, shopName: String) = ProductsFragment().apply {
            arguments = bundleOf(ARG_SHOP_ID to shopId, ARG_SHOP_NAME to shopName)
        }
    }

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private var shopId = ""
    private var shopName = ""
    private var productsRef: DatabaseReference? = null
    private val allProducts = mutableListOf<Product>()
    private lateinit var adapter: ProductAdapter

    /** Cart changed (here or in the Cart tab) -> redraw the cards so the steppers show the right numbers. */
    private val cartListener: () -> Unit = {
        if (_binding != null) adapter.notifyDataSetChanged()
    }

    private val productsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val products = snapshot.children
                .mapNotNull { Product.fromSnapshot(it) }
                .filter { it.name.isNotBlank() }
                .sortedWith(compareByDescending<Product> { it.inStock }.thenBy { it.name.lowercase(Locale.ROOT) })

            allProducts.clear()
            allProducts.addAll(products)

            // If the seller changed a price / stock of something already in the cart, fix the cart too
            val changed = CartManager.syncWithShop(shopId, products)
            if (changed.isNotEmpty()) toast("Your cart was updated: ${changed.joinToString()} changed in the shop")

            b.progress.visibility = View.GONE
            showProducts()
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load the products: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shopId = requireArguments().getString(ARG_SHOP_ID).orEmpty()
        shopName = requireArguments().getString(ARG_SHOP_NAME).orEmpty()

        adapter = ProductAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            quantityInCart = { CartManager.quantityOf(it.id) },
            onAdd = { addToCart(it) },
            onIncrease = { increase(it) },
            onDecrease = { CartManager.decrease(it.id) }
        )
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter

        binding.etSearch.doAfterTextChanged { showProducts() }

        productsRef = FirebaseRefs.sellerProductsRef(shopId).also { it.addValueEventListener(productsListener) }
        CartManager.addListener(cartListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsRef?.removeEventListener(productsListener)
        CartManager.removeListener(cartListener)
        _binding = null
    }

    /** Shows the products that match the search text. */
    private fun showProducts() {
        val b = _binding ?: return
        val query = b.etSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val shown = if (query.isEmpty()) allProducts.toList() else allProducts.filter {
            it.name.lowercase(Locale.ROOT).contains(query) || it.description.lowercase(Locale.ROOT).contains(query)
        }
        adapter.submitList(shown)

        val available = allProducts.count { it.inStock }
        b.tvCount.text = "${allProducts.size} products · $available available"
        b.emptyState.visibility = if (shown.isEmpty() && b.progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        b.tvEmpty.text = if (allProducts.isEmpty()) "This shop has no products yet" else "No products match \"$query\""
    }

    private fun addToCart(product: Product) {
        if (!product.inStock) {
            toast("${product.name} is sold out")
            return
        }
        // A cart can only have products from one shop
        if (CartManager.belongsToOtherShop(product.sellerId)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start a new cart?")
                .setMessage(
                    "Your cart has items from ${CartManager.shopName}. You can order from one shop at a time.\n\n" +
                            "Clear the cart and add ${product.name}?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear and add") { _, _ ->
                    CartManager.clear()
                    CartManager.add(product, shopName)
                }
                .show()
            return
        }
        if (!CartManager.add(product, shopName)) toast("Only ${product.quantity} of ${product.name} in stock")
    }

    private fun increase(product: Product) {
        if (!CartManager.increase(product.id)) toast("Only ${product.quantity} of ${product.name} in stock")
    }
}
