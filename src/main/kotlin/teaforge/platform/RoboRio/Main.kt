package teaforge.platform.RoboRio

import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Maybe
import teaforge.utils.Result
import java.util.*

fun <TMessage, TModel> timedRobotProgram(program: RoboRioProgram<TMessage, TModel>): RobotBase {
    return TimedRobotBasedPlatform<TMessage, TModel>(program)
}

typealias RoboRioProgram<TMessage, TModel> =
        ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

sealed interface Effect<out TMessage> {
    data class Log(val msg: String) : Effect<Nothing>

    data class LoadSong<TMessage>(
        val motor: Motor,
        val songData: ByteArray,
        val message: (Motor, Maybe<Error>) -> TMessage
    ) : Effect<TMessage>

    data class PlaySong<TMessage>(
        val motor: Motor,
        val message: (Motor, Maybe<Error>) -> TMessage
    ) : Effect<TMessage>

    data class StopSong<TMessage>(
        val motor: Motor,
        val message: (Motor, Maybe<Error>) -> TMessage
    ) : Effect<TMessage>

    data class SetPwmMotorSpeed<TMessage>(val pwmSlot: PwmPort, val value: Double) :
        Effect<TMessage>

    data class SetCanMotorSpeed(val motor: Motor, val value: Double) : Effect<Nothing>

    data class ReadFile<TMessage>(
        val path: String,
        val message: (Result<ByteArray, Error>) -> TMessage
    ) : Effect<TMessage>

    data class SetDioPort<TMessage>(
        val port: DioPort,
        val value: DioPortState,
        val message: (Result<DioPort, Error>) -> TMessage
    ) : Effect<TMessage>


    // all the other effects go here
    //   - send message over CANbus
    //   - send message over I2C
    //   - turn a DIO pin on/off
}

sealed interface Subscription<out TMessage> {
    data class Interval<TMessage>(
        val millisecondsBetweenReads: Int,
        val message: (Long) -> TMessage,
    ) : Subscription<TMessage>

    data class DioPortValue<TMessage>(
        val port: DioPort,
        val millisecondsBetweenReads: Int,
        val onInit: (Result<DioPort, Error>) -> TMessage,
        val onRead: (DioPortStatus) -> TMessage,
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

enum class DioPort(val id: Int) {
    Zero(0),
    One(1),
    Two(2),
    Three(3),
    Four(4),
    Five(5),
    Six(6),
    Seven(7),
    Eight(8),
    Nine(9),
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

enum class DioPortState {
    HIGH,
    LOW
}

enum class Motor(val id: Int) {
    FrontLeftDrive(4),
    FrontLeftSteer(1),
    FrontRightDrive(6),
    FrontRightSteer(5),
    BackLeftDrive(7),
    BackLeftSteer(0),
    BackRightDrive(2),
    BackRightSteer(3)
}

enum class Encoder(val id: Int) {
    FrontLeft(9),
    FrontRight(10),
    BackLeft(11),
    BackRight(8)
}

enum class Pigeon(val id: Int) {
    CentralPigeon(12)
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
    EStopped,
    Unknown
}

sealed class Error {
    data class InvalidFilename(val path: String) : Error()
    data object ReadOnlyFileSystem : Error()
    data object AlreadyInitialized : Error()
    data object SongNotLoaded : Error()
    data object SongNotPlaying : Error()
    data class PhoenixError(val details: String) : Error()

    // TODO: Add pre-defined default error messages for each error
    fun name(): String = this::class.simpleName ?: ""
}