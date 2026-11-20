package com.example.sufrabuyer

import java.util.Calendar
import java.util.Locale

/**
 * Delivery price (Milestone 4: "a delivery price that is dynamically calculated").
 *
 * We don't have GPS locations, so "distance" is estimated from the AREA, the first part of the
 * address before a comma ("Nasr City, Street 9" -> "nasr city"):
 *
 *   base fee                                 EGP 15
 *   buyer in a different area than the shop  + EGP 10   (farther away)
 *   big order (more than 5 pieces)           + EGP 5    (needs a bigger bag / box)
 *   rush hour (7 pm to 10:59 pm)             + EGP 5    (fewer drivers available)
 *   order of EGP 500 or more                 free delivery
 *   pick up                                  always free
 */
object DeliveryFee {

    const val BASE = 15.0
    const val OTHER_AREA = 10.0
    const val BIG_ORDER = 5.0
    const val RUSH_HOUR = 5.0
    const val FREE_FROM = 500.0
    private const val BIG_ORDER_PIECES = 5
    private val RUSH_HOURS = 19..22

    /** The fee and a short explanation shown under the delivery options. */
    data class Quote(val fee: Double, val explanation: String)

    fun calculate(
        subtotal: Double,
        pieces: Int,
        buyerAddress: String,
        shopAddress: String,
        hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    ): Quote {
        if (subtotal >= FREE_FROM) return Quote(0.0, "Free delivery on orders of EGP 500 or more 🎉")

        var fee = BASE
        val parts = mutableListOf("base ${formatPrice(BASE)}")
        if (!sameArea(buyerAddress, shopAddress)) {
            fee += OTHER_AREA
            parts.add("other area +${formatPrice(OTHER_AREA)}")
        }
        if (pieces > BIG_ORDER_PIECES) {
            fee += BIG_ORDER
            parts.add("big order +${formatPrice(BIG_ORDER)}")
        }
        if (hourOfDay in RUSH_HOURS) {
            fee += RUSH_HOUR
            parts.add("rush hour +${formatPrice(RUSH_HOUR)}")
        }
        return Quote(fee, parts.joinToString(" · "))
    }

    /**
     * A delivery address is valid when it has at least 8 characters and some letters,
     * e.g. "Maadi, Street 9". ("abc" or "12345678" are refused.)
     */
    fun isValidAddress(address: String): Boolean {
        val a = address.trim()
        return a.length >= 8 && a.any { it.isLetter() }
    }

    /** "Nasr City, Street 9" and "nasr city - Abbas" -> same area. Unknown address -> not the same. */
    fun sameArea(a: String, b: String): Boolean {
        val areaA = area(a)
        val areaB = area(b)
        return areaA.isNotEmpty() && areaA == areaB
    }

    private fun area(address: String): String =
        address.split(',', '-', '،').firstOrNull().orEmpty().trim().lowercase(Locale.ROOT)
}
