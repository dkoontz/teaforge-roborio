package teaforge.platform.RoboRio

import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Maybe

fun <TMessage, TModel> timedRobotProgram(program: RoboRioProgram<TMessage, TModel>): RobotBase {
    return TimedRobotBasedPlatform<TMessage, TModel>(program)
}

typealias RoboRioProgram<TMessage, TModel> =
        ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

sealed interface Effect<out TMessage> {
    data class Log(val msg: String) : Effect<Nothing>

    data class PlaySong(val motorMusicPaths: Map<Motor, String>) : Effect<Nothing>

    data object StopSong : Effect<Nothing>

    data class SetPwmMotorSpeed<TMessage>(val pwmSlot: PwmPort, val value: Double) :
        Effect<TMessage>

    data class SetCanMotorSpeed(val motor: Motor, val value: Double) : Effect<Nothing>

    data class ReadFile<TMessage>(
        val path: String,
        val onComplete: (Maybe<ByteArray>) -> TMessage
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
