package com.example.sufraseller

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Tells the ViewPager2 on the seller dashboard which fragment to show on each tab
 * (same idea as the ViewPager adapter in Tutorial 3.1).
 *
 *   tab 0 -> SellerProductsFragment  (grid of my products, quick edit)
 *   tab 1 -> AddProductFragment      (form + camera/gallery photo -> S3)
 *   tab 2 -> SellerOrdersFragment    (orders placed by buyers)
 */
class HomePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        SellerHomeActivity.TAB_ADD -> AddProductFragment()
        SellerHomeActivity.TAB_ORDERS -> SellerOrdersFragment()
        else -> SellerProductsFragment()
    }
}
