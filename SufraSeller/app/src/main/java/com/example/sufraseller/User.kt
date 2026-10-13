package com.example.sufraseller

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.Exclude

/**
 * One user's profile, stored in Firebase Realtime Database at /users/{uid}.
 *
 * The same structure is used by BOTH apps (Buyer and Seller share one Firebase project).
 *
 * Example JSON:
 * "users": {
 *   "<uid>": {
 *     "uid": "<uid>", "role": "seller", "name": "Farida", "email": "farida@example.com",
 *     "phone": "01012345678", "address": "New Cairo",
 *     "shopName": "Farida's Bakery", "shopCategory": "Bakery",      <- sellers only
 *     "photoUrl": "https://<bucket>.s3.<region>.amazonaws.com/profile-photos/<uid>/1760000000000.jpg",
 *     "photoUpdatedAt": 1760000000000,                               <- Milestone 2
 *     "createdAt": 1759050000000, "updatedAt": 1759050000000
 *   }
 * }
 */
data class User(
    var uid: String = "",
    var role: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var address: String = "",
    var shopName: String = "",
    var shopCategory: String = "",
    var photoUrl: String = "",          // S3 URL of the profile photo (Milestone 2)
    var photoUpdatedAt: Long = 0L,      // when the photo was last changed (Milestone 2)
    var createdAt: Long = 0L,
    var updatedAt: Long = 0L
) {
    @get:Exclude  // helper only, not saved to the database
    val isSeller: Boolean get() = role == ROLE_SELLER

    companion object {
        const val ROLE_BUYER = "buyer"
        const val ROLE_SELLER = "seller"

        /**
         * Safely builds a User from a Firebase snapshot.
         * Unlike getValue(User::class.java), this never crashes if a value has the wrong type
         * (for example a phone number typed as a number in the Firebase console).
         */
        fun fromSnapshot(snapshot: DataSnapshot): User? {
            if (!snapshot.exists()) return null
            fun text(key: String) = snapshot.child(key).value?.toString().orEmpty()
            fun number(key: String) = (snapshot.child(key).value as? Number)?.toLong() ?: 0L
            return User(
                uid = text("uid"),
                role = text("role"),
                name = text("name"),
                email = text("email"),
                phone = text("phone"),
                address = text("address"),
                shopName = text("shopName"),
                shopCategory = text("shopCategory"),
                photoUrl = text("photoUrl"),
                photoUpdatedAt = number("photoUpdatedAt"),
                createdAt = number("createdAt"),
                updatedAt = number("updatedAt")
            )
        }
    }
}
