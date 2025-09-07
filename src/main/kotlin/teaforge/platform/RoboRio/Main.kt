package teaforge.platform.RoboRio

import edu.wpi.first.wpilibj.RobotBase
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform

fun <TMessage, TModel> timedRobotProgram(program: RoboRioProgram<TMessage, TModel>): RobotBase {
    return TimedRobotBasedPlatform<TMessage, TModel>(program)
}

typealias RoboRioProgram<TMessage, TModel> =
        teaforge.ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

sealed interface Effect<out TMessage> {
    data class Log(val msg: String) : Effect<Nothing>

    data class SetPwmMotorSpeed<TMessage>(val pwmSlot: PwmPort, val value: Double) :
            Effect<TMessage>

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
}

data class HidValue(
        val axisCount: Int,
        val buttonCount: Int,
        val axisValues: Array<Double>,
        val buttonValues: Array<Boolean>,
)

data class Ps5ControllerValue(
        val crossButton: GamepadButtonState,
        val squareButton: GamepadButtonState,
        val triangleButton: GamepadButtonState,
        val circleButton: GamepadButtonState,
        val rightShoulderButton: GamepadButtonState,
        val leftShoulderButton: GamepadButtonState,
        val leftStickDown: GamepadButtonState,
        val rightStickDown: GamepadButtonState,
        val playstationButton: GamepadButtonState,
        val leftStickHorizontal: Double,
        val leftStickVertical: Double,
        val rightStickHorizontal: Double,
        val rightStickVertical: Double,
        val rightShoulderTrigger: Double,
        val leftShoulderTrigger: Double,
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

sealed interface GamepadButtonState {
    object Pressed : GamepadButtonState
    object Released : GamepadButtonState
}
