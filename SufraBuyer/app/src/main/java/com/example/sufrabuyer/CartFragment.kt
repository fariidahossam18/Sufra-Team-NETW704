package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufrabuyer.databinding.FragmentCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Cart tab (Buyer app, Milestone 3).
 *
 *  - Lists the products in the cart (CartManager) with  -  /  +  and remove
 *  - Shows the delivery address and phone from the buyer's profile
 *  - Shows the total price
 *  - Clear cart
 *  - Place order: checks the profile, checks the latest stock/prices, then saves the order
 *    under orders/{orderId} in Firebase, where the seller sees it instantly.
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CartAdapter
    private var placingOrder = false

    private val cartListener: () -> Unit = { showCart() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CartAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onIncrease = { item ->
                if (!CartManager.increase(item.productId)) toast("Only ${item.stock} of ${item.name} in stock")
            },
            onDecrease = { item -> CartManager.decrease(item.productId) },
            onRemove = { item -> CartManager.remove(item.productId) }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        binding.btnClear.setOnClickListener { confirmClear() }
        binding.btnPlaceOrder.setOnClickListener { placeOrder() }
        binding.cardAddress.setOnClickListener { openProfile() }
        binding.btnBrowse.setOnClickListener { backToShopping() }

        CartManager.addListener(cartListener)
        showCart()
    }

    override fun onResume() {
        super.onResume()
        loadDeliveryDetails()        // again after coming back from the profile screen
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CartManager.removeListener(cartListener)
        _binding = null
    }

    /** Draws the cart from CartManager. Called every time the cart changes. */
    private fun showCart() {
        val b = _binding ?: return
        val items = CartManager.getItems()
        val empty = items.isEmpty()

        b.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        b.cartContent.visibility = if (empty) View.GONE else View.VISIBLE
        adapter.submitList(items)
        if (empty) return

        val total = CartManager.total
        val count = CartManager.itemCount
        b.tvShopName.text = "🏪 ${CartManager.shopName}"
        b.tvItemCount.text = if (count == 1) "1 item" else "$count items"
        b.tvSubtotal.text = formatPrice(total)
        b.tvTotal.text = formatPrice(total)
        if (!placingOrder) b.btnPlaceOrder.text = "Place order  ·  ${formatPrice(total)}"
    }

    /** Reads the address and phone from users/{uid} once. */
    private fun loadDeliveryDetails() {
        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        FirebaseRefs.userRef(uid).get().addOnSuccessListener { snapshot ->
            val b = _binding ?: return@addOnSuccessListener
            val user = User.fromSnapshot(snapshot)
            b.tvAddress.text = user?.address?.ifBlank { null } ?: "Add your address"
            b.tvPhone.text = user?.phone?.ifBlank { null } ?: "Add your phone number"
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear cart?")
            .setMessage("All products will be removed from your cart.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ -> CartManager.clear() }
            .show()
    }

    /**
     * In the home screen: switch to the Shops tab.
     * In CartActivity (opened from a shop): close the cart, which goes back to that shop.
     */
    private fun backToShopping() {
        val home = activity as? BuyerHomeActivity
        if (home != null) home.openTab(BuyerHomeActivity.TAB_SHOPS) else activity?.finish()
    }

    private fun openProfile() = startActivity(Intent(requireContext(), ProfileActivity::class.java))

    /* ---------------------------------------------------------------------------
     *  Placing an order
     *  1) read the buyer's profile (name, phone, address)
     *  2) read the shop's products again, so we never order more than the stock
     *     or with an old price
     *  3) save the order under orders/{orderId}
     * ------------------------------------------------------------------------- */

    private fun placeOrder() {
        if (placingOrder || CartManager.isEmpty()) return
        val uid = FirebaseRefs.auth.currentUser?.uid
        if (uid == null) {
            toast("Please sign in again")
            return
        }
        setPlacing(true)

        // Step 1: the buyer's profile
        FirebaseRefs.userRef(uid).get()
            .addOnSuccessListener { snapshot ->
                val user = User.fromSnapshot(snapshot)
                if (user == null || user.address.isBlank() || !isValidPhone(user.phone)) {
                    setPlacing(false)
                    askToCompleteProfile()
                } else {
                    checkStockAndSave(uid, user)
                }
            }
            .addOnFailureListener { e ->
                setPlacing(false)
                toast("Couldn't place the order: ${e.message}")
            }
    }

    /** Step 2: compare the cart with the latest products in Firebase. */
    private fun checkStockAndSave(uid: String, user: User) {
        val sellerId = CartManager.shopId
        val shopName = CartManager.shopName

        FirebaseRefs.sellerProductsRef(sellerId).get()
            .addOnSuccessListener { snapshot ->
                val latest = snapshot.children.mapNotNull { Product.fromSnapshot(it) }
                val changed = CartManager.syncWithShop(sellerId, latest)
                when {
                    changed.isNotEmpty() -> {
                        setPlacing(false)
                        showCartChanged(changed)
                    }
                    CartManager.isEmpty() -> setPlacing(false)
                    else -> saveOrder(uid, user, sellerId, shopName)
                }
            }
            .addOnFailureListener { e ->
                setPlacing(false)
                toast("Couldn't check the stock: ${e.message}")
            }
    }

    /** Step 3: save the order. push() creates a unique id for it. */
    private fun saveOrder(uid: String, user: User, sellerId: String, shopName: String) {
        val ref = FirebaseRefs.ordersRef().push()
        val orderId = ref.key ?: return setPlacing(false)

        val items = CartManager.getItems().map {
            OrderItem(productId = it.productId, name = it.name, price = it.price, quantity = it.quantity, imageUrl = it.imageUrl)
        }
        val order = Order(
            id = orderId,
            buyerId = uid,
            buyerName = user.name,
            buyerPhone = user.phone,
            address = user.address,
            sellerId = sellerId,
            shopName = shopName,
            items = items,
            itemCount = items.sumOf { it.quantity },
            totalPrice = Math.round(items.sumOf { it.subtotal } * 100) / 100.0,
            status = Order.STATUS_PENDING
        )

        ref.setValue(order.toMap())
            .addOnSuccessListener {
                setPlacing(false)
                CartManager.clear()
                showOrderPlaced(order)
            }
            .addOnFailureListener { e ->
                setPlacing(false)
                toast("Couldn't place the order: ${e.message}")
            }
    }

    private fun setPlacing(placing: Boolean) {
        placingOrder = placing
        val b = _binding ?: return
        b.btnPlaceOrder.isEnabled = !placing
        b.btnClear.isEnabled = !placing
        b.placeProgress.visibility = if (placing) View.VISIBLE else View.GONE
        b.btnPlaceOrder.text = if (placing) "Placing order…" else "Place order  ·  ${formatPrice(CartManager.total)}"
    }

    private fun askToCompleteProfile() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle("Where should we deliver?")
            .setMessage("Please add your phone number and delivery address to your profile first.")
            .setNegativeButton("Later", null)
            .setPositiveButton("Open profile") { _, _ -> openProfile() }
            .show()
    }

    private fun showCartChanged(changed: List<String>) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle("Your cart was updated")
            .setMessage(
                "The shop changed these products since you added them:\n\n• " + changed.joinToString("\n• ") +
                        "\n\nPlease check the cart and place the order again."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showOrderPlaced(order: Order) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle("Order placed 🎉")
            .setMessage(
                "Order ${shortOrderId(order.id)} was sent to ${order.shopName}.\n\n" +
                        "${order.itemCount} items · ${formatPrice(order.totalPrice)}\n" +
                        "Delivering to: ${order.address}"
            )
            .setPositiveButton("Great") { _, _ -> backToShopping() }
            .show()
    }
}
