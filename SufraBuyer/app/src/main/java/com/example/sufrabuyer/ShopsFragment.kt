package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.sufrabuyer.databinding.FragmentShopsBinding
import com.example.sufrabuyer.databinding.ItemCategoryChipBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

/**
 * Shops tab (Buyer app, Milestone 3). Same idea as ShopFragment in Tutorial 3.2:
 *
 *  - GridLayoutManager -> 2 shops per row
 *  - ShopAdapter connects the list of shops to the RecyclerView
 *  - fetchShopsFromFirebase() reads the "shops" node with a ValueEventListener (real time),
 *    so a new seller appears here immediately
 *
 * Extra: a search box and category chips filter the list.
 * Tapping a shop opens ShopActivity and sends the shop's details with putExtra.
 */
class ShopsFragment : Fragment() {

    private var _binding: FragmentShopsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ShopAdapter
    private val allShops = mutableListOf<Shop>()
    private var selectedCategory = ALL

    private companion object {
        const val ALL = "All"
    }

    private val shopsListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val b = _binding ?: return
            val shops = snapshot.children
                .mapNotNull { Shop.fromSnapshot(it) }
                .filter { it.shopName.isNotBlank() }
                .sortedBy { it.shopName.lowercase(Locale.ROOT) }
            allShops.clear()
            allShops.addAll(shops)
            b.progress.visibility = View.GONE
            showShops()
        }

        override fun onCancelled(error: DatabaseError) {
            val b = _binding ?: return
            b.progress.visibility = View.GONE
            toast("Couldn't load shops: ${error.message}")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShopsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShopAdapter(viewLifecycleOwner.lifecycleScope) { shop -> openShop(shop) }
        binding.rvShops.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvShops.adapter = adapter

        buildCategoryChips()
        binding.etSearch.doAfterTextChanged { showShops() }

        fetchShopsFromFirebase()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        FirebaseRefs.shopsRef().removeEventListener(shopsListener)
        _binding = null
    }

    /** Listens to the "shops" node. Called again automatically every time it changes. */
    private fun fetchShopsFromFirebase() {
        binding.progress.visibility = View.VISIBLE
        FirebaseRefs.shopsRef().addValueEventListener(shopsListener)
    }

    /** "All" + the shop categories from strings.xml, as chips. */
    private fun buildCategoryChips() {
        val categories = listOf(ALL) + resources.getStringArray(R.array.shop_categories)
        binding.chipGroup.removeAllViews()
        categories.forEach { category ->
            val chip = ItemCategoryChipBinding.inflate(layoutInflater, binding.chipGroup, false).root
            chip.id = View.generateViewId()
            chip.text = if (category == ALL) "✨ All" else "${categoryEmoji(category)} $category"
            chip.isChecked = category == selectedCategory
            chip.setOnClickListener {
                selectedCategory = category
                showShops()
            }
            binding.chipGroup.addView(chip)
        }
    }

    /** Shows the shops that match the selected category and the search text. */
    private fun showShops() {
        val b = _binding ?: return
        val query = b.etSearch.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

        val shown = allShops.filter { shop ->
            val categoryOk = selectedCategory == ALL || shop.shopCategory.equals(selectedCategory, ignoreCase = true)
            val searchOk = query.isEmpty() ||
                    shop.shopName.lowercase(Locale.ROOT).contains(query) ||
                    shop.shopCategory.lowercase(Locale.ROOT).contains(query)
            categoryOk && searchOk
        }
        adapter.submitList(shown)

        b.emptyState.visibility = if (shown.isEmpty() && b.progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        b.tvEmpty.text = if (allShops.isEmpty()) "No shops yet.\nSellers will appear here as soon as they join."
        else "No shops match your search"
    }

    /** Opens the shop's products. The shop's details are sent with putExtra. */
    private fun openShop(shop: Shop) {
        val intent = Intent(requireContext(), ShopActivity::class.java)
        intent.putExtra(ShopActivity.EXTRA_SHOP_ID, shop.uid)
        intent.putExtra(ShopActivity.EXTRA_SHOP_NAME, shop.shopName)
        intent.putExtra(ShopActivity.EXTRA_SHOP_CATEGORY, shop.shopCategory)
        intent.putExtra(ShopActivity.EXTRA_SHOP_ADDRESS, shop.address)
        startActivity(intent)
    }
}
