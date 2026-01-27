package teaforge.platform.RoboRio.internal

import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.GenericHID
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import teaforge.platform.RoboRio.CanDeviceToken
import teaforge.platform.RoboRio.CanDeviceType
import teaforge.platform.RoboRio.DioPortState
import teaforge.platform.RoboRio.HidValue
import teaforge.platform.RoboRio.RunningRobotState
import teaforge.platform.RoboRio.Subscription
import teaforge.utils.Maybe
import teaforge.utils.Result
import teaforge.utils.map
import teaforge.utils.unwrap

sealed interface SubscriptionState<TMessage> {
    data class Interval<TMessage>(
        val config: Subscription.Interval<TMessage>,
        val nextReadTimeMicroseconds: Long,
    ) : SubscriptionState<TMessage>

}

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

fun <TMessage, TModel> startSubscriptionHandler(
    model: RoboRioModel<TMessage, TModel>,
    subscription: Subscription<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, SubscriptionState<TMessage>> =
    when (subscription) {
        is Subscription.Interval -> createInterval(model, subscription)
    }

fun <TMessage, TModel> stopSubscriptionHandler(
    model: RoboRioModel<TMessage, TModel>,
    subscriptionState: SubscriptionState<TMessage>,
): RoboRioModel<TMessage, TModel> =
    when (subscriptionState) {
        is SubscriptionState.Interval -> model
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
