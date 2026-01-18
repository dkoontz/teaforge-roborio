package teaforge.platform.RoboRio

import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.spark.SparkMax
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.Subscription.*
import teaforge.platform.RoboRio.internal.SubscriptionState
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Maybe
import teaforge.utils.Result
import java.util.concurrent.CountDownLatch


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

    sealed interface InitCanDevice<TMessage>  : Effect<TMessage> {
        sealed interface InitMotor<TMessage> : InitCanDevice<TMessage> {
            data class Neo<TMessage>(
                val id: Int,
                val message: (Int, Result<CanDeviceToken, Error>) -> TMessage
            ) : InitMotor<TMessage>

            data class Talon<TMessage>(
                val id: Int,
                val message: (Int, Result<CanDeviceToken, Error>) -> TMessage
            ) : InitMotor<TMessage>
        }

        data class Encoder<TMessage>(
            val id: Int,
            val message: ( Int, Result<CanDeviceToken, Error>) -> TMessage
        ) : InitCanDevice<TMessage>

        data class Pigeon<TMessage>(
            val id: Int,
            val message: ( Int, Result<CanDeviceToken, Error>) -> TMessage
        ) : InitCanDevice<TMessage>
    }

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

    data class ForwardPort<TMessage>(
        val port: UShort,
        val remoteName: String,
        val remotePort: UShort,
        val message: (Result<UShort, Error>) -> TMessage,
    ) : Effect<TMessage>
}

sealed interface Subscription<out TMessage> {
    data class Interval<TMessage>(
        val millisecondsBetweenReads: Int,
        val message: (Long) -> TMessage,
    ) : Subscription<TMessage>

