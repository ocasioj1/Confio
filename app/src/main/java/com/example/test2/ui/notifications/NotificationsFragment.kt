package com.example.test2.ui.notifications

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.test2.databinding.FragmentNotificationsBinding
import com.example.test2.ble.BleViewModel
import com.example.test2.ui.dashboard.BleDevice
import com.example.test2.ui.dashboard.DeviceAdapter
import java.util.UUID


class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
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

    // UUIDs should match those on the ESP32.
    private val serviceUUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val charUUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    // UUID for the humidity characteristic on the ESP32
    private val humidUUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
    private val tempUUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root



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
        // The following binds the functions to the buttons on screen


        binding.btnHum.setOnClickListener {
            val gatt = connectedGatt
            if (gatt == null) {
                Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val service = gatt.getService(serviceUUID)
            if (service == null) {
                Toast.makeText(requireContext(), "Service not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val humidChar = service.getCharacteristic(humidUUID)
            if (humidChar == null) {
                Toast.makeText(requireContext(), "Humidity char not found", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("BLE", "Requesting humidity read")
                gatt.readCharacteristic(humidChar)
            }
        }
        binding.btnTemp.setOnClickListener {
            val gatt = connectedGatt
            if (gatt == null) {
                Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val service = gatt.getService(serviceUUID)
            if (service == null) {
                Toast.makeText(requireContext(), "Service not found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val tempChar = service.getCharacteristic(tempUUID)
            if (tempChar == null) {
                Toast.makeText(requireContext(), "Temperature char not found", Toast.LENGTH_SHORT).show()
            } else {
                Log.i("BLE", "Requesting temperature read")
                gatt.readCharacteristic(tempChar)
            }
        }
        binding.btnSend.setOnClickListener {
            sendSignalToESP32()
        }
        binding.btnTurnoff.setOnClickListener {
            turnOffLed()
        }

        // 1) Calibrate Temperature
        binding.btnCalTemp.setOnClickListener {
            val txt = binding.etTempCal.text.toString().trim()
            if (txt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter temp in °C", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendCalibration("CT:$txt", "Temp calibrated to $txt°C")
        }

        // 2) Calibrate Humidity
        binding.btnCalHum.setOnClickListener {
            val txt = binding.etHumCal.text.toString().trim()
            if (txt.isEmpty()) {
                Toast.makeText(requireContext(), "Enter humidity %", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendCalibration("CH:$txt", "Humidity calibrated to $txt%")
        }
    }


    /** Helper to write a calibration or control command */
    private fun sendCalibration(command: String, toastMsg: String) {
        val gatt = connectedGatt
        if (gatt == null) {
            Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show()
            return
        }
        val service = gatt.getService(serviceUUID)
        if (service == null) {
            Toast.makeText(requireContext(), "Service not found", Toast.LENGTH_SHORT).show()
            return
        }
        val char = service.getCharacteristic(charUUID)
        if (char == null) {
            Toast.makeText(requireContext(), "Control characteristic not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Send without waiting for response
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        char.value = command.toByteArray(Charsets.UTF_8)
        if (gatt.writeCharacteristic(char)) {
            Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
            Log.i("BLE", "$toastMsg → $command")
        } else {
            Toast.makeText(requireContext(), "Write failed", Toast.LENGTH_SHORT).show()
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
                        binding.textView2.text = "Connected to: ${gatt?.device?.name ?: "Unnamed Device"}"
                        Toast.makeText(requireContext(), "Connected to ESP32", Toast.LENGTH_SHORT).show()
                    }
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("BLE", "Disconnected from ESP32")
                    connectedGatt = null
                    requireActivity().runOnUiThread {
                        binding.textView2.text = "Disconnected"
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

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                when (characteristic.uuid) {
                    humidUUID -> {
                        // assumes your Arduino sends ASCII digits, e.g. "45.23"
                        val humidity = String(characteristic.value, Charsets.UTF_8)
                        Log.i("BLE", "Humidity read: $humidity")
                        requireActivity().runOnUiThread {
                            binding.HumData.text = "Humidity: $humidity %"
                            Toast.makeText(requireContext(), "Humidity: $humidity%", Toast.LENGTH_SHORT).show()
                        }
                    }
                    tempUUID -> {
                        // assumes your Arduino sends ASCII digits, e.g. "45.23"
                        val temperature = String(characteristic.value, Charsets.UTF_8)
                        Log.i("BLE", "Temperature read: $temperature")
                        requireActivity().runOnUiThread {
                            binding.TempData.text = "Temperature (F): $temperature %"
                            Toast.makeText(requireContext(), "Temperature: $temperature%", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // you could handle other characteristic UUIDs here
                }
            } else {
                Log.e("BLE", "Characteristic read failed, status $status")
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