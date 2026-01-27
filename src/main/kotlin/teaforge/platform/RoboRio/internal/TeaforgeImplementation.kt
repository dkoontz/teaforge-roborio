package teaforge.platform.RoboRio.internal

import com.ctre.phoenix6.CANBus
import com.ctre.phoenix6.Orchestra
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.REVLibError
import com.revrobotics.spark.SparkLowLevel
import com.revrobotics.spark.SparkMax
import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.AnalogOutput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DigitalOutput
import edu.wpi.first.wpilibj.RobotState
import edu.wpi.first.wpilibj.motorcontrol.Spark
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import kotlinx.coroutines.runBlocking
import teaforge.HistoryEntry
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
import teaforge.platform.RoboRio.*
import teaforge.platform.RoboRio.DigitalInputToken
import teaforge.utils.Maybe
import teaforge.utils.Result
import java.io.File
import java.io.IOException
import java.nio.file.*
import kotlin.collections.Set
import edu.wpi.first.net.PortForwarder
import teaforge.EffectResult


val CANBUS_INIT_TIMEOUT_SECONDS = 1.0

data class RoboRioModel<TMessage, TModel>(
    val messageHistory: List<HistoryEntry<TMessage, TModel>>,
    val digitalInputTokens: Set<DigitalInputToken>,
    val digitalOutputTokens: Set<DigitalOutputToken>,
    val analogInputTokens: Set<AnalogInputToken>,
    val analogOutputTokens: Set<AnalogOutputToken>,
    val pwmOutputTokens: Set<PwmOutputToken>,
    val hidInputTokens: Set<HidInputToken>,
    val canTokens: Set<CanDeviceToken>,
)

fun <TMessage, TModel> createRoboRioRunner(
    program: RoboRioProgram<TMessage, TModel>,
    roboRioArgs: List<String>,
    programArgs: List<String>,
): ProgramRunnerInstance<
        Effect<TMessage>,
        TMessage,
        TModel,
        RoboRioModel<TMessage, TModel>,
        Subscription<TMessage>,
        SubscriptionState<TMessage>,
        > {
    val runnerConfig:
            ProgramRunnerConfig<
                    Effect<TMessage>,
                    TMessage,
                    TModel,
                    RoboRioModel<TMessage, TModel>,
                    Subscription<TMessage>,
                    SubscriptionState<TMessage>,
                    > =
        ProgramRunnerConfig(
            initRunner = ::initRoboRioRunner,
            processEffect = ::processEffect,
            processSubscription = ::processSubscription,
            startOfUpdateCycle = ::startOfUpdateCycle,
            endOfUpdateCycle = ::endOfUpdateCycle,
            processHistoryEntry = ::processHistoryEntry,
            startSubscription = ::startSubscriptionHandler,
            stopSubscription = ::stopSubscriptionHandler,
        )

    return teaforge.platform.initRunner(
        runnerConfig,
        roboRioArgs,
        program,
        programArgs,
    )
}

fun <TMessage, TModel> startOfUpdateCycle(model: RoboRioModel<TMessage, TModel>): RoboRioModel<TMessage, TModel> = model

fun <TMessage, TModel> endOfUpdateCycle(model: RoboRioModel<TMessage, TModel>): RoboRioModel<TMessage, TModel> = model

fun <TMessage, TModel> processHistoryEntry(
    roboRioModel: RoboRioModel<TMessage, TModel>,
    event: HistoryEntry<TMessage, TModel>,
): RoboRioModel<TMessage, TModel> = roboRioModel//.copy(messageHistory = roboRioModel.messageHistory + event) TODO implement debugger

fun <TMessage, TModel> initRoboRioRunner(
    @Suppress("UNUSED_PARAMETER") args: List<String>,
): RoboRioModel<TMessage, TModel> {
    // Do any hardware initialization here
    CANBus()

    return RoboRioModel(
        messageHistory = emptyList(),
        digitalInputTokens = emptySet(),
        digitalOutputTokens = emptySet(),
        analogInputTokens = emptySet(),
        analogOutputTokens = emptySet(),
        pwmOutputTokens = emptySet(),
        hidInputTokens = emptySet(),
        canTokens = emptySet(),
    )
}

fun <TMessage, TModel> processEffect(
    model: RoboRioModel<TMessage, TModel>,
    effect: Effect<TMessage>,
): EffectResult<TModel, TMessage> {
    return when (effect) {
        is Effect.Log -> {
            log(effect.msg)
            Pair(model, Maybe.None)
        }

        is Effect.ReadFile -> {
            val result: Result<ByteArray, Error> =
                try {
                    val path: Path = Paths.get(effect.path)
                    val data: ByteArray = Files.readAllBytes(path)
                    Result.Success(data)
                } catch (_: NoSuchFileException) {
                    Result.Error(Error.FileNotFound(effect.path))
                } catch (e: AccessDeniedException) {
                    Result.Error(Error.FileAccessDenied(effect.path, e.message ?: "Access denied"))
                } catch (e: InvalidPathException) {
                    Result.Error(Error.InvalidPath(effect.path, e.reason ?: "Invalid path format"))
                } catch (e: FileSystemException) {
                    Result.Error(Error.FileReadError(effect.path, e.reason ?: e.message ?: "File system error"))
                } catch (e: SecurityException) {
                    Result.Error(Error.FileAccessDenied(effect.path, e.message ?: "Security manager denied access"))
                } catch (e: IOException) {
                    Result.Error(Error.FileReadError(effect.path, e.message ?: "I/O error"))
                } catch (e: Exception) {
                    Result.Error(Error.FileReadError(effect.path, e.message ?: "Unknown error"))
                }

            EffectResult.Sync(model, Maybe.Some(effect.message(result)))
        }
    }
}
