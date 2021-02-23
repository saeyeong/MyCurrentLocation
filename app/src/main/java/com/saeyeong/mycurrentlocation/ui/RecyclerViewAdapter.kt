package com.saeyeong.mycurrentlocation.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.saeyeong.mycurrentlocation.databinding.ItemWifiBinding
import com.saeyeong.mycurrentlocation.model.Wifi

class RecyclerViewAdapter() : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {
    var wifiList: List<Wifi>? = null

    override fun getItemCount(): Int = wifiList?.size ?: 0

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(wifiList, position);
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWifiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, parent)
    }

    class ViewHolder(private val binding: ItemWifiBinding, parent: ViewGroup) : RecyclerView.ViewHolder(binding.root) {

        fun bind(wifiList: List<Wifi>?, pos: Int) {
            binding.tvSsid.text = wifiList?.get(pos)?.ssid
            binding.tvBssid.text = wifiList?.get(pos)?.bssid
        }
    }

}