package com.example.sufraseller

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * One place for all Firebase access, so every screen uses the same instances.
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
}
