package com.bluetoothseclab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bluetoothseclab.models.BluetoothDeviceInfo

class DevicePagerAdapter(
    private val classicDevices: List<BluetoothDeviceInfo>,
    private val bleDevices: List<BluetoothDeviceInfo>,
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<DevicePagerAdapter.PagerViewHolder>() {

    inner class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val recyclerView: RecyclerView = itemView as RecyclerView
        private var adapter: DeviceListAdapter? = null

        fun bind(isClassic: Boolean) {
            val devices = if (isClassic) classicDevices else bleDevices
            adapter = DeviceListAdapter(devices, onDeviceClick)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(itemView.context)
        }

        fun notifyDataChanged() {
            adapter?.notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
        val recyclerView = RecyclerView(parent.context)
        recyclerView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return PagerViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
        holder.bind(position == 0)
    }

    override fun getItemCount() = 2

    fun notifyPageItemInserted(page: Int) {
        notifyItemChanged(page)
    }
}

class DeviceListAdapter(
    private val devices: List<BluetoothDeviceInfo>,
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val cardAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        private val cardRssi: TextView = itemView.findViewById(R.id.tvDeviceRssi)
        private val cardView: CardView = itemView as CardView

        fun bind(device: BluetoothDeviceInfo) {
            val displayName = if (device.name.isBlank() || device.name == "Unknown") {
                "Unknown Device"
            } else {
                device.name
            }
            cardName.text = displayName
            cardAddress.text = device.address
            cardRssi.text = "${device.rssi}"
            cardView.setOnClickListener { onDeviceClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_card, parent, false) as CardView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size
}
