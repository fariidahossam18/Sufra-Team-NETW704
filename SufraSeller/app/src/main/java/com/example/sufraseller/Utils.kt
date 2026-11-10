package com.example.sufraseller

import android.app.Activity
import android.content.Intent
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------------------------------------------------------------------
 *  Small helper functions shared by all screens
 * ------------------------------------------------------------------------- */

/**
 * Makes the colourful header go behind the status bar (like real delivery apps)
 * while keeping content away from the status bar, navigation bar and keyboard.
 */
fun Activity.applyEdgeToEdge(
    root: View,
    header: View? = null,
    padBottom: Boolean = true,
    darkStatusBarIcons: Boolean = false
) {
    WindowCompat.getInsetsController(window, window.decorView)
        .isAppearanceLightStatusBars = darkStatusBarIcons

    val headerTop = header?.paddingTop ?: 0
    val rootTop = root.paddingTop
    val rootBottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        if (header != null) header.updatePadding(top = headerTop + bars.top)
        else root.updatePadding(top = rootTop + bars.top)
        if (padBottom) root.updatePadding(bottom = rootBottom + maxOf(bars.bottom, ime.bottom))
        insets
    }
}

fun Activity.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()

fun EditText.value(): String = text?.toString()?.trim().orEmpty()

fun isValidEmail(email: String): Boolean =
    email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

/** Accepts e.g. 01012345678 or +201012345678 */
fun isValidPhone(phone: String): Boolean =
    Regex("^\\+?[0-9]{8,15}$").matches(phone.replace(" ", ""))

/** "Farida Hossam" -> "FH" */
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

fun formatTimestamp(millis: Long): String =
    if (millis <= 0) "—"
    else SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(millis))

/** Opens this app's home screen and clears the back history (so Back can't return to login). */
fun Activity.goToHome() {
    startActivity(
        Intent(this, AppConfig.HOME_SCREEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}

/** "buyer" -> "Buyer", "seller" -> "Seller" */
fun roleLabel(role: String): String = if (role == User.ROLE_SELLER) "Seller" else "Buyer"

fun Activity.goToLogin() {
    startActivity(
        Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}

/** Turns Firebase Auth exceptions into friendly messages. */
fun friendlyAuthError(e: Exception): String = when (e) {
    // WeakPassword must come before InvalidCredentials (it is a subclass of it)
    is FirebaseAuthWeakPasswordException -> "Password is too weak (use at least 6 characters)."
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseAuthInvalidUserException -> "No account found with this email."
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
    is FirebaseNetworkException -> "No internet connection. Please try again."
    else -> e.localizedMessage ?: "Something went wrong. Please try again."
}

/* ---------------------------------------------------------------------------
 *  Milestone 3 helpers
 * ------------------------------------------------------------------------- */

/** Same as Activity.toast but for fragments (does nothing if the fragment is already closed). */
fun Fragment.toast(message: String) {
    context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
}

/** 45.0 -> "EGP 45",  12.5 -> "EGP 12.5",  1250.75 -> "EGP 1,250.75" */
fun formatPrice(value: Double): String =
    "EGP " + DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(value)

/** An emoji for each shop category (shown when a shop or product has no photo yet). */
fun categoryEmoji(category: String): String {
    val c = category.lowercase(Locale.ROOT)
    return when {
        "grocery" in c -> "🛒"
        "bakery" in c -> "🥐"
        "cafe" in c -> "☕"
        "sweet" in c || "dessert" in c -> "🍰"
        "pharmacy" in c -> "💊"
        "fruit" in c || "vegetable" in c -> "🍎"
        else -> "🍽️"
    }
}

/** Short, readable order number: "-NxA9k2LmQ" -> "#A9K2LMQ" (last 7 characters). */
fun shortOrderId(id: String): String = "#" + id.takeLast(7).uppercase(Locale.ROOT)