    data class WebSocket<TMessage>(
        val url: String,
        val message: (String) -> TMessage
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

/**
 * Maps the message type of an Effect to a new message type.
 *
 * This function allows developers to create sub-components with their own message types
 * and convert them to a unified application message type by wrapping the message from the component inside
 * a top level message that has an argument for the component specific message.
 *
 * Example:
 * ```
 * // Component-level message type
 * sealed interface DrivetrainMsg {
 *     data class MotorInitialized(val result: Result<CanDeviceToken, Error>) : DrivetrainMsg
 *     object TargetSpeedReached() : DrivetrainMsg
 *     data class WheelAlignment(val angle: Double) : DrivetrainMsg
 * }
 *
 * // Top-level application message type
 * sealed interface Msg {
 *     data class Autonomous(val message: AutonomousMsg) : Msg
 *     data class Drivetrain(val message: DrivetrainMsg) : Msg
 *     data class Vision(val message: VisionMsg) : Msg
 * }
 *
 * // Component function returning Effect<DrivetrainMsg>
 * fun initDrivetrain(): Effect<DrivetrainMsg> {
 *     return Effect.InitCanDevice(
 *         type = CanDeviceType.Talon,
 *         id = 1,
 *         message = { canDeviceType, id, result -> DrivetrainMsg.MotorInitialized(result) }
 *     )
 * }
 *
 * // In the top-level update function, map the component-specific message to the top level application Msg type
 * val drivetrainEffect: Effect<DrivetrainMsg> = initDrivetrain()
 * val appEffect: Effect<Msg> = mapEffect(drivetrainEffect, Msg::Drivetrain)
 * ```
 * Msg::Drivetrain is of type `DrivetrainMsg -> Msg` so this converts the component specific `DrivetrainMsg` into the
 * application level `Msg` type which is what the `update` function returns.
 *
 * @param effect The effect to convert
 * @param mapFunction A function that converts the original message type to the new message type
 * @return A new Effect with the transformed message type
 */
fun <TMessage, TNewMessage> mapEffect(
    effect: Effect<TMessage>,
    mapFunction: (TMessage) -> TNewMessage,
): Effect<TNewMessage> =
    when (effect) {
        is Effect.Log -> effect
        is Effect.InitDigitalPortForInput ->
            Effect.InitDigitalPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.InitDigitalPortForOutput ->
            Effect.InitDigitalPortForOutput(
                port = effect.port,
                initialValue = effect.initialValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.InitAnalogPortForInput ->
            Effect.InitAnalogPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.InitAnalogPortForOutput ->
            Effect.InitAnalogPortForOutput(
                port = effect.port,
                initialVoltage = effect.initialVoltage,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.SetDigitalPortState ->
            Effect.SetDigitalPortState(
                token = effect.token,
                value = effect.value,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.SetAnalogPortVoltage ->
            Effect.SetAnalogPortVoltage(
                token = effect.token,
                voltage = effect.voltage,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.InitPwmPortForOutput ->
            Effect.InitPwmPortForOutput(
                port = effect.port,
                initialSpeed = effect.initialSpeed,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.SetPwmValue ->
            Effect.SetPwmValue(
                token = effect.token,
                value = effect.value,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.InitHidPortForInput ->
            Effect.InitHidPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )

        is Effect.InitCanDevice.InitMotor.Talon ->
            Effect.InitCanDevice.InitMotor.Talon(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        is Effect.InitCanDevice.InitMotor.Neo ->
            Effect.InitCanDevice.InitMotor.Neo(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) }
            )
        is Effect.InitCanDevice.Encoder ->
            Effect.InitCanDevice.Encoder(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) }
            )
        is Effect.InitCanDevice.Pigeon ->
            Effect.InitCanDevice.Pigeon(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message( deviceId, result)) }
            )

        is Effect.SetCanMotorSpeed -> effect
        is Effect.ReadFile ->
            Effect.ReadFile(
                path = effect.path,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.LoadSong ->
            Effect.LoadSong(
                motor = effect.motor,
                songData = effect.songData,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.PlaySong ->
            Effect.PlaySong(
                token = effect.token,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.StopSong ->
            Effect.StopSong(
                token = effect.token,
                message = { result -> mapFunction(effect.message(result)) },
            )
        is Effect.ForwardPort ->
            Effect.ForwardPort(
                port = effect.port,
                remoteName = effect.remoteName,
                remotePort = effect.remotePort,
                message = { result -> mapFunction(effect.message(result)) }
            )
    }

/**
 * Maps the message type of a Subscription to a new message type.
 *
 * This function allows developers to create sub-components with their own message types
 * and convert them to a unified application message type by wrapping the message from the component inside
 * a top level message that has an argument for the component specific message.
 *
 * Example:
 * ```
 * // Component-level message type
 * sealed interface DrivetrainMsg {
 *     data class FrontLeftWheelDirection(val angle: Double) : DrivetrainMsg
 *     data class LeftSideBumper(val state: DioPortState) : DrivetrainMsg
 * }
 *
 * // Top-level application message type
 * sealed interface Msg {
 *     data class Autonomous(val message: AutonomousMsg) : Msg
 *     data class Drivetrain(val message: DrivetrainMsg) : Msg
 *     data class Vision(val message: VisionMsg) : Msg
 * }
 *
 * // Component function returning Subscription<DrivetrainMsg>
 * fun drivetrainSubscriptions(encoderToken: CanDeviceToken.EncoderToken): Subscription<DrivetrainMsg> {
 *     return Subscription.CANcoderValue(
 *         token = encoderToken,
 *         millisecondsBetweenReads = 20,
 *         message = DrivetrainMsg::FrontLeftWheelDirection
 *     )
 * }
 *
 * // In the top-level subscriptions function, map the component-specific message to the top level application Msg type
 * val drivetrainSub: Subscription<DrivetrainMsg> = drivetrainSubscriptions(encoder)
 * val appSub: Subscription<Msg> = mapSubscription(drivetrainSub, Msg::Drivetrain)
 * ```
 * Msg::Drivetrain is of type `DrivetrainMsg -> Msg` so this converts the component specific `DrivetrainMsg` into the
 * application level `Msg` type which is what the `subscription` function returns.
 *
 * @param subscription The subscription to convert
 * @param mapFunction A function that converts the original message type to the new message type
 * @return A new Subscription with the transformed message type
 */
fun <TMessage, TNewMessage> mapSubscription(
    subscription: Subscription<TMessage>,
    mapFunction: (TMessage) -> TNewMessage,
): Subscription<TNewMessage> =
    when (subscription) {
        is Interval ->
            Interval(
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { elapsed -> mapFunction(subscription.message(elapsed)) },
            )
        is DigitalPortValue ->
            DigitalPortValue(
                token = subscription.token,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        is DigitalPortValueChanged ->
            DigitalPortValueChanged(
                token = subscription.token,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        is AnalogPortValue ->
            AnalogPortValue(
                token = subscription.token,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                useAverageValue = subscription.useAverageValue,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        is AnalogPortValueChanged ->
            AnalogPortValueChanged(
                token = subscription.token,
                useAverageValue = subscription.useAverageValue,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        is HidPortValue ->
            HidPortValue(
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        is HidPortValueChanged ->
            HidPortValueChanged(
                token = subscription.token,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        is RobotState ->
            RobotState(
                message = { state -> mapFunction(subscription.message(state)) },
            )
        is RobotStateChanged ->
            RobotStateChanged(
                message = { oldState, newState -> mapFunction(subscription.message(oldState, newState)) },
            )
        is CANcoderValue ->
            CANcoderValue(
                token = subscription.token,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        is PigeonValue ->
            PigeonValue(
                pigeon = subscription.pigeon,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { rotation -> mapFunction(subscription.message(rotation)) },
            )
        is WebSocket -> {
            WebSocket(
                url = subscription.url,
                message = { info -> mapFunction(subscription.message(info)) }
            )
        }
         }
