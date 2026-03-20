package teaforge.platform.RoboRio

import com.ctre.phoenix6.configs.CANcoderConfiguration
import com.ctre.phoenix6.configs.Pigeon2Configuration
import com.ctre.phoenix6.configs.TalonFXConfiguration
import edu.wpi.first.wpilibj.RobotBase
import edu.wpi.first.wpilibj.SerialPort
import teaforge.ProgramConfig
import teaforge.SubscriptionIdentifier
import teaforge.platform.RoboRio.Subscription.AnalogPortValue
import teaforge.platform.RoboRio.Subscription.AnalogPortValueChanged
import teaforge.platform.RoboRio.Subscription.CANcoderValue
import teaforge.platform.RoboRio.Subscription.DigitalPortValue
import teaforge.platform.RoboRio.Subscription.DigitalPortValueChanged
import teaforge.platform.RoboRio.Subscription.HidPortValue
import teaforge.platform.RoboRio.Subscription.HidPortValueChanged
import teaforge.platform.RoboRio.Subscription.Interval
import teaforge.platform.RoboRio.Subscription.PigeonValue
import teaforge.platform.RoboRio.Subscription.RobotState
import teaforge.platform.RoboRio.Subscription.RobotStateChanged
import teaforge.platform.RoboRio.Subscription.SerialValue
import teaforge.platform.RoboRio.Subscription.TalonValue
import teaforge.platform.RoboRio.Subscription.WebSocket
import teaforge.platform.RoboRio.internal.TimedRobotBasedPlatform
import teaforge.utils.Result

fun <TMessage, TModel> timedRobotProgram(config: RoboRioProgramConfig<TMessage, TModel>): RobotBase =
    TimedRobotBasedPlatform<TMessage, TModel>(config)

typealias RoboRioProgram<TMessage, TModel> =
    ProgramConfig<Effect<TMessage>, TMessage, TModel, Subscription<TMessage>>

data class RoboRioProgramConfig<TMessage, TModel>(
    val program: RoboRioProgram<TMessage, TModel>,
    val debugLogging: DebugLogging,
)

sealed interface DebugLogging {
    data object Disabled : DebugLogging

    data class Enabled(
        val compression: Boolean,
        val logFile: LogFile,
    ) : DebugLogging
}

sealed interface LogFile {
    data object Default : LogFile

