package com.example.sufraseller

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * One place for all Firebase access, so every screen uses the same instances.
 *
 * Database tree:
 *   users/{uid}                    private profile (Milestone 1)
 *   shops/{sellerUid}              public shop card that buyers see (Milestone 3)
 *   products/{sellerUid}/{id}      products of each shop (Milestone 3)
 *   orders/{orderId}               orders placed by buyers (Milestone 3)
 */
object FirebaseRefs {

    /**
     * Leave this EMPTY if your Realtime Database is in "United States (us-central1)".
     *
     * If you picked another location (e.g. Belgium / europe-west1), paste the URL shown
     * at the top of the Firebase console → Realtime Database → Data tab, for example:
     * "https://sufra-12345-default-rtdb.europe-west1.firebasedatabase.app"
     */
    private const val DATABASE_URL = ""

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val database: FirebaseDatabase
        get() = if (DATABASE_URL.isBlank()) FirebaseDatabase.getInstance()
        else FirebaseDatabase.getInstance(DATABASE_URL)

    /** /users */
    fun usersRef(): DatabaseReference = database.getReference("users")

    /** /users/{uid} */
    fun userRef(uid: String): DatabaseReference = usersRef().child(uid)

    /** /shops */
    fun shopsRef(): DatabaseReference = database.getReference("shops")

    /** /shops/{sellerUid} */
    fun shopRef(sellerUid: String): DatabaseReference = shopsRef().child(sellerUid)

    /** /products */
    fun productsRef(): DatabaseReference = database.getReference("products")

    /** /products/{sellerUid}  (all products of one shop) */
    fun sellerProductsRef(sellerUid: String): DatabaseReference = productsRef().child(sellerUid)

    /** /products/{sellerUid}/{productId} */
    fun productRef(sellerUid: String, productId: String): DatabaseReference =
        sellerProductsRef(sellerUid).child(productId)

    /** /orders */
    fun ordersRef(): DatabaseReference = database.getReference("orders")
}
