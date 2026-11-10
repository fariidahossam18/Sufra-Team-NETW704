package com.example.sufrabuyer

/**
 * One product inside the cart.
 * [stock] is how many pieces the shop has, so the buyer can't add more than that.
 */
data class CartItem(
    val productId: String,
    var name: String,
    var price: Double,
    var imageUrl: String,
    var quantity: Int,
    var stock: Int
) {
    val subtotal: Double
        get() = price * quantity
}

/**
 * The buyer's cart (Milestone 3).
 *
 * It is a singleton (object), so the Products screen, the Cart tab and the badge on the
 * home screen all share the SAME cart. Screens register a listener and get called every time
 * the cart changes, so they always show the right numbers.
 *
 * Rules:
 *  - A cart holds products from ONE shop only (like Talabat). Adding from another shop
 *    asks the user to clear the cart first.
 *  - The quantity of a product can't go above the stock the seller entered.
 */
object CartManager {

    /** The shop (seller uid) and its name for the products in the cart. Empty when the cart is empty. */
    var shopId: String = ""
        private set
    var shopName: String = ""
        private set

    private val items = LinkedHashMap<String, CartItem>()     // productId -> item (keeps adding order)
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    /** A copy of the items, in the order they were added. */
    fun getItems(): List<CartItem> = items.values.toList()

    fun isEmpty(): Boolean = items.isEmpty()

    /** Total number of pieces (shown on the badge). */
    val itemCount: Int
        get() = items.values.sumOf { it.quantity }

    /** Price of everything in the cart. */
    val total: Double
        get() = items.values.sumOf { it.subtotal }

    fun quantityOf(productId: String): Int = items[productId]?.quantity ?: 0

    /** True if the cart already has products from a DIFFERENT shop. */
    fun belongsToOtherShop(sellerId: String): Boolean = items.isNotEmpty() && shopId != sellerId

    /**
     * Adds one piece of [product]. Returns false if there is no stock left
     * (or the cart has another shop's products: check belongsToOtherShop() first).
     */
    fun add(product: Product, shopName: String): Boolean {
        if (belongsToOtherShop(product.sellerId)) return false
        val current = items[product.id]
        val inCart = current?.quantity ?: 0
        if (inCart >= product.quantity) return false

        if (items.isEmpty()) {
            shopId = product.sellerId
            this.shopName = shopName
        }
        if (current == null) {
            items[product.id] = CartItem(product.id, product.name, product.price, product.imageUrl, 1, product.quantity)
        } else {
            current.quantity++
            current.stock = product.quantity
            current.price = product.price
        }
        notifyChanged()
        return true
    }

    /** +1. Returns false if it would go above the stock. */
    fun increase(productId: String): Boolean {
        val item = items[productId] ?: return false
        if (item.quantity >= item.stock) return false
        item.quantity++
        notifyChanged()
        return true
    }

    /** -1. When it reaches 0 the product is removed from the cart. */
    fun decrease(productId: String) {
        val item = items[productId] ?: return
        if (item.quantity <= 1) items.remove(productId) else item.quantity--
        if (items.isEmpty()) resetShop()
        notifyChanged()
    }

    fun remove(productId: String) {
        if (items.remove(productId) == null) return
        if (items.isEmpty()) resetShop()
        notifyChanged()
    }

    fun clear() {
        items.clear()
        resetShop()
        notifyChanged()
    }

    /**
     * Called with the latest products of the cart's shop (from Firebase).
     * Keeps names, prices, photos and stock up to date, lowers a quantity that is now above
     * the stock, and removes products that were deleted or sold out.
     * Returns the names of products that were changed, so the screen can tell the buyer.
     */
    fun syncWithShop(sellerId: String, products: List<Product>): List<String> {
        if (items.isEmpty() || sellerId != shopId) return emptyList()
        val latest = products.associateBy { it.id }
        val changed = mutableListOf<String>()
        var anyUpdate = false

        val iterator = items.values.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val product = latest[item.productId]
            if (product == null || product.quantity <= 0) {
                changed.add(item.name)
                iterator.remove()
                anyUpdate = true
                continue
            }
            if (item.quantity > product.quantity || item.price != product.price) changed.add(product.name)
            if (item.name != product.name || item.imageUrl != product.imageUrl || item.stock != product.quantity ||
                item.price != product.price) anyUpdate = true
            item.name = product.name
            item.price = product.price
            item.imageUrl = product.imageUrl
            item.stock = product.quantity
            if (item.quantity > item.stock) item.quantity = item.stock
        }
        if (items.isEmpty()) resetShop()
        if (anyUpdate) notifyChanged()
        return changed
    }

    private fun resetShop() {
        shopId = ""
        shopName = ""
    }
}
