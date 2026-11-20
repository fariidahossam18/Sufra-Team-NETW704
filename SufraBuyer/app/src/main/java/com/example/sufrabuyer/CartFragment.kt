package com.example.sufrabuyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sufrabuyer.databinding.DialogDeliveryBinding
import com.example.sufrabuyer.databinding.FragmentCartBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.ServerValue

/**
 * Cart tab (Buyer app, Milestones 3 and 4).
 *
 *  - Lists the products in the cart (CartManager) with  -  /  +  and remove (Snackbar UNDO)
 *  - Milestone 4: Delivery or Pick up. Delivery needs a valid address and has a fee that is
 *    calculated by DeliveryFee (area, order size, rush hour, free over EGP 500). Pick up is free.
 *  - Shows subtotal, delivery fee and total; bonus: "Use my credit" (earned in the Delivery app)
 *  - Clear cart
 *  - Place order:
 *      1) client-side checks: profile complete, latest stock and prices (syncWithShop)
 *      2) server-side checks + inventory update: OrderPlacer takes the stock with Firebase
 *         transactions, then saves the order under orders/{orderId}
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CartAdapter
    private var placingOrder = false

    // Last delivery details read from the profile (used to fill the delivery dialog)
    private var deliveryAddress = ""
    private var deliveryPhone = ""

    // Milestone 4: the cart shop's address (to compare areas for the delivery fee)
    private var shopAddress = ""
    private var loadedShopId = ""

    // Milestone 4 bonus: the buyer's delivery credit (users/{uid}/credit)
    private var credit = 0.0

    /** True when the buyer chose "Pick up" instead of "Delivery". */
    private val isPickup: Boolean
        get() = _binding?.rbPickup?.isChecked == true

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
            onRemove = { item -> removeWithUndo(item) }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        binding.btnClear.setOnClickListener { confirmClear() }
        binding.btnPlaceOrder.setOnClickListener { placeOrder() }
        binding.cardAddress.setOnClickListener { showDeliveryDialog() }
        binding.btnBrowse.setOnClickListener { backToShopping() }
        binding.rgMethod.setOnCheckedChangeListener { _, _ -> showCart() }   // Milestone 4
        binding.cbUseCredit.setOnCheckedChangeListener { _, _ -> showCart() } // Milestone 4 bonus

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

        val subtotal = CartManager.total
        val count = CartManager.itemCount
        b.tvShopName.text = "🏪 ${CartManager.shopName}"
        b.tvItemCount.text = if (count == 1) "1 item" else "$count items"
        loadShopAddress()

        // ---- Milestone 4: delivery or pick up ----
        val fee = currentFee()
        b.cardAddress.visibility = if (isPickup) View.GONE else View.VISIBLE
        b.cardPickup.visibility = if (isPickup) View.VISIBLE else View.GONE
        b.tvPickupInfo.text = listOf(CartManager.shopName, shopAddress).filter { it.isNotBlank() }.joinToString("  ·  ")
        b.tvFeeNote.text = if (isPickup) "Pick up is free. You'll get a notification when it's ready."
        else feeQuote().explanation
        b.tvDeliveryFee.text = if (fee == 0.0) "Free" else formatPrice(fee)
        b.tvDeliveryFee.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(), if (fee == 0.0) R.color.brand_green else R.color.text_dark
            )
        )

        // ---- Milestone 4 bonus: credit ----
        val used = creditToUse(subtotal + fee)
        b.creditRow.visibility = if (credit > 0.0) View.VISIBLE else View.GONE
        b.cbUseCredit.text = "Use my credit (${formatPrice(credit)})"
        b.tvCredit.text = if (used > 0.0) "−${formatPrice(used)}" else ""

        val total = subtotal + fee - used
        b.tvSubtotal.text = formatPrice(subtotal)
        b.tvTotal.text = formatPrice(total)
        if (!placingOrder) b.btnPlaceOrder.text = "Place order  ·  ${formatPrice(total)}"
    }

    /** How much credit is used: all of it, but never more than the order costs. */
    private fun creditToUse(orderCost: Double): Double {
        val b = _binding ?: return 0.0
        if (!b.cbUseCredit.isChecked || credit <= 0.0) return 0.0
        return Math.round(minOf(credit, orderCost) * 100) / 100.0
    }

    /** Delivery fee for the current cart (0 when picking up). */
    private fun currentFee(): Double = if (isPickup) 0.0 else feeQuote().fee

    private fun feeQuote(): DeliveryFee.Quote =
        DeliveryFee.calculate(CartManager.total, CartManager.itemCount, deliveryAddress, shopAddress)

    /** Reads shops/{shopId} once to know the shop's address (for the fee and the pick-up card). */
    private fun loadShopAddress() {
        val shopId = CartManager.shopId
        if (shopId.isEmpty() || shopId == loadedShopId) return
        loadedShopId = shopId
        FirebaseRefs.shopRef(shopId).get().addOnSuccessListener { snapshot ->
            shopAddress = Shop.fromSnapshot(snapshot)?.address.orEmpty()
            showCart()
        }
    }

    /** Reads the address and phone from users/{uid} once. */
    private fun loadDeliveryDetails() {
        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        FirebaseRefs.userRef(uid).get().addOnSuccessListener { snapshot ->
            val b = _binding ?: return@addOnSuccessListener
            val user = User.fromSnapshot(snapshot)
            deliveryAddress = user?.address.orEmpty()
            deliveryPhone = user?.phone.orEmpty()
            credit = (snapshot.child("credit").value as? Number)?.toDouble() ?: 0.0
            b.tvAddress.text = user?.address?.ifBlank { null } ?: "Add your address"
            b.tvPhone.text = user?.phone?.ifBlank { null } ?: "Add your phone number"
            showCart()        // the delivery fee depends on the address
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
     *  2) CLIENT-SIDE check: read the shop's products again, so the cart never asks for more
     *     than the stock or uses an old price
     *  3) SERVER-SIDE check + inventory update (OrderPlacer): transactions take the stock,
     *     then the order is saved under orders/{orderId}
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
                // Delivery needs a valid address and phone; pick up needs a phone (to contact the buyer)
                val missingAddress = !isPickup && !DeliveryFee.isValidAddress(user?.address.orEmpty())
                if (user == null || missingAddress || !isValidPhone(user.phone)) {
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

    /**
     * Step 3: build the order and let OrderPlacer take the stock (transactions) and save it.
     * push() creates a unique id for the order.
     */
    private fun saveOrder(uid: String, user: User, sellerId: String, shopName: String) {
        val ref = FirebaseRefs.ordersRef().push()
        val orderId = ref.key ?: return setPlacing(false)

        val items = CartManager.getItems().map {
            OrderItem(productId = it.productId, name = it.name, price = it.price, quantity = it.quantity, imageUrl = it.imageUrl)
        }
        val subtotal = Math.round(items.sumOf { it.subtotal } * 100) / 100.0
        val fee = currentFee()
        val used = creditToUse(subtotal + fee)
        val order = Order(
            id = orderId,
            buyerId = uid,
            buyerName = user.name,
            buyerPhone = user.phone,
            address = if (isPickup) "Pick up from the shop" else user.address,
            sellerId = sellerId,
            shopName = shopName,
            items = items,
            itemCount = items.sumOf { it.quantity },
            subtotal = subtotal,
            deliveryMethod = if (isPickup) Order.METHOD_PICKUP else Order.METHOD_DELIVERY,
            deliveryFee = fee,
            creditUsed = used,
            totalPrice = Math.round((subtotal + fee - used) * 100) / 100.0,
            status = Order.STATUS_WAITING
        )

        OrderPlacer.place(ref, order) { result ->
            setPlacing(false)
            when (result) {
                is OrderPlacer.Result.Success -> {
                    if (used > 0.0) loadDeliveryDetails()          // refresh the credit left
                    CartManager.clear()
                    showOrderPlaced(order)
                }
                is OrderPlacer.Result.OutOfStock -> showSoldOut(result.productName, sellerId)
                is OrderPlacer.Result.Failed -> toast("Couldn't place the order: ${result.message}")
            }
        }
    }

    /** Server-side check failed: someone bought it first. Refresh the cart from the latest stock. */
    private fun showSoldOut(productName: String, sellerId: String) {
        FirebaseRefs.sellerProductsRef(sellerId).get().addOnSuccessListener { snapshot ->
            CartManager.syncWithShop(sellerId, snapshot.children.mapNotNull { Product.fromSnapshot(it) })
        }
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle("Not enough stock")
            .setMessage(
                "Sorry, someone just bought the last pieces of $productName.\n\n" +
                        "Your cart was updated to what's left. Nothing was charged and no order was sent."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setPlacing(placing: Boolean) {
        placingOrder = placing
        val b = _binding ?: return
        b.btnPlaceOrder.isEnabled = !placing
        b.btnClear.isEnabled = !placing
        b.placeProgress.visibility = if (placing) View.VISIBLE else View.GONE
        b.rbDelivery.isEnabled = !placing
        b.rbPickup.isEnabled = !placing
        if (placing) b.btnPlaceOrder.text = "Placing order…" else showCart()   // showCart writes the total
    }

    private fun askToCompleteProfile() {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle(if (isPickup) "How can the shop reach you?" else "Where should we deliver?")
            .setMessage(
                if (isPickup) "Please add a valid phone number first, so the shop can contact you."
                else "Please add a valid delivery address (area and street, e.g. \"Maadi, Street 9\") and phone number first."
            )
            .setNegativeButton("Later", null)
            .setPositiveButton("Add details") { _, _ -> showDeliveryDialog() }
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
        val how = if (order.isDelivery) "Delivering to: ${order.address}\nDelivery fee: ${
            if (order.deliveryFee == 0.0) "Free" else formatPrice(order.deliveryFee)
        }" else "Pick up from ${order.shopName}"
        MaterialAlertDialogBuilder(context)
            .setTitle("Order placed 🎉")
            .setMessage(
                "Order ${shortOrderId(order.id)} was sent to ${order.shopName}.\n" +
                        "Status: ${Order.statusLabel(order.status)}\n\n" +
                        "${order.itemCount} items · ${formatPrice(order.totalPrice)}\n$how"
            )
            .setPositiveButton("Great") { _, _ -> backToShopping() }
            .setNeutralButton("Track order") { _, _ -> openOrdersTab() }
            .show()
    }

    /** Milestone 4: opens the Orders tab of the home screen (also from CartActivity). */
    private fun openOrdersTab() {
        val home = activity as? BuyerHomeActivity
        if (home != null) {
            home.openTab(BuyerHomeActivity.TAB_ORDERS)
        } else {
            startActivity(
                Intent(requireContext(), BuyerHomeActivity::class.java)
                    .putExtra(BuyerHomeActivity.EXTRA_OPEN_TAB, BuyerHomeActivity.TAB_ORDERS)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            activity?.finish()
        }
    }

    /* ---------------------------------------------------------------------------
     *  Tutorial 5 (Alerts): Snackbar with an action + custom layout dialog
     * ------------------------------------------------------------------------- */

    /** Removes an item and shows a Snackbar with UNDO that puts it back. */
    private fun removeWithUndo(item: CartItem) {
        val b = _binding ?: return
        val sellerId = CartManager.shopId          // remember the shop before the cart may become empty
        val shopName = CartManager.shopName
        CartManager.remove(item.productId)
        Snackbar.make(b.root, "${item.name} removed from your cart", Snackbar.LENGTH_LONG)
            .setAction("UNDO") { CartManager.restore(item, sellerId, shopName) }
            .show()
    }

    /**
     * Custom layout dialog (setView): edit the delivery address and phone without leaving the cart.
     * Saved to users/{uid} with updateChildren(), the same way the profile screen does it.
     */
    private fun showDeliveryDialog() {
        val uid = FirebaseRefs.auth.currentUser?.uid ?: return
        val dialogBinding = DialogDeliveryBinding.inflate(layoutInflater)
        dialogBinding.etAddress.setText(deliveryAddress)
        dialogBinding.etPhone.setText(deliveryPhone)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delivery details")
            .setView(dialogBinding.root)                       // <- our own XML layout
            .setPositiveButton("Save", null)                   // set below, so errors keep the dialog open
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Open profile") { _, _ -> openProfile() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = dialogBinding.etAddress.value()
                val phone = dialogBinding.etPhone.value()
                dialogBinding.tilAddress.error =
                    if (!DeliveryFee.isValidAddress(address)) "Write the area and street, e.g. Maadi, Street 9" else null
                dialogBinding.tilPhone.error =
                    if (!isValidPhone(phone)) "Enter a valid phone number (8–15 digits)" else null
                if (dialogBinding.tilAddress.error != null || dialogBinding.tilPhone.error != null) return@setOnClickListener

                val updates = mapOf<String, Any>(
                    "address" to address,
                    "phone" to phone,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
                FirebaseRefs.userRef(uid).updateChildren(updates)
                    .addOnSuccessListener {
                        toast("Delivery details saved")
                        loadDeliveryDetails()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e -> toast("Couldn't save: ${e.message}") }
            }
        }
        dialog.show()
    }
}
