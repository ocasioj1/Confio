package com.example.test2.ui.dashboard

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.test2.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSION = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

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

        // Initialize the Bluetooth adapter.
        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Repurpose the button to connect to a bonded ESP32.
        binding.btnScan.text = "Connect To Bonded Device"
        binding.btnScan.setOnClickListener {
            Toast.makeText(requireContext(), "Connecting to bonded Device", Toast.LENGTH_SHORT).show()
            checkPermissionsAndConnect()
        }
    }

    /**
     * Checks the necessary runtime permissions and, if granted, attempts to connect to the ESP32.
     */
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

    /**
     * Looks for a bonded device with the name "MyESP32" and initiates a GATT connection.
     */
    private fun connectToBondedESP32() {
        // Check if Bluetooth is enabled.
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }
        // Get the list of bonded (paired) devices.
        val bondedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (bondedDevices.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No bonded devices found", Toast.LENGTH_SHORT).show()
            return
        }
        // Find the ESP32 device by name.
        val esp32Device = bondedDevices.find { device ->
            device.name == "MyESP32"
        }
        if (esp32Device == null) {
            Toast.makeText(requireContext(), "ESP32 not found among bonded devices", Toast.LENGTH_SHORT).show()
            return
        }
        // Connect using GATT.
        Log.i("BLE", "Attempting to connect to bonded ESP32: ${esp32Device.name} (${esp32Device.address})")
        esp32Device.connectGatt(requireContext(), false, gattCallback)
    }

    /**
     * A basic GATT callback that logs connection events and initiates service discovery.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("BLE", "Connected to ESP32")
                    requireActivity().runOnUiThread {
                        // Update the TextView to display the device name.
                        binding.textDashboard.text = "Connected to: ${gatt?.device?.name ?: "Unnamed Device"}"
                        Toast.makeText(requireContext(), "Connected to ESP32", Toast.LENGTH_SHORT).show()
                    }
                    // Discover services after connecting.
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("BLE", "Disconnected from ESP32")
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
                // Process discovered services and update UI if needed.
            } else {
                Log.e("BLE", "Service discovery failed with status: $status")
            }
        }



    // Implement additional callbacks (e.g., onCharacteristicRead, onCharacteristicWrite, onCharacteristicChanged) as needed.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
