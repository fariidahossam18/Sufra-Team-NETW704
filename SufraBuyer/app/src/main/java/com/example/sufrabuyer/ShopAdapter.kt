package com.example.sufrabuyer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sufrabuyer.databinding.ItemShopCardBinding
import kotlinx.coroutines.CoroutineScope

/**
 * Adapter for the shops grid (Buyer app, Milestone 3). Same structure as ShopAdapter in Tutorial 3.2:
 *
 *  - ShopViewHolder keeps the views of one card (through View Binding, so no findViewById)
 *  - onCreateViewHolder() inflates item_shop_card.xml (only for the cards visible on screen)
 *  - onBindViewHolder() puts the shop's name, category, address and photo in the card
 *  - onShopClick is called when a card is tapped
 *
 * The photo is downloaded from S3 with ImageLoader (our bucket is private, so it needs our keys).
 */
class ShopAdapter(
    private val scope: CoroutineScope,
    private val onShopClick: (Shop) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    private val shops = mutableListOf<Shop>()

    class ShopViewHolder(val binding: ItemShopCardBinding) : RecyclerView.ViewHolder(binding.root)

    /** Replaces the list and refreshes the RecyclerView. */
    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<Shop>) {
        shops.clear()
        shops.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShopViewHolder(binding)
    }

    override fun getItemCount(): Int = shops.size

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        val shop = shops[position]
        val b = holder.binding

        b.tvShopName.text = shop.shopName
        b.tvCategory.text = shop.shopCategory.ifEmpty { "Shop" }
        b.tvAddress.text = shop.address.ifEmpty { "Sufra partner" }
        b.tvEmoji.text = categoryEmoji(shop.shopCategory)
        ImageLoader.load(b.ivShop, shop.photoUrl, scope)

        b.root.setOnClickListener { onShopClick(shop) }
    }
}
