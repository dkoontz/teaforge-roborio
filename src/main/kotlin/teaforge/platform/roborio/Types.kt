package teaforge.platform.roborio

import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.REVLibError
import com.revrobotics.spark.SparkMax
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.AnalogOutput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DigitalOutput
import edu.wpi.first.wpilibj.motorcontrol.Spark
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

enum class RunningRobotState {
    Disabled,
    Teleop,
    Autonomous,
    Test,
    EStopped,
    Unknown,
}

sealed class Error {
    data class FileNotFound(
        val path: String,
    ) : Error()

    data class FileAccessDenied(
        val path: String,
        val reason: String,
    ) : Error()

    data class InvalidPath(
        val path: String,
        val reason: String,
    ) : Error()

    data class FileReadError(
        val path: String,
        val reason: String,
    ) : Error()

    data object ReadOnlyFileSystem : Error()

    data object AlreadyInitialized : Error()

    data class PhoenixError(
        val canId: Int,
        val status: StatusCode,
    ) : Error()

    data class RevError(
        val canId: Int,
        val error: REVLibError,
    ) : Error()

    data class PortInitializationError(
        val details: String,
    ) : Error()

    data class DigitalPortError(
        val port: DioPort,
        val details: String,
    ) : Error()

    data class AnalogPortError(
        val port: AnalogPort,
        val details: String,
    ) : Error()

    data class PwmPortError(
        val port: PwmPort,
        val details: String,
    ) : Error()

    data class WebSocketInitializationError(
        val uri: String,
        val details: String,
    ) : Error()
}

enum class DioPort {
    Zero,
    One,
    Two,
    Three,
    Four,
    Five,
    Six,
    Seven,
    Eight,
    Nine,
}

enum class AnalogPort {
    Zero,
    One,
    Two,
    Three,
}

enum class PwmPort {
    Zero,
    One,
    Two,
    Three,
    Four,
    Five,
    Six,
    Seven,
    Eight,
    Nine,
}

data class DigitalOutputToken internal constructor(
    val port: DioPort,
    val device: DigitalOutput,
)

data class DigitalInputToken internal constructor(
    val port: DioPort,
    val device: DigitalInput,
)

data class AnalogOutputToken internal constructor(
    val port: AnalogPort,
    val device: AnalogOutput,
)

data class AnalogInputToken internal constructor(
    val port: AnalogPort,
    val device: AnalogInput,
)

data class PwmOutputToken internal constructor(
    val port: PwmPort,
    val device: Spark,
)

data class HidInputToken internal constructor(
    val port: Int,
)

data class OrchestraToken internal constructor(
    val motor: CanDeviceToken.MotorToken.TalonMotorToken,
    val orchestra: com.ctre.phoenix6.Orchestra,
)

data class WebSocketToken internal constructor(
    val url: String,
    val client: HttpClient,
    val session: DefaultClientWebSocketSession,
)

enum class DioPortState {
    HIGH,
    LOW,
}

data class HidValue(
    val axisCount: Int,
    val buttonCount: Int,
    val axisValues: Array<Double>,
    val buttonValues: Array<Boolean>,
)

sealed interface CanDeviceToken {
    sealed interface MotorToken : CanDeviceToken {
        data class NeoMotorToken internal constructor(
            val id: Int,
            val device: SparkMax,
        ) : MotorToken

        data class TalonMotorToken internal constructor(
            val id: Int,
            val device: TalonFX,
        ) : MotorToken
    }

    data class EncoderToken internal constructor(
        val id: Int,
        val device: CANcoder,
    ) : CanDeviceToken

    data class PigeonToken internal constructor(
        val id: Int,
        val device: Pigeon2,
    ) : CanDeviceToken
}

enum class CanDeviceType {
    Neo,
    Talon,
    Encoder,
    Pigeon,
}

sealed interface GamepadButtonState {
    object Pressed : GamepadButtonState

    object Released : GamepadButtonState
}
