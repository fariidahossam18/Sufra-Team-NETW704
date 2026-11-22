package com.example.sufradelivery

/**
 * Sufra Delivery (Milestone 4 bonus): volunteers sign in with their existing BUYER account.
 */
object AppConfig {
    /** Only buyer accounts can sign in to this app. */
    const val APP_ROLE = User.ROLE_BUYER

    /** Shown when a seller tries to sign in here. */
    const val OTHER_APP_NAME = "Sufra Seller"

    /** The screen opened after signing in. */
    val HOME_SCREEN: Class<*> = DeliveryHomeActivity::class.java
}
