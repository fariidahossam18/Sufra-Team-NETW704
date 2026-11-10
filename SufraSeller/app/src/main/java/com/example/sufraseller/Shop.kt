package com.example.sufraseller

import com.google.firebase.database.DataSnapshot

/**
 * A shop as the buyers see it (Milestone 3).
 *
 * Saved in Firebase under   shops/{sellerUid}
 * The Seller app copies these fields from the seller's profile every time the dashboard opens,
 * so a new seller (or a changed shop name / photo) shows up in the Buyer app straight away.
 * The shop id is the seller's uid, so products of this shop are under products/{uid}.
 */
data class Shop(
    var uid: String = "",
    var shopName: String = "",
    var shopCategory: String = "",
    var address: String = "",
    var ownerName: String = "",
    var photoUrl: String = "",
    var updatedAt: Long = 0L
) {
    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): Shop? {
            if (!snapshot.exists()) return null
            fun text(key: String) = snapshot.child(key).value?.toString().orEmpty()
            val updated = snapshot.child("updatedAt").value
            return Shop(
                uid = snapshot.key.orEmpty(),
                shopName = text("shopName"),
                shopCategory = text("shopCategory"),
                address = text("address"),
                ownerName = text("ownerName"),
                photoUrl = text("photoUrl"),
                updatedAt = (updated as? Number)?.toLong() ?: 0L
            )
        }
    }
}