    data class Path(
        val path: String,
    ) : LogFile
}

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

    data class InitWebSocket<TMessage>(
        val url: String,
        val message: (Result<WebSocketToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    data class InitTCPClient<TMessage>(
        val host: String,
        val port: UShort,
        val topic: String,
        val message: (Result<TCPToken, Error>) -> TMessage,
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
        val minBoundMicroseconds: Int = 1000,
        val maxBoundMicroseconds: Int = 2000,
        val deadbandPercent: Double = 0.05,
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

    sealed interface InitCanDevice<TMessage> : Effect<TMessage> {
        sealed interface InitMotor<TMessage> : InitCanDevice<TMessage> {
            data class Neo<TMessage>(
                val id: Int,
                val message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
            ) : InitMotor<TMessage>

            data class Talon<TMessage>(
                val id: Int,
                val message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
            ) : InitMotor<TMessage>
        }

        data class Encoder<TMessage>(
            val id: Int,
            val message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
        ) : InitCanDevice<TMessage>

        data class Pigeon<TMessage>(
            val id: Int,
            val message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
        ) : InitCanDevice<TMessage>

        data class Range<TMessage>(
            val id: Int,
            val message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
        ) : InitCanDevice<TMessage>
    }

    sealed interface ConfigCanDevice<TMessage> : Effect<TMessage> {
        data class Talon<TMessage>(
            val talon: CanDeviceToken.MotorToken.TalonMotorToken,
            val config: TalonFXConfiguration,
            val message: (Result<CanDeviceToken.MotorToken.TalonMotorToken, Error>) -> TMessage,
        ) : ConfigCanDevice<TMessage>

        data class Encoder<TMessage>(
            val cancoder: CanDeviceToken.EncoderToken,
            val config: CANcoderConfiguration,
            val message: (Result<CanDeviceToken.EncoderToken, Error>) -> TMessage,
        ) : ConfigCanDevice<TMessage>

        data class Pigeon<TMessage>(
            val pigeon: CanDeviceToken.PigeonToken,
            val config: Pigeon2Configuration,
            val message: (Result<CanDeviceToken.PigeonToken, Error>) -> TMessage,
        ) : ConfigCanDevice<TMessage>
    }

    // refactor to set voltage
    data class SetCanMotorSpeed(
        val motor: CanDeviceToken.MotorToken,
        val value: Double,
    ) : Effect<Nothing>

    data class SetTalonVoltage(
        val talon: CanDeviceToken.MotorToken.TalonMotorToken,
        val voltage: Double,
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

    data class RunAsync<TMessage, TOutput>(
        val function: () -> TOutput,
        val message: (TOutput) -> TMessage,
    ) : Effect<TMessage>

    data class InitNetworkTable<TMessage>(
        val name: String,
        val message: (Result<NetworkTableToken, Error>) -> TMessage,
    ) : Effect<TMessage>

    sealed interface InitNetworkTablePublisher<TMessage> : Effect<TMessage> {
        data class Double<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.DoublePublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class String<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.StringPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class Integer<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.IntegerPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class Boolean<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.BooleanPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class DoubleArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.DoubleArrayPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class StringArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.StringArrayPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class IntegerArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.IntegerArrayPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>

        data class BooleanArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val message: (Result<NetworkTablePublisherToken.BooleanArrayPublisherToken, Error>) -> TMessage,
        ) : InitNetworkTablePublisher<TMessage>
    }

    sealed interface PublishToNetworkTable : Effect<Nothing> {
        data class Double(
            val publisher: NetworkTablePublisherToken.DoublePublisherToken,
            val value: kotlin.Double,
        ) : PublishToNetworkTable

        data class String(
            val publisher: NetworkTablePublisherToken.StringPublisherToken,
            val value: kotlin.String,
        ) : PublishToNetworkTable

        data class Integer(
            val publisher: NetworkTablePublisherToken.IntegerPublisherToken,
            val value: Long,
        ) : PublishToNetworkTable

        data class Boolean(
            val publisher: NetworkTablePublisherToken.BooleanPublisherToken,
            val value: kotlin.Boolean,
        ) : PublishToNetworkTable

        data class DoubleArray(
            val publisher: NetworkTablePublisherToken.DoubleArrayPublisherToken,
            val value: List<kotlin.Double>,
        ) : PublishToNetworkTable

        data class StringArray(
            val publisher: NetworkTablePublisherToken.StringArrayPublisherToken,
            val value: List<kotlin.String>,
        ) : PublishToNetworkTable

        data class IntegerArray(
            val publisher: NetworkTablePublisherToken.IntegerArrayPublisherToken,
            val value: List<Long>,
        ) : PublishToNetworkTable

        data class BooleanArray(
            val publisher: NetworkTablePublisherToken.BooleanArrayPublisherToken,
            val value: List<kotlin.Boolean>,
        ) : PublishToNetworkTable
    }

    sealed interface InitNetworkTableSubscriber<TMessage> : Effect<TMessage> {
        data class Double<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: kotlin.Double,
            val message: (Result<NetworkTableSubscriberToken.DoubleSubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class String<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: kotlin.String,
            val message: (Result<NetworkTableSubscriberToken.StringSubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class Integer<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: Long,
            val message: (Result<NetworkTableSubscriberToken.IntegerSubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class Boolean<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: kotlin.Boolean,
            val message: (Result<NetworkTableSubscriberToken.BooleanSubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class DoubleArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: List<kotlin.Double>,
            val message: (Result<NetworkTableSubscriberToken.DoubleArraySubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class StringArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: List<kotlin.String>,
            val message: (Result<NetworkTableSubscriberToken.StringArraySubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class IntegerArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: List<Long>,
            val message: (Result<NetworkTableSubscriberToken.IntegerArraySubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>

        data class BooleanArray<TMessage>(
            val table: NetworkTableToken,
            val topicName: kotlin.String,
            val defaultValue: List<kotlin.Boolean>,
            val message: (Result<NetworkTableSubscriberToken.BooleanArraySubscriberToken, Error>) -> TMessage,
        ) : InitNetworkTableSubscriber<TMessage>
    }
}

sealed interface Subscription<out TMessage> {
    data class Interval<TMessage>(
        val id: SubscriptionIdentifier,
        val millisecondsBetweenReads: Int,
        val message: (Long) -> TMessage,
    ) : Subscription<TMessage>

    data class WebSocket<TMessage>(
        val id: SubscriptionIdentifier,
        val token: WebSocketToken,
        val message: (String) -> TMessage,
    ) : Subscription<TMessage>

    data class DigitalPortValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: DigitalInputToken,
        val millisecondsBetweenReads: Int,
        val message: (DioPortState) -> TMessage,
    ) : Subscription<TMessage>

    data class DigitalPortValueChanged<TMessage>(
        val id: SubscriptionIdentifier,
        val token: DigitalInputToken,
        val message: (DioPortState, DioPortState) -> TMessage,
    ) : Subscription<TMessage>

    data class AnalogPortValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: AnalogInputToken,
        val millisecondsBetweenReads: Int,
        val useAverageValue: Boolean,
        val message: (Double) -> TMessage,
    ) : Subscription<TMessage>

    data class AnalogPortValueChanged<TMessage>(
        val id: SubscriptionIdentifier,
        val token: AnalogInputToken,
        val useAverageValue: Boolean,
        val message: (Double, Double) -> TMessage,
    ) : Subscription<TMessage>

    data class HidPortValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: HidInputToken,
        val message: (HidValue) -> TMessage,
    ) : Subscription<TMessage>

    data class HidPortValueChanged<TMessage>(
        val id: SubscriptionIdentifier,
        val token: HidInputToken,
        val message: (HidValue, HidValue) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotState<TMessage>(
        val id: SubscriptionIdentifier,
        val message: (RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class RobotStateChanged<TMessage>(
        val id: SubscriptionIdentifier,
        val message: (RunningRobotState, RunningRobotState) -> TMessage,
    ) : Subscription<TMessage>

    data class CANcoderValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: CanDeviceToken.EncoderToken,
        val message: (CanDeviceSnapshot.EncoderSnapshot) -> TMessage,
    ) : Subscription<TMessage>

    data class CANRangeValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: CanDeviceToken.CANRangeToken,
        val message: (CanDeviceSnapshot.CanRangeSnapshot) -> TMessage,
    ) : Subscription<TMessage>

    data class PigeonValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: CanDeviceToken.PigeonToken,
        val message: (CanDeviceSnapshot.PigeonSnapshot) -> TMessage,
    ) : Subscription<TMessage>

    data class TalonValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: CanDeviceToken.MotorToken.TalonMotorToken,
        val message: (CanDeviceSnapshot.TalonSnapshot) -> TMessage,
    ) : Subscription<TMessage>

    data class SerialValue<TMessage>(
        val id: SubscriptionIdentifier,
        val baudRate: Int,
        val port: SerialPort.Port,
        val message: (String) -> TMessage,
    ) : Subscription<TMessage>

    data class TCPValue<TMessage>(
        val id: SubscriptionIdentifier,
        val token: TCPToken,
        val message: (List<String>) -> TMessage,
    ) : Subscription<TMessage>

    sealed interface NetworkTableValue<TMessage> : Subscription<TMessage> {
        val id: SubscriptionIdentifier

        data class Double<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.DoubleSubscriberToken,
            val message: (kotlin.Double) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class String<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.StringSubscriberToken,
            val message: (kotlin.String) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class Integer<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.IntegerSubscriberToken,
            val message: (Long) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class Boolean<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.BooleanSubscriberToken,
            val message: (kotlin.Boolean) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class DoubleArray<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.DoubleArraySubscriberToken,
            val message: (List<kotlin.Double>) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class StringArray<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.StringArraySubscriberToken,
            val message: (List<kotlin.String>) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class IntegerArray<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.IntegerArraySubscriberToken,
            val message: (List<Long>) -> TMessage,
        ) : NetworkTableValue<TMessage>

        data class BooleanArray<TMessage>(
            override val id: SubscriptionIdentifier,
            val token: NetworkTableSubscriberToken.BooleanArraySubscriberToken,
            val message: (List<kotlin.Boolean>) -> TMessage,
        ) : NetworkTableValue<TMessage>
    }
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
        is Effect.Log -> {
            effect
        }

        is Effect.InitDigitalPortForInput -> {
            Effect.InitDigitalPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitDigitalPortForOutput -> {
            Effect.InitDigitalPortForOutput(
                port = effect.port,
                initialValue = effect.initialValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitAnalogPortForInput -> {
            Effect.InitAnalogPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitAnalogPortForOutput -> {
            Effect.InitAnalogPortForOutput(
                port = effect.port,
                initialVoltage = effect.initialVoltage,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.SetDigitalPortState -> {
            Effect.SetDigitalPortState(
                token = effect.token,
                value = effect.value,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.SetAnalogPortVoltage -> {
            Effect.SetAnalogPortVoltage(
                token = effect.token,
                voltage = effect.voltage,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitPwmPortForOutput -> {
            Effect.InitPwmPortForOutput(
                port = effect.port,
                initialSpeed = effect.initialSpeed,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.SetPwmValue -> {
            Effect.SetPwmValue(
                token = effect.token,
                value = effect.value,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitHidPortForInput -> {
            Effect.InitHidPortForInput(
                port = effect.port,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitCanDevice.InitMotor.Talon -> {
            Effect.InitCanDevice.InitMotor.Talon(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        }

        is Effect.InitCanDevice.InitMotor.Neo -> {
            Effect.InitCanDevice.InitMotor.Neo(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        }

        is Effect.InitCanDevice.Encoder -> {
            Effect.InitCanDevice.Encoder(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        }

        is Effect.InitCanDevice.Pigeon -> {
            Effect.InitCanDevice.Pigeon(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        }

        is Effect.InitCanDevice.Range -> {
            Effect.InitCanDevice.Range(
                id = effect.id,
                message = { deviceId, result -> mapFunction(effect.message(deviceId, result)) },
            )
        }

        is Effect.ConfigCanDevice.Talon -> {
            Effect.ConfigCanDevice.Talon(
                talon = effect.talon,
                config = effect.config,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.ConfigCanDevice.Encoder -> {
            Effect.ConfigCanDevice.Encoder(
                cancoder = effect.cancoder,
                config = effect.config,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.ConfigCanDevice.Pigeon -> {
            Effect.ConfigCanDevice.Pigeon(
                pigeon = effect.pigeon,
                config = effect.config,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.SetCanMotorSpeed -> {
            effect
        }

        is Effect.SetTalonVoltage -> {
            effect
        }

        is Effect.ReadFile -> {
            Effect.ReadFile(
                path = effect.path,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.LoadSong -> {
            Effect.LoadSong(
                motor = effect.motor,
                songData = effect.songData,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.PlaySong -> {
            Effect.PlaySong(
                token = effect.token,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.StopSong -> {
            Effect.StopSong(
                token = effect.token,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.ForwardPort -> {
            Effect.ForwardPort(
                port = effect.port,
                remoteName = effect.remoteName,
                remotePort = effect.remotePort,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitWebSocket -> {
            Effect.InitWebSocket(
                url = effect.url,
                message = { token -> mapFunction(effect.message(token)) },
            )
        }

        is Effect.InitTCPClient -> {
            Effect.InitTCPClient(
                host = effect.host,
                port = effect.port,
                topic = effect.topic,
                message = { token -> mapFunction(effect.message(token)) },
            )
        }

        is Effect.RunAsync<TMessage, *> -> {
            fun <TOutput> mapRunAsync(
                effect: Effect.RunAsync<TMessage, TOutput>,
                mapFunction: (TMessage) -> TNewMessage,
            ): Effect.RunAsync<TNewMessage, TOutput> =
                Effect.RunAsync(
                    function = effect.function,
                    message = { output -> mapFunction(effect.message(output)) },
                )
            mapRunAsync(
                effect = effect,
                mapFunction = mapFunction,
            )
        }

        is Effect.InitNetworkTable -> {
            Effect.InitNetworkTable(
                name = effect.name,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.Double -> {
            Effect.InitNetworkTablePublisher.Double(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.String -> {
            Effect.InitNetworkTablePublisher.String(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.Integer -> {
            Effect.InitNetworkTablePublisher.Integer(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.Boolean -> {
            Effect.InitNetworkTablePublisher.Boolean(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.DoubleArray -> {
            Effect.InitNetworkTablePublisher.DoubleArray(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.StringArray -> {
            Effect.InitNetworkTablePublisher.StringArray(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.IntegerArray -> {
            Effect.InitNetworkTablePublisher.IntegerArray(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTablePublisher.BooleanArray -> {
            Effect.InitNetworkTablePublisher.BooleanArray(
                table = effect.table,
                topicName = effect.topicName,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.PublishToNetworkTable.Double -> effect
        is Effect.PublishToNetworkTable.String -> effect
        is Effect.PublishToNetworkTable.Integer -> effect
        is Effect.PublishToNetworkTable.Boolean -> effect
        is Effect.PublishToNetworkTable.DoubleArray -> effect
        is Effect.PublishToNetworkTable.StringArray -> effect
        is Effect.PublishToNetworkTable.IntegerArray -> effect
        is Effect.PublishToNetworkTable.BooleanArray -> effect

        is Effect.InitNetworkTableSubscriber.Double -> {
            Effect.InitNetworkTableSubscriber.Double(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.String -> {
            Effect.InitNetworkTableSubscriber.String(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.Integer -> {
            Effect.InitNetworkTableSubscriber.Integer(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.Boolean -> {
            Effect.InitNetworkTableSubscriber.Boolean(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.DoubleArray -> {
            Effect.InitNetworkTableSubscriber.DoubleArray(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.StringArray -> {
            Effect.InitNetworkTableSubscriber.StringArray(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.IntegerArray -> {
            Effect.InitNetworkTableSubscriber.IntegerArray(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }

        is Effect.InitNetworkTableSubscriber.BooleanArray -> {
            Effect.InitNetworkTableSubscriber.BooleanArray(
                table = effect.table,
                topicName = effect.topicName,
                defaultValue = effect.defaultValue,
                message = { result -> mapFunction(effect.message(result)) },
            )
        }
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
        is Interval -> {
            Interval(
                id = subscription.id,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { elapsed -> mapFunction(subscription.message(elapsed)) },
            )
        }

        is DigitalPortValue -> {
            DigitalPortValue(
                id = subscription.id,
                token = subscription.token,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is DigitalPortValueChanged -> {
            DigitalPortValueChanged(
                id = subscription.id,
                token = subscription.token,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        }

        is AnalogPortValue -> {
            AnalogPortValue(
                id = subscription.id,
                token = subscription.token,
                millisecondsBetweenReads = subscription.millisecondsBetweenReads,
                useAverageValue = subscription.useAverageValue,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is AnalogPortValueChanged -> {
            AnalogPortValueChanged(
                id = subscription.id,
                token = subscription.token,
                useAverageValue = subscription.useAverageValue,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        }

        is HidPortValue -> {
            HidPortValue(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is HidPortValueChanged -> {
            HidPortValueChanged(
                id = subscription.id,
                token = subscription.token,
                message = { oldValue, newValue -> mapFunction(subscription.message(oldValue, newValue)) },
            )
        }

        is RobotState -> {
            RobotState(
                id = subscription.id,
                message = { state -> mapFunction(subscription.message(state)) },
            )
        }

        is RobotStateChanged -> {
            RobotStateChanged(
                id = subscription.id,
                message = { oldState, newState -> mapFunction(subscription.message(oldState, newState)) },
            )
        }

        is CANcoderValue -> {
            CANcoderValue(
                id = subscription.id,
                token = subscription.token,
                message = { snapshot -> mapFunction(subscription.message(snapshot)) },
            )
        }

        is Subscription.CANRangeValue -> {
            Subscription.CANRangeValue(
                id = subscription.id,
                token = subscription.token,
                message = { snapshot -> mapFunction(subscription.message(snapshot)) },
            )
        }

        is PigeonValue -> {
            PigeonValue(
                id = subscription.id,
                token = subscription.token,
                message = { snapshot -> mapFunction(subscription.message(snapshot)) },
            )
        }

        is TalonValue -> {
            TalonValue(
                id = subscription.id,
                token = subscription.token,
                message = { snapshot -> mapFunction(subscription.message(snapshot)) },
            )
        }

        is WebSocket -> {
            WebSocket(
                id = subscription.id,
                token = subscription.token,
                message = { info -> mapFunction(subscription.message(info)) },
            )
        }

        is SerialValue -> {
            SerialValue(
                id = subscription.id,
                baudRate = subscription.baudRate,
                port = subscription.port,
                message = { info -> mapFunction(subscription.message(info)) },
            )
        }

        is Subscription.TCPValue -> {
            Subscription.TCPValue(
                id = subscription.id,
                token = subscription.token,
                message = { info -> mapFunction(subscription.message(info)) },
            )
        }

        is Subscription.NetworkTableValue.Double -> {
            Subscription.NetworkTableValue.Double(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.String -> {
            Subscription.NetworkTableValue.String(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.Integer -> {
            Subscription.NetworkTableValue.Integer(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.Boolean -> {
            Subscription.NetworkTableValue.Boolean(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.DoubleArray -> {
            Subscription.NetworkTableValue.DoubleArray(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.StringArray -> {
            Subscription.NetworkTableValue.StringArray(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.IntegerArray -> {
            Subscription.NetworkTableValue.IntegerArray(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }

        is Subscription.NetworkTableValue.BooleanArray -> {
            Subscription.NetworkTableValue.BooleanArray(
                id = subscription.id,
                token = subscription.token,
                message = { value -> mapFunction(subscription.message(value)) },
            )
        }
    }
