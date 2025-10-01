package teaforge.platform.RoboRio.internal

import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.GenericHID
import teaforge.platform.RoboRio.*
import teaforge.utils.*

sealed interface SubscriptionState<TMessage> {
        data class Interval<TMessage>(
                val config: Subscription.Interval<TMessage>,
                val nextReadTimeMicroseconds: Long,
        ) : SubscriptionState<TMessage>

        data class DioPortValue<TMessage>(
                val config: Subscription.DioPortValue<TMessage>,
                val lastReadTimeMicroseconds: Long,
                val active: Boolean,
                val hasInit: Boolean
        ) : SubscriptionState<TMessage>

        data class AnalogInputValue<TMessage>(
                val config: Subscription.AnalogInputValue<TMessage>,
                val lastReadTimeMicroseconds: Long,
        ) : SubscriptionState<TMessage>

        data class HidPortValue<TMessage>(
                val config: Subscription.HidPortValue<TMessage>,
                val hidDevice: GenericHID,
        ) : SubscriptionState<TMessage>

        data class RobotState<TMessage>(
                val config: Subscription.RobotState<TMessage>
        ) : SubscriptionState<TMessage>

        data class RobotStateChanged<TMessage>(
                val config: Subscription.RobotStateChanged<TMessage>,
                val lastReadValue: RunningRobotState
        ) : SubscriptionState<TMessage>

        data class CANcoderValue<TMessage>(
                val config: Subscription.CANcoderValue<TMessage>,
                val lastReadTimeMicroseconds: Long
        ) : SubscriptionState<TMessage>

        data class PigeonValue<TMessage>(
                val config: Subscription.PigeonValue<TMessage>,
                val lastReadTimeMicroseconds: Long
        ) : SubscriptionState<TMessage>

}

fun <TMessage, TModel> createDioPortValueState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.DioPortValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        val active = !model.dioInputs.containsKey(config.port) && !model.dioOutputs.containsKey(config.port)
        val newModel = if (active) {
                model.copy(dioInputs = model.dioInputs + (config.port to DigitalInput(config.port.id)))
        } else {
                model
        }

        return Pair(
                newModel,
                SubscriptionState.DioPortValue(
                        config = config,
                        lastReadTimeMicroseconds = 0,
                        active = active,
                        hasInit = false,
                )
        )
}

fun <TMessage, TModel> createAnalogInputEntryState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.AnalogInputValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.AnalogInputValue(
                        config = config,
                        lastReadTimeMicroseconds = 0,
                )
        )
}

fun <TMessage, TModel> createHidPortValueState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.HidPortValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        val hidDevice = GenericHID(config.port)
        return Pair(
                model,
                SubscriptionState.HidPortValue(
                        config = config,
                        hidDevice = hidDevice,
                )
        )
}

fun <TMessage, TModel> createRobotStateSubscriptionState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.RobotState<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.RobotState(
                        config = config,
                )
        )
}

fun <TMessage, TModel> createRobotStateChangedState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.RobotStateChanged<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.RobotStateChanged(
                        config = config,
                        lastReadValue = getRunningRobotState()
                )
        )
}

fun <TMessage, TModel> createCANcoderValueState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.CANcoderValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.CANcoderValue(
                        config = config,
                        lastReadTimeMicroseconds = 0,
                )
        )
}

fun <TMessage, TModel> createPigeonValueState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.PigeonValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.PigeonValue(
                        config = config,
                        lastReadTimeMicroseconds = 0,
                )
        )
}



