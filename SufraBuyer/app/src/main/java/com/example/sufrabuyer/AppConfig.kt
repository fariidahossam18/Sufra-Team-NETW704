package com.example.sufrabuyer

/**
 * The ONLY file that is different between the Buyer app and the Seller app (besides the home
 * screen and the colours/texts). It decides which role this app is for.
 */
object AppConfig {
    /** Only accounts with this role can sign up / sign in in this app. */
    const val APP_ROLE = User.ROLE_BUYER

    /** Name of the other app, shown when someone signs in with the wrong account type. */
    const val OTHER_APP_NAME = "Sufra Seller"

    /** The screen opened after a successful sign in / sign up. */
    val HOME_SCREEN: Class<*> = BuyerHomeActivity::class.java
}
