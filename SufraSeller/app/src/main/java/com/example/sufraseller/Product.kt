package com.example.sufraseller

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Exclude
import com.google.firebase.database.ServerValue

/**
 * One product sold by a seller (Milestone 3).
 *
 * Saved in Firebase under   products/{sellerId}/{productId}
 * The photo itself lives in AWS S3 (product-photos/<sellerId>/<time>.jpg),
 * Firebase only keeps its URL in imageUrl.
 */
data class Product(
    var id: String = "",
    var sellerId: String = "",
    var name: String = "",
    var description: String = "",
    var price: Double = 0.0,
    var quantity: Int = 0,
    var imageUrl: String = "",
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
) {
    @get:Exclude
    val inStock: Boolean
        get() = quantity > 0

    /** The JSON we save in Firebase for a NEW product. */
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "sellerId" to sellerId,
        "name" to name,
        "description" to description,
        "price" to price,
        "quantity" to quantity,
        "imageUrl" to imageUrl,
        "createdAt" to ServerValue.TIMESTAMP,
        "updatedAt" to ServerValue.TIMESTAMP
    )

    companion object {
        // Input limits (used by the Add and Edit screens)
        const val NAME_MIN = 2
        const val NAME_MAX = 40
        const val DESCRIPTION_MAX = 150
        const val PRICE_MAX = 100_000.0
        const val QUANTITY_MAX = 9_999

        /** Safe conversion from Firebase (works even if a field is missing or typed by hand in the console). */
        fun fromSnapshot(snapshot: DataSnapshot): Product? {
            if (!snapshot.exists()) return null
            fun text(key: String) = snapshot.child(key).value?.toString().orEmpty()
            fun number(key: String): Double = when (val v = snapshot.child(key).value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            return Product(
                id = snapshot.key.orEmpty(),
                sellerId = text("sellerId").ifEmpty { snapshot.ref.parent?.key.orEmpty() },
                name = text("name"),
                description = text("description"),
                price = number("price"),
                quantity = number("quantity").toInt(),
                imageUrl = text("imageUrl"),
                createdAt = number("createdAt").toLong(),
                updatedAt = number("updatedAt").toLong()
            )
        }

        /** Returns an error message, or null if the name is fine. */
        fun nameError(name: String): String? = when {
            name.length < NAME_MIN -> "Name must be at least $NAME_MIN characters"
            name.length > NAME_MAX -> "Name can't be longer than $NAME_MAX characters"
            else -> null
        }

        /** "45" or "45.5" -> 45.5   (null if empty, not a number, <= 0 or too big) */
        fun parsePrice(text: String): Double? {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (value <= 0.0 || value > PRICE_MAX) return null
            return Math.round(value * 100) / 100.0          // keep 2 decimals only
        }

        /** "12" -> 12   (null if empty, not a whole number or outside min..QUANTITY_MAX) */
        fun parseQuantity(text: String, min: Int): Int? {
            val value = text.trim().toIntOrNull() ?: return null
            return if (value in min..QUANTITY_MAX) value else null
        }
    }
}
