package com.example.sufrabuyer

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Tells the ViewPager2 on the buyer home screen which fragment to show on each tab
 * (same idea as the ViewPager adapter in Tutorial 3.1).
 *
 *   tab 0 -> ShopsFragment   (grid of shops)
 *   tab 1 -> CartFragment    (items in the cart, total, place order)
 */
class HomePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        BuyerHomeActivity.TAB_CART -> CartFragment()
        else -> ShopsFragment()
    }
}
