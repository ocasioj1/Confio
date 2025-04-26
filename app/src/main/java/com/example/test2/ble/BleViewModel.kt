package com.example.test2.ble  // or .viewmodel—match your folder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic


// This is created to share the GATT connection between each of the fragments
// Without it, the connection is only maintained on the fragment you initialize it on
class BleViewModel : ViewModel() {
    // Hold your live GATT connection
    var gatt: BluetoothGatt? = null

    // for observing humidity changes
    val latestHumidity = MutableLiveData<String>()

    // Hold the characteristic that you write commands to
    var controlChar: BluetoothGattCharacteristic? = null

    // Optional: a LiveData to track connection state
    private val _connected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _connected

    // Call this from your GATT callbacks:
    fun setConnected(yes: Boolean) {
        _connected.value = yes
    }
}