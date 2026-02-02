package teaforge.platform.RoboRio.internal

import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.StatusSignal
import com.ctre.phoenix6.Timestamp
import teaforge.platform.RoboRio.*
import edu.wpi.first.hal.HALUtil
import edu.wpi.first.math.geometry.Rotation3d
import edu.wpi.first.units.measure.Angle
import edu.wpi.first.units.measure.AngularVelocity
import edu.wpi.first.wpilibj.GenericHID
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import sun.misc.Signal
import teaforge.platform.RoboRio.DioPortState
import teaforge.platform.RoboRio.HidValue
import teaforge.platform.RoboRio.CanDeviceSnapshot
import teaforge.platform.RoboRio.RunningRobotState
import teaforge.platform.RoboRio.Subscription
import teaforge.utils.Maybe
import teaforge.utils.map
import teaforge.utils.Result
import kotlin.math.PI

sealed interface SubscriptionState<TMessage> {
    data class Interval<TMessage>(
        val config: Subscription.Interval<TMessage>,
        val nextReadTimeMicroseconds: Long,
    ) : SubscriptionState<TMessage>

    data class WebSocket<TMessage>(
        val config: Subscription.WebSocket<TMessage>,
        val session: ClientWebSocketSession,
        val client: HttpClient,
    ) : SubscriptionState<TMessage>

    data class DigitalPortValue<TMessage>(
        val config: Subscription.DigitalPortValue<TMessage>,
        val lastReadTimeMicroseconds: Long,
    ) : SubscriptionState<TMessage>

    data class DigitalPortValueChanged<TMessage>(
        val config: Subscription.DigitalPortValueChanged<TMessage>,
        val lastReadValue: DioPortState,
    ) : SubscriptionState<TMessage>

    data class AnalogPortValue<TMessage>(
        val config: Subscription.AnalogPortValue<TMessage>,
        val lastReadTimeMicroseconds: Long,
    ) : SubscriptionState<TMessage>

    data class AnalogPortValueChanged<TMessage>(
        val config: Subscription.AnalogPortValueChanged<TMessage>,
        val lastReadValue: Double,
    ) : SubscriptionState<TMessage>

    data class HidPortValue<TMessage>(
        val config: Subscription.HidPortValue<TMessage>,
        val hidDevice: GenericHID,
    ) : SubscriptionState<TMessage>

    data class HidPortValueChanged<TMessage>(
        val config: Subscription.HidPortValueChanged<TMessage>,
        val hidDevice: GenericHID,
        val lastReadValue: HidValue,
    ) : SubscriptionState<TMessage>

    data class RobotState<TMessage>(
        val config: Subscription.RobotState<TMessage>,
    ) : SubscriptionState<TMessage>

    data class RobotStateChanged<TMessage>(
        val config: Subscription.RobotStateChanged<TMessage>,
        val lastReadValue: RunningRobotState,
    ) : SubscriptionState<TMessage>

    data class CANcoderValue<TMessage>(
        val config: Subscription.CANcoderValue<TMessage>,
        val lastReadCANcoderValue: CanDeviceSnapshot.EncoderSnapshot
    ) : SubscriptionState<TMessage>

    data class PigeonValue<TMessage>(
        val config: Subscription.PigeonValue<TMessage>,
        val lastReadPigeonValue: CanDeviceSnapshot.PigeonSnapshot
    ) : SubscriptionState<TMessage>

    data class TalonValue<TMessage>(
        val config: Subscription.TalonValue<TMessage>,
        val lastReadTalonValue: CanDeviceSnapshot.TalonSnapshot,
    ) : SubscriptionState<TMessage>
}

fun <TMessage, TModel> createDigitalPortValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.DigitalPortValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.DigitalPortValue(
            config = config,
            lastReadTimeMicroseconds = 0,
        ),
    )

fun <TMessage, TModel> createDigitalPortValueChangedState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.DigitalPortValueChanged<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    val initialValue = getDioPortValue(config.token.device)
    return Pair(
        model,
        SubscriptionState.DigitalPortValueChanged(
            config = config,
            lastReadValue = initialValue,
        ),
    )
}

