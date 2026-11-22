package com.example.sufradelivery

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** Tabs of the Delivery app: Available, My deliveries, Rewards (swipe between them). */
class DeliveryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        DeliveryHomeActivity.TAB_MINE -> DeliveryOrdersFragment.newInstance(DeliveryOrdersFragment.MODE_MINE)
        DeliveryHomeActivity.TAB_REWARDS -> RewardsFragment()
        else -> DeliveryOrdersFragment.newInstance(DeliveryOrdersFragment.MODE_AVAILABLE)
    }
}
