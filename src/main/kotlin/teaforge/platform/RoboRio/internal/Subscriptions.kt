package teaforge.platform.RoboRio.internal

import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.GenericHID
import teaforge.platform.RoboRio.*
import teaforge.utils.Maybe

sealed interface SubscriptionState<TMessage> {
        data class DioPortValue<TMessage>(
                val config: Subscription.DioPortValue<TMessage>,
                val lastReadTimeMicroseconds: Long,
        ) : SubscriptionState<TMessage>

        data class DioPortValueChanged<TMessage>(
                val config: Subscription.DioPortValueChanged<TMessage>,
                val lastReadValue: DioPortStatus,
        ) : SubscriptionState<TMessage>

        data class AnalogInputValue<TMessage>(
                val config: Subscription.AnalogInputValue<TMessage>,
                val lastReadTimeMicroseconds: Long,
        ) : SubscriptionState<TMessage>

        data class HidPortValue<TMessage>(
                val config: Subscription.HidPortValue<TMessage>,
                val hidDevice: GenericHID,
        ) : SubscriptionState<TMessage>
}

fun <TMessage, TModel> createDioPortValueState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.DioPortValue<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.DioPortValue(
                        config = config,
                        lastReadTimeMicroseconds = 0,
                )
        )
}

fun <TMessage, TModel> createDioPortValueChangedState(
        model: RoboRioModel<TMessage, TModel>,
        config: Subscription.DioPortValueChanged<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return Pair(
                model,
                SubscriptionState.DioPortValueChanged(
                        config = config,
                        lastReadValue = getDioPortValue(getDioPort(config.port, model)),
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

fun <TMessage, TModel> processSubscription(
        model: RoboRioModel<TMessage, TModel>,
        subscriptionState: SubscriptionState<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {

        return when (subscriptionState) {
                is SubscriptionState.DioPortValue -> runReadDioPort(model, subscriptionState)
                is SubscriptionState.DioPortValueChanged ->
                        runHasDioPortChanged(model, subscriptionState)
                is SubscriptionState.AnalogInputValue ->
                        runReadAnalogInput(model, subscriptionState)
                is SubscriptionState.HidPortValue -> runReadHidPort(model, subscriptionState)
        }
}

fun <TMessage, TModel> startSubscriptionHandler(
        model: RoboRioModel<TMessage, TModel>,
        subscription: Subscription<TMessage>
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> {
        return when (subscription) {
                is Subscription.DioPortValue -> createDioPortValueState(model, subscription)
                is Subscription.DioPortValueChanged ->
                        createDioPortValueChangedState(model, subscription)
                is Subscription.AnalogInputValue -> createAnalogInputEntryState(model, subscription)
                is Subscription.HidPortValue -> createHidPortValueState(model, subscription)
        }
}

fun <TMessage, TModel> stopSubscriptionHandler(
        model: RoboRioModel<TMessage, TModel>,
        subscriptionState: SubscriptionState<TMessage>
): RoboRioModel<TMessage, TModel> {
        return when (subscriptionState) {
                is SubscriptionState.DioPortValue -> model
                is SubscriptionState.DioPortValueChanged -> model
                is SubscriptionState.AnalogInputValue -> model
                is SubscriptionState.HidPortValue -> model
        }
}

fun <TMessage, TModel> runReadDioPort(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.DioPortValue<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val currentMicroseconds = HALUtil.getFPGATime()
        val elapsedTime = currentMicroseconds - state.lastReadTimeMicroseconds

        return if (elapsedTime >= state.config.millisecondsBetweenReads * 1_000L) {
                val newValue = getDioPortValue(getDioPort(state.config.port, model))

                val updatedState =
                        state.copy(
                                lastReadTimeMicroseconds = currentMicroseconds,
                        )

                Triple(model, updatedState, Maybe.Some(state.config.message(newValue)))
        } else {
                Triple(model, state, Maybe.None)
        }
}

fun <TMessage, TModel> runHasDioPortChanged(
        model: RoboRioModel<TMessage, TModel>,
        state: SubscriptionState.DioPortValueChanged<TMessage>
): Triple<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>, Maybe<TMessage>> {
        val newValue = getDioPortValue(getDioPort(state.config.port, model))

        return if (newValue != state.lastReadValue) {
                val updatedState =
                        state.copy(
                                lastReadValue = newValue,
                        )

                Triple(model, updatedState, Maybe.Some(state.config.message(newValue)))
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