fun <TMessage, TModel> createAnalogPortValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.AnalogPortValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.AnalogPortValue(
            config = config,
            lastReadTimeMicroseconds = 0,
        ),
    )

fun <TMessage, TModel> createAnalogPortValueChangedState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.AnalogPortValueChanged<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    val analogInput = config.token.device
    val initialValue =
        if (config.useAverageValue) {
            analogInput.averageVoltage
        } else {
            analogInput.voltage
        }
    return Pair(
        model,
        SubscriptionState.AnalogPortValueChanged(
            config = config,
            lastReadValue = initialValue,
        ),
    )
}

fun <TMessage, TModel> createHidPortValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.HidPortValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    val hidDevice = GenericHID(config.token.port)
    return Pair(
        model,
        SubscriptionState.HidPortValue(
            config = config,
            hidDevice = hidDevice,
        ),
    )
}

fun <TMessage, TModel> createHidPortValueChangedState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.HidPortValueChanged<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    val hidDevice = GenericHID(config.token.port)
    val axisCount = hidDevice.getAxisCount()
    val buttonCount = hidDevice.getButtonCount()
    val axisValues = (0..<axisCount).map { i -> hidDevice.getRawAxis(i) }.toTypedArray()
    val buttonValues = (1..buttonCount).map { i -> hidDevice.getRawButton(i) }.toTypedArray()

    val initialValue =
        HidValue(
            axisCount = axisCount,
            buttonCount = buttonCount,
            axisValues = axisValues,
            buttonValues = buttonValues,
        )

    return Pair(
        model,
        SubscriptionState.HidPortValueChanged(
            config = config,
            hidDevice = hidDevice,
            lastReadValue = initialValue,
        ),
    )
}

fun <TMessage, TModel> createRobotStateSubscriptionState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.RobotState<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.RobotState(
            config = config,
        ),
    )

fun <TMessage, TModel> createRobotStateChangedState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.RobotStateChanged<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.RobotStateChanged(
            config = config,
            lastReadValue = getRunningRobotState(),
        ),
    )

fun <TMessage, TModel> createCANcoderValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.CANcoderValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.CANcoderValue(
            config = config,
            lastReadCANcoderValue = CanDeviceSnapshot.EncoderSnapshot(
                absolutePos = SignalValue<Double>(
                    value = config.initialAbsolutePos.valueAsDouble,
                    timestamp = config.initialAbsolutePos.timestamp,
                    status = config.initialAbsolutePos.status,
                ),
                relativePos = SignalValue<Double>(
                    value = config.initialRelativePos.valueAsDouble,
                    timestamp = config.initialRelativePos.timestamp,
                    status = config.initialRelativePos.status,
                ),
                velocity = SignalValue<Double>(
                    value = config.initialVelocity.valueAsDouble,
                    timestamp = config.initialVelocity.timestamp,
                    status = config.initialVelocity.status,
                ),
            )
        ),
    )

fun <TMessage, TModel> createPigeonValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.PigeonValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.PigeonValue(
            config = config,
            lastReadPigeonValue = CanDeviceSnapshot.PigeonSnapshot(
                yawRate = SignalValue(
                    value = config.initialYawRate.valueAsDouble,
                    timestamp = config.initialYawRate.timestamp,
                    status = config.initialYawRate.status,
                ),
                pitchRate = SignalValue(
                    value = config.initialPitchRate.valueAsDouble,
                    timestamp = config.initialPitchRate.timestamp,
                    status = config.initialPitchRate.status,
                ),
                rollRate = SignalValue(
                    value = config.initialRollRate.valueAsDouble,
                    timestamp = config.initialRollRate.timestamp,
                    status = config.initialRollRate.status,
                ),
                yaw = SignalValue(
                    value = config.initialYaw.valueAsDouble,
                    timestamp = config.initialYaw.timestamp,
                    status = config.initialYaw.status,
                ),
                pitch = SignalValue(
                    value = config.initialPitch.valueAsDouble,
                    timestamp = config.initialPitch.timestamp,
                    status = config.initialPitch.status,
                ),
                roll = SignalValue(
                    value = config.initialRoll.valueAsDouble,
                    timestamp = config.initialRoll.timestamp,
                    status = config.initialRoll.status,
                ),
            )
        ),
    )

