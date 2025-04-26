package com.example.test2.ui.dashboard


import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test2.databinding.FragmentDashboardBinding
import java.util.*
import com.example.test2.ble.BleViewModel



class DashboardFragment : Fragment() {

    private val bleVm: BleViewModel by lazy {
        ViewModelProvider(requireActivity()).get(BleViewModel::class.java)
    }
    private var _binding: FragmentDashboardBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

    // List to hold discovered BLE devices.
    private val deviceList = mutableListOf<BleDevice>()
    private lateinit var deviceAdapter: DeviceAdapter

    // Hold the connected GATT instance once the ESP32 is connected.
    private var connectedGatt: BluetoothGatt? = null

    // UUIDs should match those on your ESP32.
    private val serviceUUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val charUUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        dashboardViewModel.text.observe(viewLifecycleOwner) {
            binding.textDashboard.text = it
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // Initialize Bluetooth adapter.
        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Set up the connect button (repurposed from btnScan) to connect to a bonded ESP32.
        binding.btnScan.text = "Connect Bonded ESP32"
        binding.btnScan.setOnClickListener {
            Toast.makeText(requireContext(), "Connecting to bonded ESP32", Toast.LENGTH_SHORT).show()
            checkPermissionsAndConnect()
        }


    }

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_BLUETOOTH_PERMISSION
                )
                return
            }
        }
        connectToBondedESP32()
    }

    private fun connectToBondedESP32() {
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }
        val bondedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (bondedDevices.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No bonded devices found", Toast.LENGTH_SHORT).show()
            return
        }
        val esp32Device = bondedDevices.find { device -> device.name == "ESP32-S3-LED" }
        if (esp32Device == null) {
            Toast.makeText(requireContext(), "ESP32 not found among bonded devices", Toast.LENGTH_SHORT).show()
            return
        }
        Log.i("BLE", "Attempting to connect to bonded ESP32: ${esp32Device.name} (${esp32Device.address})")
        esp32Device.connectGatt(requireContext(), false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE", "Connected to ESP32")
                    // Save the connected GATT instance.
                    connectedGatt = gatt
                    requireActivity().runOnUiThread {
                        binding.textDashboard.text = "Connected to: ${gatt?.device?.name ?: "Unnamed Device"}"
                        Toast.makeText(requireContext(), "Connected to ESP32", Toast.LENGTH_SHORT).show()
                    }
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("BLE", "Disconnected from ESP32")
                    connectedGatt = null
                    requireActivity().runOnUiThread {
                        binding.textDashboard.text = "Disconnected"
                        Toast.makeText(requireContext(), "Disconnected from ESP32", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BLE", "Services discovered")
                // Services can be inspected here if needed.
            } else {
                Log.e("BLE", "Service discovery failed with status: $status")
            }
        }
    }

    /**
     * Sends a signal (writes the value "1") to the ESP32 by writing to a specific characteristic.
     */
    private fun sendSignalToESP32() {
        val gatt = connectedGatt
        if (gatt == null) {
            Toast.makeText(requireContext(), "Not connected to ESP32", Toast.LENGTH_SHORT).show()
            return
        }
        // Retrieve the desired service.
        val service = gatt.getService(serviceUUID)
        if (service == null) {
            Log.e("BLE", "Service with UUID $serviceUUID not found")
            return
        }
        // Retrieve the writable characteristic.
        val characteristic = service.getCharacteristic(charUUID)
        if (characteristic == null) {
            Log.e("BLE", "Characteristic with UUID $charUUID not found")
            return
        }
        // Prepare the value to be written. For example, "1" to signal the LED to turn on.
        val valueToSend = "LED1"
        characteristic.value = valueToSend.toByteArray(Charsets.UTF_8)
        // Write the characteristic.
        val writeResult = gatt.writeCharacteristic(characteristic)
        Log.i("BLE", "Attempted to write characteristic: result=$writeResult")
    }
    private fun turnOffLed() {
        val gatt = connectedGatt
        if (gatt == null) {
            Toast.makeText(requireContext(), "Not connected to ESP32", Toast.LENGTH_SHORT).show()
            return
        }
        // Retrieve the desired service.
        val service = gatt.getService(serviceUUID)
        if (service == null) {
            Log.e("BLE", "Service with UUID $serviceUUID not found")
            return
        }
        // Retrieve the writable characteristic.
        val characteristic = service.getCharacteristic(charUUID)
        if (characteristic == null) {
            Log.e("BLE", "Characteristic with UUID $charUUID not found")
            return
        }
        // Prepare the value to be written. For example, "1" to signal the LED to turn on.
        val valueToSend = "LED0"
        characteristic.value = valueToSend.toByteArray(Charsets.UTF_8)
        // Write the characteristic.
        val writeResult = gatt.writeCharacteristic(characteristic)
        Log.i("BLE", "Attempted to write characteristic: result=$writeResult")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
