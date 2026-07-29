package com.katya.app.tools

import android.content.Context
import android.hardware.ConsumerIrManager
import com.katya.app.network.tools.ParameterSchema
import com.katya.app.network.tools.Tool
import com.katya.app.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

object InfraredTool : Tool {

    override val timeout = 5.seconds

    override val schema = ToolSchema(
        name = "transmit_ir",
        description = """Transmit an Infrared (IR) pattern natively on Android.
Params:
- frequency: Carrier frequency in Hertz (e.g. 38000)
- pattern: Comma-separated sequence of alternating on/off durations in microseconds (e.g. '9000,4500,560,560')""",
        parameters = mapOf(
            "frequency" to ParameterSchema("integer", "Carrier frequency in Hz", true),
            "pattern" to ParameterSchema("string", "Comma-separated pattern in microseconds", true),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val frequency = (args["frequency"] as? Number)?.toInt() ?: return mapOf("success" to false, "error" to "Missing or invalid 'frequency'")
        val patternStr = args["pattern"] as? String ?: return mapOf("success" to false, "error" to "Missing 'pattern'")

        val pattern = try {
            patternStr.split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "Failed to parse pattern. Must be comma-separated integers.")
        }

        val context: Context by inject(Context::class.java)
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        if (irManager == null || !irManager.hasIrEmitter()) {
            return mapOf("success" to false, "error" to "IR Emitter not found on this device")
        }

        return try {
            irManager.transmit(frequency, pattern)
            mapOf("success" to true)
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to transmit IR signal"))
        }
    }
}