fun <TMessage, TModel> createTalonValueState(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.TalonValue<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    Pair(
        model,
        SubscriptionState.TalonValue(
            config = config,
            lastReadTalonValue = CanDeviceSnapshot.TalonSnapshot(
                position = SignalValue(
                    value = config.initialPosition.valueAsDouble,
                    timestamp = config.initialPosition.timestamp,
                    status = config.initialPosition.status,
                ),
                velocity = SignalValue(
                    value = config.initialVelocity.valueAsDouble,
                    timestamp = config.initialVelocity.timestamp,
                    status = config.initialVelocity.status,
                ),
            )
        )
    )

fun <TMessage, TModel> createInterval(
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.Interval<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    val currentTime = HALUtil.getFPGATime()
    return Pair(
        model,
        SubscriptionState.Interval(
            config = config,
            nextReadTimeMicroseconds = (config.millisecondsBetweenReads * 1_000L) + currentTime,
        ),
    )
}

fun <TMessage, TModel> createWebSocket( // BLOCKING
    model: RoboRioModel<TMessage, TModel>,
    config: Subscription.WebSocket<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
    return Pair(
        model,
        SubscriptionState.WebSocket(
            config = config,
            session = config.token.session,
            client = config.token.client
        ),
    )
}



fun <TMessage, TModel> processSubscription(
    model: RoboRioModel<TMessage, TModel>,
    subscriptionState: SubscriptionState<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> =
    when (subscriptionState) {
        is SubscriptionState.DigitalPortValue -> runReadDigitalPort(model, subscriptionState)
        is SubscriptionState.DigitalPortValueChanged -> runReadDigitalPortChanged(model, subscriptionState)
        is SubscriptionState.AnalogPortValue ->
            runReadAnalogPort(model, subscriptionState)
        is SubscriptionState.AnalogPortValueChanged -> runReadAnalogPortChanged(model, subscriptionState)
        is SubscriptionState.HidPortValue -> runReadHidPort(model, subscriptionState)
        is SubscriptionState.HidPortValueChanged -> runReadHidPortChanged(model, subscriptionState)
        is SubscriptionState.RobotState -> runReadRobotState(model, subscriptionState)
        is SubscriptionState.RobotStateChanged -> runHasRobotStateChanged(model, subscriptionState)
        is SubscriptionState.CANcoderValue -> runReadCANcoder(model, subscriptionState)
        is SubscriptionState.PigeonValue -> runReadPigeon(model, subscriptionState)
        is SubscriptionState.Interval -> runReadInterval(model, subscriptionState)
        is SubscriptionState.WebSocket -> runReadWebSocket(model, subscriptionState)
        is SubscriptionState.TalonValue -> runReadTalonValue(model, subscriptionState)
    }

fun <TMessage, TModel> startSubscriptionHandler(
    model: RoboRioModel<TMessage, TModel>,
    subscription: Subscription<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    when (subscription) {
        is Subscription.DigitalPortValue -> createDigitalPortValueState(model, subscription)
        is Subscription.DigitalPortValueChanged -> createDigitalPortValueChangedState(model, subscription)
        is Subscription.AnalogPortValue -> createAnalogPortValueState(model, subscription)
        is Subscription.AnalogPortValueChanged -> createAnalogPortValueChangedState(model, subscription)
        is Subscription.HidPortValue -> createHidPortValueState(model, subscription)
        is Subscription.HidPortValueChanged -> createHidPortValueChangedState(model, subscription)
        is Subscription.RobotState -> createRobotStateSubscriptionState(model, subscription)
        is Subscription.RobotStateChanged -> createRobotStateChangedState(model, subscription)
        is Subscription.CANcoderValue -> createCANcoderValueState(model, subscription)
        is Subscription.PigeonValue -> createPigeonValueState(model, subscription)
        is Subscription.Interval -> createInterval(model, subscription)
        is Subscription.WebSocket -> createWebSocket(model, subscription)
        is Subscription.TalonValue -> createTalonValueState(model, subscription)
    }

fun <TMessage, TModel> stopSubscriptionHandler(
    model: RoboRioModel<TMessage, TModel>,
    subscriptionState: SubscriptionState<TMessage>,
): RoboRioModel<TMessage, TModel> =
    when (subscriptionState) {
        is SubscriptionState.DigitalPortValue -> model
        is SubscriptionState.DigitalPortValueChanged -> model
        is SubscriptionState.AnalogPortValue -> model
        is SubscriptionState.AnalogPortValueChanged -> model
        is SubscriptionState.HidPortValue -> model
        is SubscriptionState.HidPortValueChanged -> model
        is SubscriptionState.RobotState -> model
        is SubscriptionState.RobotStateChanged -> model
        is SubscriptionState.CANcoderValue -> model
        is SubscriptionState.PigeonValue -> model
        is SubscriptionState.TalonValue -> model
        is SubscriptionState.Interval -> model
        is SubscriptionState.WebSocket -> closeWebSocket(model, subscriptionState)
    }

fun <TMessage, TModel> runReadDigitalPort(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.DigitalPortValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val currentMicroseconds = HALUtil.getFPGATime()
    val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds

    return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
        val newValue = getDioPortValue(state.config.token.device)

        val updatedState =
            state.copy(
                lastReadTimeMicroseconds = currentMicroseconds,
            )

        Triple(model, updatedState, Maybe.Some(state.config.message(newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadAnalogPort(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.AnalogPortValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val currentMicroseconds = HALUtil.getFPGATime()
    val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds
    return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
        val analogInput = state.config.token.device
        val newValue =
            if (state.config.useAverageValue) {
                analogInput.averageVoltage
            } else {
                analogInput.voltage
            }

        val updatedState =
            state.copy(
                lastReadTimeMicroseconds = currentMicroseconds,
            )

        Triple(model, updatedState, Maybe.Some(state.config.message(newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadHidPort(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.HidPortValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val axisCount = state.hidDevice.getAxisCount()
    val buttonCount = state.hidDevice.getButtonCount()
    val axisValues =
        (0..<axisCount).map({ i -> state.hidDevice.getRawAxis(i) }).toTypedArray()
    val buttonValues =
        (1..buttonCount).map({ i -> state.hidDevice.getRawButton(i) }).toTypedArray()

    val hidValue =
        HidValue(
            axisCount = axisCount,
            buttonCount = buttonCount,
            axisValues = axisValues,
            buttonValues = buttonValues,
        )

    return Triple(model, state, Maybe.Some(state.config.message(hidValue)))
}

fun <TMessage, TModel> runReadRobotState(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.RobotState<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> =
    Triple(model, state, Maybe.Some(state.config.message(getRunningRobotState())))

fun <TMessage, TModel> runHasRobotStateChanged(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.RobotStateChanged<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val newValue = getRunningRobotState()

    return if (newValue != state.lastReadValue) {
        val updatedState =
            state.copy(
                lastReadValue = newValue,
            )

        Triple(model, updatedState, Maybe.Some(state.config.message(state.lastReadValue, newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadCANcoder(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.CANcoderValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val absolutePosSignal = state.config.cancoder.device.absolutePosition
    val relativePosSignal = state.config.cancoder.device.positionSinceBoot
    val velocitySignal = state.config.cancoder.device.velocity

    val currentAbsolutePosTimestamp = absolutePosSignal.timestamp
    val currentRelativePosTimestamp = relativePosSignal.timestamp
    val currentVelocityTimestamp = velocitySignal.timestamp

    val atLeastOneUpdated = !(currentAbsolutePosTimestamp.equals(state.lastReadCANcoderValue.absolutePos.timestamp) &&
                            currentRelativePosTimestamp.equals(state.lastReadCANcoderValue.relativePos.timestamp) &&
                            currentVelocityTimestamp.equals(state.lastReadCANcoderValue.velocity.timestamp))

    return if (atLeastOneUpdated) {
        val newSnapshot = CanDeviceSnapshot.EncoderSnapshot(
            //TODO: convert to rotations? (max)
            absolutePos = SignalValue<Double>(
                value = absolutePosSignal.valueAsDouble * 360, //degrees
                timestamp = currentAbsolutePosTimestamp,
                status = absolutePosSignal.status,
            ),
            relativePos = SignalValue<Double>(
                value = relativePosSignal.valueAsDouble * 360, //degrees
                timestamp = currentRelativePosTimestamp,
                status = relativePosSignal.status,
            ),
            velocity = SignalValue<Double>(
                value = velocitySignal.valueAsDouble, //rotations per sec
                timestamp = currentVelocityTimestamp,
                status = velocitySignal.status,
            )
        )
        val updatedState =
            state.copy(
                lastReadCANcoderValue = newSnapshot,
            )
        Triple(model, updatedState, Maybe.Some(state.config.message(newSnapshot)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadPigeon(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.PigeonValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val yawRateSignal = state.config.pigeon.device.angularVelocityZWorld
    val pitchRateSignal = state.config.pigeon.device.angularVelocityYWorld
    val rollRateSignal = state.config.pigeon.device.angularVelocityXWorld
    val yawSignal = state.config.pigeon.device.yaw
    val pitchSignal = state.config.pigeon.device.pitch
    val rollSignal = state.config.pigeon.device.roll

    val currentYawRateTimestamp = yawRateSignal.timestamp
    val currentPitchRateTimestamp = pitchRateSignal.timestamp
    val currentRollRateTimestamp = rollRateSignal.timestamp
    val currentYawTimestamp = yawSignal.timestamp
    val currentPitchTimestamp = pitchSignal.timestamp
    val currentRollTimestamp = rollSignal.timestamp

    val atLeastOneUpdated = !(currentYawRateTimestamp.equals(state.lastReadPigeonValue.yawRate.timestamp) &&
                            currentPitchRateTimestamp.equals(state.lastReadPigeonValue.pitchRate.timestamp) &&
                            currentRollRateTimestamp.equals(state.lastReadPigeonValue.rollRate.timestamp) &&
                            currentYawTimestamp.equals(state.lastReadPigeonValue.yaw.timestamp) &&
                            currentPitchTimestamp.equals(state.lastReadPigeonValue.pitch.timestamp) &&
                            currentRollTimestamp.equals(state.lastReadPigeonValue.roll.timestamp))

    return if (atLeastOneUpdated) {
        val newSnapshot = CanDeviceSnapshot.PigeonSnapshot(
                yawRate = SignalValue(
                    value = yawRateSignal.valueAsDouble/360,
                    timestamp = yawRateSignal.timestamp,
                    status = yawRateSignal.status,
                ),
                pitchRate = SignalValue(
                    value = pitchRateSignal.valueAsDouble/360,
                    timestamp = pitchRateSignal.timestamp,
                    status = pitchRateSignal.status,
                ),
                rollRate = SignalValue(
                    value = rollRateSignal.valueAsDouble/360,
                    timestamp = rollRateSignal.timestamp,
                    status = rollRateSignal.status,
                ),
                yaw = SignalValue(
                    value = yawSignal.valueAsDouble/360,
                    timestamp = yawSignal.timestamp,
                    status = yawSignal.status,
                ),
                pitch = SignalValue(
                    value = pitchSignal.valueAsDouble/360,
                    timestamp = pitchSignal.timestamp,
                    status = pitchSignal.status,
                ),
                roll = SignalValue(
                    value = rollSignal.valueAsDouble/360,
                    timestamp = rollSignal.timestamp,
                    status = rollSignal.status,
                ),
        )
        val updatedState = state.copy(
            lastReadPigeonValue = newSnapshot,
        )
        Triple(model, updatedState, Maybe.Some(state.config.message(newSnapshot)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadTalonValue(
    //not thread safe, don't use in multiple threads
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.TalonValue<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val positionSignal = state.config.talon.device.position
    val velocitySignal = state.config.talon.device.velocity

    val currentPositionTimestamp = positionSignal.timestamp
    val currentVelocityTimestamp = velocitySignal.timestamp

    val atLeastOneUpdated = !(currentPositionTimestamp.equals(state.lastReadTalonValue.position.timestamp) &&
                            currentVelocityTimestamp.equals(state.lastReadTalonValue.velocity.timestamp))

    return if (atLeastOneUpdated) {
        val newSnapshot = CanDeviceSnapshot.TalonSnapshot(
            position = SignalValue<Double>(
                //since we're returning both the timestamp and status in the message we can check if it's stale and its status(error) in the application layer
                value = positionSignal.valueAsDouble,
                timestamp = currentPositionTimestamp,
                status = positionSignal.status,
            ),
            velocity = SignalValue<Double>(
                value = velocitySignal.valueAsDouble,
                timestamp = currentVelocityTimestamp,
                status = velocitySignal.status,
            ),
        )
        val updatedState =
            state.copy(
                lastReadTalonValue = newSnapshot,
            )
        Triple(model, updatedState, Maybe.Some(state.config.message(newSnapshot)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadInterval(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.Interval<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val currentMicroseconds = HALUtil.getFPGATime()
    return if (currentMicroseconds >= state.nextReadTimeMicroseconds) {
        val elapsedMicroseconds = currentMicroseconds - state.nextReadTimeMicroseconds

        val intervalMicroseconds = state.config.millisecondsBetweenReads * 1_000L
        val intervalsMissed = elapsedMicroseconds / intervalMicroseconds

        val newNextReadTime = state.nextReadTimeMicroseconds + (intervalMicroseconds * (intervalsMissed + 1))

        val updatedState = state.copy(nextReadTimeMicroseconds = newNextReadTime)
        Triple(model, updatedState, Maybe.Some(state.config.message(elapsedMicroseconds / 1_000L)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadWebSocket(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.WebSocket<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val read: Maybe<String> = state.session.incoming.tryReceive().getOrNull()?.let { frame ->
        (frame as? Frame.Text)?.let{ Maybe.Some(it.readText()) } ?: Maybe.None
    } ?: Maybe.None

    val message: Maybe<TMessage> = read.map { state.config.message(it) }

    return Triple(model, state, message)
}

fun <TMessage, TModel> runReadDigitalPortChanged(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.DigitalPortValueChanged<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val newValue = getDioPortValue(state.config.token.device)

    return if (newValue != state.lastReadValue) {
        val updatedState = state.copy(lastReadValue = newValue)
        Triple(model, updatedState, Maybe.Some(state.config.message(state.lastReadValue, newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadAnalogPortChanged(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.AnalogPortValueChanged<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val analogInput = state.config.token.device
    val newValue =
        if (state.config.useAverageValue) {
            analogInput.averageVoltage
        } else {
            analogInput.voltage
        }

    return if (newValue != state.lastReadValue) {
        val updatedState = state.copy(lastReadValue = newValue)
        Triple(model, updatedState, Maybe.Some(state.config.message(state.lastReadValue, newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> runReadHidPortChanged(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.HidPortValueChanged<TMessage>,
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
    val axisCount = state.hidDevice.getAxisCount()
    val buttonCount = state.hidDevice.getButtonCount()
    val axisValues = (0..<axisCount).map { i -> state.hidDevice.getRawAxis(i) }.toTypedArray()
    val buttonValues = (1..buttonCount).map { i -> state.hidDevice.getRawButton(i) }.toTypedArray()

    val newValue =
        HidValue(
            axisCount = axisCount,
            buttonCount = buttonCount,
            axisValues = axisValues,
            buttonValues = buttonValues,
        )

    // Compare arrays to detect changes
    val hasChanged =
        !newValue.axisValues.contentEquals(state.lastReadValue.axisValues) ||
                !newValue.buttonValues.contentEquals(state.lastReadValue.buttonValues)

    return if (hasChanged) {
        val updatedState = state.copy(lastReadValue = newValue)
        Triple(model, updatedState, Maybe.Some(state.config.message(state.lastReadValue, newValue)))
    } else {
        Triple(model, state, Maybe.None)
    }
}

fun <TMessage, TModel> closeWebSocket(
    model: RoboRioModel<TMessage, TModel>,
    state: SubscriptionState.WebSocket<TMessage>,
) : RoboRioModel<TMessage, TModel> {
    runBlocking {
        state.session.close(CloseReason(CloseReason.Codes.NORMAL, "End Program"))
    }
    state.client.close()
    return model
}