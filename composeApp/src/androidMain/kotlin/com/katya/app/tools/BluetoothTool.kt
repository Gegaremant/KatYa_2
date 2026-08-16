package com.katya.app.tools

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

object BluetoothTool : Tool {

    override val timeout = 10.seconds

    override val schema = ToolSchema(
        name = "manage_bluetooth",
        description = """Manage Bluetooth devices natively on Android.
Actions:
- list_paired: Show all paired Bluetooth devices""",
        parameters = mapOf(
            "action" to ParameterSchema("string", "Action to perform: list_paired", true),
        ),
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"] as? String ?: return mapOf("success" to false, "error" to "Missing 'action' parameter")
        val context: Context by inject(Context::class.java)

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            return mapOf("success" to false, "error" to "Bluetooth is not supported on this device")
        }

        if (!bluetoothAdapter.isEnabled) {
            return mapOf("success" to false, "error" to "Bluetooth is turned off")
        }

        return try {
            when (action) {
                "list_paired" -> {
                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
                    val devicesList = pairedDevices?.map { device ->
                        mapOf(
                            "name" to (device.name ?: "Unknown"),
                            "address" to device.address,
                            "type" to device.type,
                        )
                    } ?: emptyList()
                    mapOf("success" to true, "devices" to devicesList)
                }

                else -> mapOf("success" to false, "error" to "Unknown action: $action")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
}
