package teaforge.platform.RoboRio

import edu.wpi.first.wpilibj.RobotBase
import teaforge.ProgramConfig
import teaforge.platform.RoboRio.Effect.ReadFile
import teaforge.platform.RoboRio.Subscription.Interval
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

    data class ReadFile<TMessage>(
        val path: String,
        val message: (Result<ByteArray, Error>) -> TMessage,
    ) : Effect<TMessage>
}

sealed interface Subscription<out TMessage> {
    data class Interval<TMessage>(
        val millisecondsBetweenReads: Int,
        val message: (Long) -> TMessage,
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
        is Effect.ReadFile ->
            ReadFile(
                path = effect.path,
                message = { result -> mapFunction(effect.message(result)) },
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

    }
