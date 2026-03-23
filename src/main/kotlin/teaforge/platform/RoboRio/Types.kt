package teaforge.platform.RoboRio

import com.ctre.phoenix6.CANBus
import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.Timestamp
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.CANrange
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.REVLibError
import com.revrobotics.spark.SparkMax
import edu.wpi.first.networktables.BooleanArrayPublisher
import edu.wpi.first.networktables.BooleanArraySubscriber
import edu.wpi.first.networktables.BooleanPublisher
import edu.wpi.first.networktables.BooleanSubscriber
import edu.wpi.first.networktables.DoubleArrayPublisher
import edu.wpi.first.networktables.DoubleArraySubscriber
import edu.wpi.first.networktables.DoublePublisher
import edu.wpi.first.networktables.DoubleSubscriber
import edu.wpi.first.networktables.IntegerArrayPublisher
import edu.wpi.first.networktables.IntegerArraySubscriber
import edu.wpi.first.networktables.IntegerPublisher
import edu.wpi.first.networktables.IntegerSubscriber
import edu.wpi.first.networktables.NetworkTable
import edu.wpi.first.networktables.StringArrayPublisher
import edu.wpi.first.networktables.StringArraySubscriber
import edu.wpi.first.networktables.StringPublisher
import edu.wpi.first.networktables.StringSubscriber
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.AnalogOutput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DigitalOutput
import edu.wpi.first.wpilibj.Servo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import org.zeromq.ZContext
import org.zeromq.ZMQ

enum class RunningRobotState {
    Disabled,
    Teleop,
    Autonomous,
    Test,
    EStopped,
    Unknown,
}

sealed interface Error {
    sealed interface PhoenixError : Error {
        data class PhoenixInitializationError(
            val canId: Int,
            val status: StatusCode,
        ) : Error

        data class PhoenixDeviceError(
            val canDeviceToken: CanDeviceToken,
            val status: StatusCode,
        ) : Error
    }

    data class FileNotFound(
        val path: String,
    ) : Error

    data class FileAccessDenied(
        val path: String,
        val reason: String,
    ) : Error

    data class InvalidPath(
        val path: String,
        val reason: String,
    ) : Error

    data class FileReadError(
        val path: String,
        val reason: String,
    ) : Error

    data object ReadOnlyFileSystem : Error

    data object AlreadyInitialized : Error

    data class RevError(
        val canId: Int,
        val error: REVLibError,
    ) : Error

    data class PortInitializationError(
        val details: String,
    ) : Error

    data class DigitalPortError(
        val port: DioPort,
        val details: String,
    ) : Error

    data class AnalogPortError(
        val port: AnalogPort,
        val details: String,
    ) : Error

    data class PwmPortError(
        val port: PwmPort,
        val details: String,
    ) : Error

    data class WebSocketInitializationError(
        val uri: String,
        val details: String,
    ) : Error

    data class TCPClientInitError(
        val details: String,
    ) : Error

    data class CanBusInitError(
        val details: String,
    ) : Error

    data class CanBusError(
        val details: String,
    ) : Error

    data class NetworkTableError(
        val details: String,
    ) : Error
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

data class CanBusToken internal constructor(
    val bus: CANBus,
)

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
    val device: Servo,
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

data class NetworkTableToken internal constructor(
    val name: String,
    val table: NetworkTable,
)

sealed interface NetworkTablePublisherToken {
    data class DoublePublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: DoublePublisher,
    ) : NetworkTablePublisherToken

    data class StringPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: StringPublisher,
    ) : NetworkTablePublisherToken

    data class IntegerPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: IntegerPublisher,
    ) : NetworkTablePublisherToken

    data class BooleanPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: BooleanPublisher,
    ) : NetworkTablePublisherToken

    data class DoubleArrayPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: DoubleArrayPublisher,
    ) : NetworkTablePublisherToken

    data class StringArrayPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: StringArrayPublisher,
    ) : NetworkTablePublisherToken

    data class IntegerArrayPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: IntegerArrayPublisher,
    ) : NetworkTablePublisherToken

    data class BooleanArrayPublisherToken internal constructor(
        val tableName: String,
        val topicName: String,
        val publisher: BooleanArrayPublisher,
    ) : NetworkTablePublisherToken
}

sealed interface NetworkTableSubscriberToken {
    data class DoubleSubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: DoubleSubscriber,
    ) : NetworkTableSubscriberToken

    data class StringSubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: StringSubscriber,
    ) : NetworkTableSubscriberToken

    data class IntegerSubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: IntegerSubscriber,
    ) : NetworkTableSubscriberToken

    data class BooleanSubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: BooleanSubscriber,
    ) : NetworkTableSubscriberToken

    data class DoubleArraySubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: DoubleArraySubscriber,
    ) : NetworkTableSubscriberToken

    data class StringArraySubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: StringArraySubscriber,
    ) : NetworkTableSubscriberToken

    data class IntegerArraySubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: IntegerArraySubscriber,
    ) : NetworkTableSubscriberToken

    data class BooleanArraySubscriberToken internal constructor(
        val tableName: String,
        val topicName: String,
        val subscriber: BooleanArraySubscriber,
    ) : NetworkTableSubscriberToken
}

sealed interface TCPToken
internal data class TCPTokenImplementation(
    val context: ZContext,
    val socket: ZMQ.Socket,
) : TCPToken

enum class DioPortState {
    HIGH,
    LOW,
}

data class HidValue(
    val axisCount: Int,
    val buttonCount: Int,
    val povCount: Int,
    val axisValues: Array<Double>,
    val buttonValues: Array<Boolean>,
    val povValues: Array<Int>,
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

    data class CANRangeToken internal constructor(
        val id: Int,
        val device: CANrange,
    ) : CanDeviceToken
}

enum class CanDeviceType {
    Neo,
    Talon,
    Encoder,
    Pigeon,
    Range,
}

sealed interface GamepadButtonState {
    object Pressed : GamepadButtonState

    object Released : GamepadButtonState
}

data class SignalValue<T>(
    val value: T,
    val timestamp: Timestamp,
    val status: StatusCode,
)

sealed interface CanDeviceSnapshot {
    data class TalonSnapshot(
        val position: SignalValue<Double>,
        val velocity: SignalValue<Double>,
        val motorVoltage: SignalValue<Double>,
    ) : CanDeviceSnapshot

    data class EncoderSnapshot(
        val absolutePos: SignalValue<Double>,
        val relativePos: SignalValue<Double>,
        val velocity: SignalValue<Double>,
    ) : CanDeviceSnapshot

    data class CanRangeSnapshot(
        val distance: SignalValue<Double>,
    ) : CanDeviceSnapshot

    data class PigeonSnapshot(
        // rotations per second
        val yawRate: SignalValue<Double>,
        // rotations per second
        val pitchRate: SignalValue<Double>,
        // rotations per second
        val rollRate: SignalValue<Double>,
        // rotations, does not wrap around
        val yaw: SignalValue<Double>,
        // rotations, does not wrap around
        val pitch: SignalValue<Double>,
        // rotations, does not wrap around
        val roll: SignalValue<Double>,
    ) : CanDeviceSnapshot
}

data class MotorConfig(
    // Current motor is allowed to continuously draw
    val currentLimit: Double,
    // How high current is allowed to spike
    val lowerLimit: Double,
    // How long motor is allowed to exceed lower limit
    val lowerLimitTime: Double,
    // Acceleration limit
    val ramp: Double,
)
