package teaforge.platform.RoboRio

import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Result

fun <TMessage, TModel> timedRobotProgram(program: RoboRioProgram<TMessage, TModel>): RobotBase {
    return TimedRobotBasedPlatform<TMessage, TModel>(program)
}

typealias RoboRioProgram<TMessage, TModel> =
        ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

sealed interface Effect<out TMessage> {
    data class Log(val msg: String) : Effect<Nothing>

    data class PlaySong<TMessage>(
        val motor: Motor,
        val songData: ByteArray,
        val message: (Error) -> TMessage
    ) : Effect<TMessage>

    data object StopSong : Effect<Nothing>

    data class SetPwmMotorSpeed<TMessage>(val pwmSlot: PwmPort, val value: Double) :
        Effect<TMessage>

    data class SetCanMotorSpeed(val motor: Motor, val value: Double) : Effect<Nothing>

    data class ReadFile<TMessage>(
        val path: String,
        val message: (Result<ByteArray, Error>) -> TMessage
    ) : Effect<TMessage>

    data class SetDioPort<TMessage>(
        val port: DioPort,
        val value: DioPortVoltage
    ) : Effect<TMessage>


    // all the other effects go here
    //   - send message over CANbus
    //   - send message over I2C
    //   - turn a DIO pin on/off
}

sealed interface Subscription<out TMessage> {
    data class DioPortValue<TMessage>(
        val port: DioPort,
        val millisecondsBetweenReads: Int,
        val message: (DioPortStatus) -> TMessage,
    ) : Subscription<TMessage>

    data class DioPortValueChanged<TMessage>(
        val port: DioPort,
        val message: (DioPortStatus) -> TMessage,
    ) : Subscription<TMessage>

    data class AnalogInputValue<TMessage>(
        val port: AnalogPort,
        val millisecondsBetweenReads: Int,
        val useAverageValue: Boolean,
        val message: (Double) -> TMessage,
    ) : Subscription<TMessage>

    data class HidPortValue<TMessage>(
        val port: Int,
        val message: (HidValue) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotState<TMessage>(
        val message: (RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotStateChanged<TMessage>(
        val message: (RunningRobotState, RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class CANcoderValue<TMessage>(
        val encoder: Encoder,
        val millisecondsBetweenReads: Int,
        val message: (Encoder, Double) -> TMessage
    ) : Subscription<TMessage>

    data class PigeonValue<TMessage>(
        val pigeon: Pigeon,
        val millisecondsBetweenReads: Int,
        val message: (Pigeon, Rotation3d) -> TMessage
    ) : Subscription<TMessage>




}

data class HidValue(
    val axisCount: Int,
    val buttonCount: Int,
    val axisValues: Array<Double>,
    val buttonValues: Array<Boolean>,
)

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

enum class DioPortStatus {
    Open,
    Closed,
}

enum class DioPortVoltage {
    HIGH,
    LOW
}

enum class Motor(val id: Int) {
    FrontLeftDrive(2),
    FrontLeftSteer(3),
    FrontRightDrive(7),
    FrontRightSteer(0),
    BackLeftDrive(6),
    BackLeftSteer(5),
    BackRightDrive(4),
    BackRightSteer(1)
}

enum class Encoder(val id: Int) {
    FrontLeft(8),
    FrontRight(9),
    BackLeft(10),
    BackRight(11)
}

enum class Pigeon(val id: Int) {
    CentralPigeon(3)
}

sealed interface GamepadButtonState {
    object Pressed : GamepadButtonState
    object Released : GamepadButtonState
}

enum class RunningRobotState {
    Disabled,
    Teleop,
    Autonomous,
    Test,
    EStopped
}

sealed class Error {
    data class InvalidFilename(val path: String) : Error()
    data object ReadOnlyFileSystem : Error()

    // TODO: Add pre-defined default error messages for each error
    fun name(): String = this::class.simpleName ?: ""
}