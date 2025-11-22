package teaforge.platform.RoboRio

import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Result

fun <TMessage, TModel> timedRobotProgram(program: RoboRioProgram<TMessage, TModel>): RobotBase =
    TimedRobotBasedPlatform<TMessage, TModel>(program)

typealias RoboRioProgram<TMessage, TModel> =
    ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

sealed interface Effect<out TMessage> {
    data class Log(
        val msg: String,
    ) : Effect<Nothing>

    data class InitDigitalPortForInput<TMessage>(
        val port: DioPort,
        val message: (Result<DigitalInputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitDigitalPortForOutput<TMessage>(
        val port: DioPort,
        val initialValue: DioPortState,
        val message: (Result<DigitalOutputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitAnalogPortForInput<TMessage>(
        val port: AnalogPort,
        val message: (Result<AnalogInputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitAnalogPortForOutput<TMessage>(
        val port: AnalogPort,
        val initialVoltage: Double,
        val message: (Result<AnalogOutputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class SetDigitalPortState<TMessage>(
        val token: DigitalOutputToken,
        val value: DioPortState,
        val message: (Result<DioPort, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class SetAnalogPortVoltage<TMessage>(
        val token: AnalogOutputToken,
        val voltage: Double,
        val message: (Result<AnalogPort, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitPwmPortForOutput<TMessage>(
        val port: PwmPort,
        val initialSpeed: Double,
        val message: (Result<PwmOutputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class SetPwmValue<TMessage>(
        val token: PwmOutputToken,
        val value: Double,
        val message: (Result<PwmPort, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitHidPortForInput<TMessage>(
        val port: Int,
        val message: (Result<HidInputToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitCanDevice<TMessage>(
        val type: CanDeviceType,
        val id: Int,
        val message: (CanDeviceType, Int, Result<CanDeviceToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class SetCanMotorSpeed(
        val motor: CanDeviceToken.MotorToken,
        val value: Double,
    ) : Effect<Nothing>

    data class ReadFile<TMessage>(
        val path: String,
        val message: (Result<ByteArray, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class LoadSong<TMessage>(
        val motor: CanDeviceToken.MotorToken.TalonMotorToken,
        val songData: ByteArray,
        val message: (Result<OrchestraToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class PlaySong<TMessage>(
        val token: OrchestraToken,
        val message: (Result<Unit, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class StopSong<TMessage>(
        val token: OrchestraToken,
        val message: (Result<Unit, Error>) -> TMessage,
    ) : Effect<TMessage>
}

sealed interface Subscription<out TMessage> {
    data class Interval<TMessage>(
        val millisecondsBetweenReads: Int,
        val message: (Long) -> TMessage,
    ) : Subscription<TMessage>

    data class DigitalPortValue<TMessage>(
        val token: DigitalInputToken,
        val millisecondsBetweenReads: Int,
        val message: (DioPortState) -> TMessage,
    ) : Subscription<TMessage>

    data class DigitalPortValueChanged<TMessage>(
        val token: DigitalInputToken,
        val message: (DioPortState, DioPortState) -> TMessage,
    ) : Subscription<TMessage>

    data class AnalogPortValue<TMessage>(
        val token: AnalogInputToken,
        val millisecondsBetweenReads: Int,
        val useAverageValue: Boolean,
        val message: (Double) -> TMessage,
    ) : Subscription<TMessage>

    data class AnalogPortValueChanged<TMessage>(
        val token: AnalogInputToken,
        val useAverageValue: Boolean,
        val message: (Double, Double) -> TMessage,
    ) : Subscription<TMessage>

    data class HidPortValue<TMessage>(
        val token: HidInputToken,
        val message: (HidValue) -> TMessage,
    ) : Subscription<TMessage>

    data class HidPortValueChanged<TMessage>(
        val token: HidInputToken,
        val message: (HidValue, HidValue) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotState<TMessage>(
        val message: (RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotStateChanged<TMessage>(
        val message: (RunningRobotState, RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class CANcoderValue<TMessage>(
        val token: CanDeviceToken.EncoderToken,
        val millisecondsBetweenReads: Int,
        val message: (Double) -> TMessage,
    ) : Subscription<TMessage>

    data class PigeonValue<TMessage>(
        val pigeon: CanDeviceToken.PigeonToken,
        val millisecondsBetweenReads: Int,
        val message: (Rotation3d) -> TMessage,
    ) : Subscription<TMessage>
}
