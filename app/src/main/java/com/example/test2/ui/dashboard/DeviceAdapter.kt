// File: DeviceAdapter.kt
package com.example.test2.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test2.databinding.ItemDeviceBinding

class DeviceAdapter(private val devices: MutableList<BleDevice>) :
    RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.binding.deviceName.text = device.name ?: "Unnamed Device"
        holder.binding.deviceAddress.text = device.address
    }

    override fun getItemCount(): Int = devices.size

    // Add a new device to the list if not already present.
    fun addDevice(newDevice: BleDevice) {
        if (devices.none { it.address == newDevice.address }) {
            devices.add(newDevice)
            notifyItemInserted(devices.size - 1)
        }
    }
}