fun <TMessage, TModel> createInterval(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.Interval<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>>{
   val currentTime = HALUtil.getFPGATime()
   return Pair(
           model,
           SubscriptionState.Interval(
                   config = config,
                   nextReadTimeMicroseconds = (config.millisecondsBetweenReads * 1_000L) + currentTime
           )
   )
}

fun <TMessage, TModel> processSubscription(
        model: RoboRioModel<TMessage, TModel>,
        subscriptionState: SubscriptionState<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {

        return when (subscriptionState) {
                is SubscriptionState.DioPortValue -> runReadDioPort(model, subscriptionState)
                is SubscriptionState.AnalogInputValue ->
                        runReadAnalogInput(model, subscriptionState)
                is SubscriptionState.HidPortValue -> runReadHidPort(model, subscriptionState)
                is SubscriptionState.RobotState -> runReadRobotState(model, subscriptionState)
                is SubscriptionState.RobotStateChanged -> runHasRobotStateChanged(model, subscriptionState)
                is SubscriptionState.CANcoderValue -> runReadCANcoder(model, subscriptionState)
                is SubscriptionState.PigeonValue -> runReadPigeon(model, subscriptionState)
                is SubscriptionState.Interval -> runReadInterval(model, subscriptionState)
        }
}

fun <TMessage, TModel> startSubscriptionHandler(
        model: RoboRioModel<TMessage, TModel>,
        subscription: Subscription<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return when (subscription) {
                is Subscription.DioPortValue -> createDioPortValueState(model, subscription)
                is Subscription.AnalogInputValue -> createAnalogInputEntryState(model, subscription)
                is Subscription.HidPortValue -> createHidPortValueState(model, subscription)
                is Subscription.RobotState -> createRobotStateSubscriptionState(model, subscription)
                is Subscription.RobotStateChanged -> createRobotStateChangedState(model, subscription)
                is Subscription.CANcoderValue -> createCANcoderValueState(model, subscription)
                is Subscription.PigeonValue -> createPigeonValueState(model, subscription)
                is Subscription.Interval -> createInterval(model, subscription)
        }
}

fun <TMessage, TModel> stopSubscriptionHandler(
        model: RoboRioModel<TMessage, TModel>,
        subscriptionState: SubscriptionState<TMessage>
): RoboRioModel<TMessage, TModel> {
        return when (subscriptionState) {
                is SubscriptionState.DioPortValue -> model
                is SubscriptionState.AnalogInputValue -> model
                is SubscriptionState.HidPortValue -> model
                is SubscriptionState.RobotState -> model
                is SubscriptionState.RobotStateChanged -> model
                is SubscriptionState.CANcoderValue -> model
                is SubscriptionState.PigeonValue<*> -> model
                is SubscriptionState.Interval -> model
        }
}

fun <TMessage, TModel> runReadDioPort(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.DioPortValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        if (!state.hasInit) {
                val result: Result<DioPort, Error> = if (state.active) {
                        Result.Success(state.config.port)
                } else {
                        Result.Error(Error.AlreadyInitialized)
                }
                return Triple(model, state.copy(hasInit = true), Maybe.Some(state.config.onInit(result)))
        }

        if (!state.active) {
                return Triple(model, state, Maybe.None)
        }

        val currentMicroseconds = HALUtil.getFPGATime()
        val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds

        return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
                val dio: DigitalInput = model.dioInputs[state.config.port]!!
                val newValue = getDioPortValue(dio)

                val updatedState =
                        state.copy(
                                lastReadTimeMicroseconds = currentMicroseconds,
                        )

                Triple(model, updatedState, Maybe.Some(state.config.onRead(newValue)))
        } else {
                Triple(model, state, Maybe.None)
        }
}

fun <TMessage, TModel> runReadAnalogInput(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.AnalogInputValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val currentMicroseconds = HALUtil.getFPGATime()
        val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds
        return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
                val analogInput = getAnalogPort(state.config.port, model)
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
        state: SubscriptionState.HidPortValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val axisCount = state.hidDevice.getAxisCount()
        val buttonCount = state.hidDevice.getButtonCount()
        val axisValues =
                (0 ..< axisCount).map({ i -> state.hidDevice.getRawAxis(i) }).toTypedArray()
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
        state: SubscriptionState.RobotState<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        return Triple(model, state, Maybe.Some(state.config.message(getRunningRobotState())))
}

fun <TMessage, TModel> runHasRobotStateChanged(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.RobotStateChanged<TMessage>
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
        state: SubscriptionState.CANcoderValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val currentMicroseconds = HALUtil.getFPGATime()
        val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds
        return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
                val cancoder = getCanDevice(state.config.encoder, model)
                val newValue = cancoder.absolutePosition.valueAsDouble
                val normalized = newValue * 360

                val updatedState =
                        state.copy(
                                lastReadTimeMicroseconds = currentMicroseconds,
                        )

                Triple(model, updatedState, Maybe.Some(state.config.message(state.config.encoder, normalized)))
        } else {
                Triple(model, state, Maybe.None)
        }
}

fun <TMessage, TModel> runReadPigeon(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.PigeonValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val currentMicroseconds = HALUtil.getFPGATime()
        val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds
        return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
                val pigeon = getCanDevice(state.config.pigeon, model)
                pigeon.rotation3d?.let { newValue ->
                        val updatedState =
                                state.copy(
                                        lastReadTimeMicroseconds = currentMicroseconds,
                                )

                        Triple(model, updatedState, Maybe.Some(state.config.message(state.config.pigeon, newValue)))
                } ?: Triple(model, state, Maybe.None)
        } else {
                Triple(model, state, Maybe.None)
        }
}

fun <TMessage, TModel> runReadInterval(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.Interval<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>>{
        val currentMicroseconds = HALUtil.getFPGATime()
        return if (currentMicroseconds >= state.nextReadTimeMicroseconds){
                val elapsedMicroseconds = currentMicroseconds - state.nextReadTimeMicroseconds

                val intervalMicroseconds = state.config.millisecondsBetweenReads * 1_000L
                val intervalsMissed = elapsedMicroseconds / intervalMicroseconds

                val newNextReadTime = state.nextReadTimeMicroseconds + (intervalMicroseconds * (intervalsMissed + 1))

                val updatedState = state.copy(nextReadTimeMicroseconds = newNextReadTime)
                Triple(model, updatedState, Maybe.Some(state.config.message(elapsedMicroseconds / 1_000L)))
        }
        else{
                Triple(model, state, Maybe.None)
        }

}
