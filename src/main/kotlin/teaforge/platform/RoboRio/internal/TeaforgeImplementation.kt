package teaforge.platform.RoboRio.internal

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import com.ctre.phoenix6.CANBus
import com.ctre.phoenix6.Orchestra
import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.CANrange
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.REVLibError
import com.revrobotics.spark.SparkLowLevel
import com.revrobotics.spark.SparkMax
import edu.wpi.first.hal.HALUtil
import edu.wpi.first.net.PortForwarder
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.AnalogOutput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DigitalOutput
import edu.wpi.first.wpilibj.Servo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.websocket.Frame
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.zeromq.SocketType
import org.zeromq.ZContext
import teaforge.DebugLoggingConfig
import teaforge.EffectResult
import teaforge.LoggerStatus
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
import teaforge.SubscriptionIdentifier
import teaforge.platform.RoboRio.AnalogInputToken
import teaforge.platform.RoboRio.AnalogOutputToken
import teaforge.platform.RoboRio.AnalogPort
import teaforge.platform.RoboRio.CanDeviceToken
import teaforge.platform.RoboRio.DebugLogging
import teaforge.platform.RoboRio.DigitalInputToken
import teaforge.platform.RoboRio.DigitalOutputToken
import teaforge.platform.RoboRio.DioPort
import teaforge.platform.RoboRio.DioPortState
import teaforge.platform.RoboRio.Effect
import teaforge.platform.RoboRio.Error
import teaforge.platform.RoboRio.HidInputToken
import teaforge.platform.RoboRio.LogFile
import teaforge.platform.RoboRio.OrchestraToken
import teaforge.platform.RoboRio.PwmOutputToken
import teaforge.platform.RoboRio.PwmPort
import teaforge.platform.RoboRio.RoboRioProgram
import teaforge.platform.RoboRio.Subscription
import teaforge.platform.RoboRio.TCPToken
import teaforge.platform.RoboRio.TCPTokenImplementation
import teaforge.platform.RoboRio.WebSocketToken
import teaforge.utils.Maybe
import teaforge.utils.Result
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArraySet

val CANBUS_INIT_TIMEOUT_SECONDS = 1.0

private fun createLoggerStatus(debugLogging: DebugLogging): LoggerStatus =
    when (debugLogging) {
        is DebugLogging.Disabled -> {
            LoggerStatus.Disabled
        }

        is DebugLogging.Enabled -> {
            val filename =
                when (debugLogging.logFile) {
                    is LogFile.Default -> {
                        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"))
                        "$timestamp-debug-log.jsonl"
                    }

                    is LogFile.Path -> {
                        debugLogging.logFile.path
                    }
                }
            val fileWriter = FileWriter(File(filename), true)

            val sessions = CopyOnWriteArraySet<DefaultWebSocketServerSession>()
            embeddedServer(Netty, port = 8080) {
                install(io.ktor.server.websocket.WebSockets)

                routing {
                    webSocket("/") {
                        sessions.add(this)
                        try {
                            awaitCancellation()
                        } finally {
                            sessions.remove(this)
                        }
                    }
                }
            }.start(wait = false)

            LoggerStatus.Enabled(
                DebugLoggingConfig(
                    getTimestamp = { HALUtil.getFPGATime() },
                    log = { json ->
                        fileWriter.write(json)
                        fileWriter.write("\n")
                        fileWriter.flush()

                        coroutineScope.launch {
                            sessions.forEach { session ->
                                try {
                                    session.send(Frame.Text(json))
                                } catch (_: Exception) {
                                    sessions.remove(session)
                                }
                            }
                        }
                    },
                    compressionEnabled = debugLogging.compression,
                ),
            )
        }
    }

data class RoboRioModel<TMessage, TModel>(
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
    debugLogging: DebugLogging,
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
    val loggerStatus = createLoggerStatus(debugLogging)

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
            getUniqueIdentifierForSubscription = ::getUniqueIdentifierForSubscription,
            startSubscription = ::startSubscriptionHandler,
            stopSubscription = ::stopSubscriptionHandler,
            loggerStatus = { loggerStatus },
        )

    return teaforge.platform.initRunner(
        runnerConfig,
        roboRioArgs,
        program,
        programArgs,
    )
}

fun <TMessage> getUniqueIdentifierForSubscription(subscription: Subscription<TMessage>): SubscriptionIdentifier =
    when (subscription) {
        is Subscription.Interval -> subscription.id
        is Subscription.WebSocket -> subscription.id
        is Subscription.DigitalPortValue -> subscription.id
        is Subscription.DigitalPortValueChanged -> subscription.id
        is Subscription.AnalogPortValue -> subscription.id
        is Subscription.AnalogPortValueChanged -> subscription.id
        is Subscription.HidPortValue -> subscription.id
        is Subscription.HidPortValueChanged -> subscription.id
        is Subscription.RobotState -> subscription.id
        is Subscription.RobotStateChanged -> subscription.id
        is Subscription.CANcoderValue -> subscription.id
        is Subscription.CANRangeValue -> subscription.id
        is Subscription.PigeonValue -> subscription.id
        is Subscription.TalonValue -> subscription.id
        is Subscription.SerialValue -> subscription.id
        is Subscription.TCPValue -> subscription.id
    }

fun <TMessage, TModel> startOfUpdateCycle(model: RoboRioModel<TMessage, TModel>): RoboRioModel<TMessage, TModel> = model

fun <TMessage, TModel> endOfUpdateCycle(model: RoboRioModel<TMessage, TModel>): RoboRioModel<TMessage, TModel> = model

fun <TMessage, TModel> initRoboRioRunner(
    @Suppress("UNUSED_PARAMETER") args: List<String>,
): RoboRioModel<TMessage, TModel> {
    // Do any hardware initialization here
    CANBus()

    return RoboRioModel(
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
): EffectResult<RoboRioModel<TMessage, TModel>, TMessage> {
    return when (effect) {
        is Effect.InitAnalogPortForInput -> {
            // Check if the analog port has already been initialized
            val alreadyInitialized = model.analogInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<AnalogInputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // Configure the analog port for input using WPILib
                try {
                    val portId = analogPortToInt(effect.port)
                    val analogInput = AnalogInput(portId)
                    // Verify the port is working by attempting to read from it
                    analogInput.voltage

                    val token = AnalogInputToken(effect.port, analogInput)
                    val newModel = model.copy(analogInputTokens = model.analogInputTokens + token)
                    val result = Result.Success<AnalogInputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<AnalogInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing analog input"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.InitAnalogPortForOutput -> {
            // Check if the analog port has already been initialized
            val alreadyInitialized = model.analogOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<AnalogOutputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // Configure the analog port for output using WPILib
                try {
                    val portId = analogPortToInt(effect.port)
                    val analogOutput = AnalogOutput(portId)
                    // Set the port to the initial voltage
                    analogOutput.voltage = effect.initialVoltage

                    val token = AnalogOutputToken(effect.port, analogOutput)
                    val newModel = model.copy(analogOutputTokens = model.analogOutputTokens + token)
                    val result = Result.Success<AnalogOutputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<AnalogOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing analog output"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.InitDigitalPortForInput -> {
            // Check if the digital port has already been initialized
            val alreadyInitialized = model.digitalInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<DigitalInputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // Configure the digital port for input using WPILib
                try {
                    val portId = digitalIoPortToInt(effect.port)
                    val digitalInput = DigitalInput(portId)
                    // Verify the port is working by attempting to read from it
                    digitalInput.get()

                    val token = DigitalInputToken(effect.port, digitalInput)
                    val newModel = model.copy(digitalInputTokens = model.digitalInputTokens + token)
                    val result = Result.Success<DigitalInputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<DigitalInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing digital input"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.InitDigitalPortForOutput -> {
            // Check if the digital port has already been initialized
            val alreadyInitialized = model.digitalOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<DigitalOutputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // Configure the digital port for output using WPILib
                try {
                    val portId = digitalIoPortToInt(effect.port)
                    val digitalOutput = DigitalOutput(portId)
                    // Set the port to the initial value
                    val initialState =
                        when (effect.initialValue) {
                            DioPortState.HIGH -> true
                            DioPortState.LOW -> false
                        }
                    digitalOutput.set(initialState)

                    val token = DigitalOutputToken(effect.port, digitalOutput)
                    val newModel = model.copy(digitalOutputTokens = model.digitalOutputTokens + token)
                    val result = Result.Success<DigitalOutputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<DigitalOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing digital output"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.InitWebSocket -> {
            EffectResult.Async(
                updatedModel = model,
                completion = {
                    val result: Result<WebSocketToken, Error> =
                        try {
                            val client = HttpClient(CIO) { install(WebSockets) }
                            val session = client.webSocketSession { url(effect.url) }

                            Result.Success(WebSocketToken(effect.url, client, session))
                        } catch (e: Exception) {
                            val exception = e.message ?: ""
                            Result.Error(
                                Error.WebSocketInitializationError(
                                    uri = effect.url,
                                    details = exception,
                                ),
                            )
                        }
                    { currentModel: RoboRioModel<TMessage, TModel> ->
                        currentModel to Maybe.Some(effect.message(result))
                    }
                },
            )
        }

        // TODO: PWM ports should be configurable to use a variety of motors, not just a Spark
        is Effect.InitPwmPortForOutput -> {
            // Check if the PWM port has already been initialized
            val alreadyInitialized = model.pwmOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<PwmOutputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // Configure the PWM port for output using WPILib
                try {
                    val portId = pwmPortToInt(effect.port)
                    val pwmOutput = Servo(portId)

                    val center = (effect.minBoundMicroseconds + effect.maxBoundMicroseconds) / 2
                    val range = effect.maxBoundMicroseconds - effect.minBoundMicroseconds
                    val deadband = (effect.deadbandPercent * range).toInt()
                    pwmOutput.setBoundsMicroseconds(
                        effect.maxBoundMicroseconds,
                        center + deadband,
                        center,
                        center - deadband,
                        effect.minBoundMicroseconds,
                    )
                    // Set the port to the initial speed
                    pwmOutput.set(effect.initialSpeed)

                    val token = PwmOutputToken(effect.port, pwmOutput)
                    val newModel = model.copy(pwmOutputTokens = model.pwmOutputTokens + token)
                    val result = Result.Success<PwmOutputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<PwmOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing PWM output"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.InitHidPortForInput -> {
            // Check if the HID port has already been initialized
            val alreadyInitialized = model.hidInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<HidInputToken, Error>(Error.AlreadyInitialized)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                // HID devices don't require complex initialization - just create the token
                try {
                    val token = HidInputToken(effect.port)
                    val newModel = model.copy(hidInputTokens = model.hidInputTokens + token)
                    val result = Result.Success<HidInputToken, Error>(token)
                    EffectResult.Sync(newModel, Maybe.Some(effect.message(result)))
                } catch (e: Exception) {
                    val result =
                        Result.Error<HidInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing HID input"),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            }
        }

        is Effect.Log -> {
            log(effect.msg)
            EffectResult.Sync(model, Maybe.None)
        }

        is Effect.LoadSong -> {
            try {
                val motor = effect.motor.device
                val orchestra = Orchestra(listOf(motor))

                // Write song data to a temporary file
                val tempFile = File.createTempFile("orchestra_", ".chrp")
                tempFile.writeBytes(effect.songData)
                tempFile.deleteOnExit()

                val status = orchestra.loadMusic(tempFile.absolutePath)

                if (status.isOK) {
                    val token = OrchestraToken(effect.motor, orchestra)
                    val result = Result.Success<OrchestraToken, Error>(token)
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                } else {
                    val result =
                        Result.Error<OrchestraToken, Error>(
                            Error.PhoenixError.PhoenixInitializationError(effect.motor.id, status),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            } catch (e: Exception) {
                val result =
                    Result.Error<OrchestraToken, Error>(
                        Error.PhoenixError.PhoenixInitializationError(effect.motor.id, StatusCode.GeneralError),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.PlaySong -> {
            try {
                val status = effect.token.orchestra.play()
                if (status.isOK) {
                    val result = Result.Success<Unit, Error>(Unit)
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                } else {
                    val result =
                        Result.Error<Unit, Error>(
                            Error.PhoenixError.PhoenixInitializationError(effect.token.motor.id, status),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            } catch (e: Exception) {
                // TODO: We should be able to catch Phoenix specific errors here to give a better status
                val result =
                    Result.Error<Unit, Error>(
                        Error.PhoenixError.PhoenixInitializationError(effect.token.motor.id, StatusCode.GeneralError),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.StopSong -> {
            try {
                val status = effect.token.orchestra.stop()
                if (status.isOK) {
                    val result = Result.Success<Unit, Error>(Unit)
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                } else {
                    val result =
                        Result.Error<Unit, Error>(
                            Error.PhoenixError.PhoenixInitializationError(effect.token.motor.id, status),
                        )
                    EffectResult.Sync(model, Maybe.Some(effect.message(result)))
                }
            } catch (e: Exception) {
                // TODO: We should be able to catch Phoenix specific errors here to give a better status
                val result =
                    Result.Error<Unit, Error>(
                        Error.PhoenixError.PhoenixInitializationError(effect.token.motor.id, StatusCode.GeneralError),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.SetCanMotorSpeed -> {
            when (effect.motor) {
                is CanDeviceToken.MotorToken.TalonMotorToken -> effect.motor.device.set(effect.value)
                is CanDeviceToken.MotorToken.NeoMotorToken -> effect.motor.device.set(effect.value)
            }
            return EffectResult.Sync(model, Maybe.None)
        }

        is Effect.SetTalonVoltage -> {
            val voltageRequest = VoltageOut(effect.voltage)
            effect.talon.device.setControl(voltageRequest)

            return EffectResult.Sync(model, Maybe.None)
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

        is Effect.SetDigitalPortState -> {
            try {
                val state =
                    when (effect.value) {
                        DioPortState.HIGH -> true
                        DioPortState.LOW -> false
                    }
                effect.token.device.set(state)
                val result = Result.Success<DioPort, Error>(effect.token.port)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } catch (e: Exception) {
                val result =
                    Result.Error<DioPort, Error>(
                        Error.DigitalPortError(effect.token.port, e.message ?: "Failed to set digital port state"),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.SetAnalogPortVoltage -> {
            try {
                effect.token.device.voltage = effect.voltage
                val result = Result.Success<AnalogPort, Error>(effect.token.port)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } catch (e: Exception) {
                val result =
                    Result.Error<AnalogPort, Error>(
                        Error.AnalogPortError(effect.token.port, e.message ?: "Failed to set analog port voltage"),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.SetPwmValue -> {
            try {
                effect.token.device.set(effect.value)
                val result = Result.Success<PwmPort, Error>(effect.token.port)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } catch (e: Exception) {
                val result =
                    Result.Error<PwmPort, Error>(
                        Error.PwmPortError(effect.token.port, e.message ?: "Failed to set PWM motor speed"),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.InitCanDevice -> {
            // Local helpers for success and error cases
            fun success(
                token: CanDeviceToken,
                id: Int,
                message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
            ): EffectResult<RoboRioModel<TMessage, TModel>, TMessage> {
                val result = Result.Success<CanDeviceToken, Error>(token)
                val newModel = model.copy(canTokens = model.canTokens + token)
                val msg = message(id, result)
                return EffectResult.Sync(newModel, Maybe.Some(msg))
            }

            fun failure(
                error: Error,
                id: Int,
                message: (Int, Result<CanDeviceToken, Error>) -> TMessage,
            ): EffectResult<RoboRioModel<TMessage, TModel>, TMessage> {
                val result = Result.Error<CanDeviceToken, Error>(error)
                return EffectResult.Sync(model, Maybe.Some(message(id, result)))
            }

            when (effect) {
                is Effect.InitCanDevice.InitMotor.Neo -> {
                    val motor = SparkMax(effect.id, SparkLowLevel.MotorType.kBrushless)
                    val connected = motor.lastError == REVLibError.kOk && !motor.firmwareString.isNullOrEmpty()
                    if (connected) {
                        success(
                            CanDeviceToken.MotorToken.NeoMotorToken(effect.id, motor),
                            effect.id,
                            effect.message,
                        )
                    } else {
                        failure(
                            Error.RevError(effect.id, motor.lastError),
                            effect.id,
                            effect.message,
                        )
                    }
                }

                is Effect.InitCanDevice.InitMotor.Talon -> {
                    val motor = TalonFX(effect.id)
                    val status = motor.deviceTemp.waitForUpdate(CANBUS_INIT_TIMEOUT_SECONDS).status
                    if (status.isOK) {
                        success(
                            CanDeviceToken.MotorToken.TalonMotorToken(effect.id, motor),
                            effect.id,
                            effect.message,
                        )
                    } else {
                        failure(
                            Error.PhoenixError.PhoenixInitializationError(effect.id, status),
                            effect.id,
                            effect.message,
                        )
                    }
                }

                is Effect.InitCanDevice.Encoder -> {
                    val encoder = CANcoder(effect.id)
                    val status = encoder.supplyVoltage.status
                    if (status.isOK) {
                        success(
                            CanDeviceToken.EncoderToken(effect.id, encoder),
                            effect.id,
                            effect.message,
                        )
                    } else {
                        failure(
                            Error.PhoenixError.PhoenixInitializationError(effect.id, status),
                            effect.id,
                            effect.message,
                        )
                    }
                }

                is Effect.InitCanDevice.Pigeon -> {
                    val pigeon = Pigeon2(effect.id)
                    val status = pigeon.supplyVoltage.status
                    if (status.isOK) {
                        success(
                            CanDeviceToken.PigeonToken(effect.id, pigeon),
                            effect.id,
                            effect.message,
                        )
                    } else {
                        failure(
                            Error.PhoenixError.PhoenixInitializationError(effect.id, status),
                            effect.id,
                            effect.message,
                        )
                    }
                }

                is Effect.InitCanDevice.Range -> {
                    val range = CANrange(effect.id)
                    val status = range.supplyVoltage.status
                    if (status.isOK) {
                        success(
                            CanDeviceToken.CANRangeToken(effect.id, range),
                            effect.id,
                            effect.message,
                        )
                    } else {
                        failure(
                            Error.PhoenixError.PhoenixInitializationError(effect.id, status),
                            effect.id,
                            effect.message,
                        )
                    }
                }
            }
        }

        // todo: change to async effects
        is Effect.ConfigCanDevice -> {
            when (effect) {
                is Effect.ConfigCanDevice.Talon -> {
                    val status =
                        effect.talon.device.configurator.apply(
                            effect.config,
                        ) // applies config, waits for .1 seconds
                    if (status.isOK) {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(
                                    Result.Success<CanDeviceToken.MotorToken.TalonMotorToken, Error>(effect.talon),
                                ),
                            ),
                        )
                    } else {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(
                                    Result.Error<CanDeviceToken.MotorToken.TalonMotorToken, Error>(
                                        Error.PhoenixError.PhoenixDeviceError(effect.talon, status),
                                    ),
                                ),
                            ),
                        )
                    }
                }

                is Effect.ConfigCanDevice.Encoder -> {
                    val status =
                        effect.cancoder.device.configurator.apply(
                            effect.config,
                        ) // applies config, waits for .1 seconds
                    if (status.isOK) {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(Result.Success<CanDeviceToken.EncoderToken, Error>(effect.cancoder)),
                            ),
                        )
                    } else {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(
                                    Result.Error<CanDeviceToken.EncoderToken, Error>(
                                        Error.PhoenixError.PhoenixDeviceError(effect.cancoder, status),
                                    ),
                                ),
                            ),
                        )
                    }
                }

                is Effect.ConfigCanDevice.Pigeon -> {
                    val status =
                        effect.pigeon.device.configurator.apply(
                            effect.config,
                        ) // applies config, waits for .1 seconds
                    if (status.isOK) {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(Result.Success<CanDeviceToken.PigeonToken, Error>(effect.pigeon)),
                            ),
                        )
                    } else {
                        EffectResult.Sync(
                            model,
                            Maybe.Some(
                                effect.message(
                                    Result.Error<CanDeviceToken.PigeonToken, Error>(
                                        Error.PhoenixError.PhoenixDeviceError(effect.pigeon, status),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }

        is Effect.ForwardPort -> {
            if ((effect.port >= 1024u) and (!effect.remoteName.any { it in "\$_+!*'(),/?:@=&" })) {
                PortForwarder.add(effect.port.toInt(), effect.remoteName, effect.remotePort.toInt())
                val result = Result.Success<UShort, Error>(effect.port)
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else if (effect.port < 1024u) {
                val result =
                    Result.Error<UShort, Error>(
                        Error.PortInitializationError(details = "Port number must be no less than 1024"),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            } else {
                val result =
                    Result.Error<UShort, Error>(
                        Error.PortInitializationError(details = "Invalid remote name (must be DNS or IP address)"),
                    )
                EffectResult.Sync(model, Maybe.Some(effect.message(result)))
            }
        }

        is Effect.RunAsync<TMessage, *> -> {
            fun <TOutput> runAsyncEffect(
                model: RoboRioModel<TMessage, TModel>,
                effect: Effect.RunAsync<TMessage, TOutput>,
            ): EffectResult<RoboRioModel<TMessage, TModel>, TMessage> =
                EffectResult.Async(
                    updatedModel = model,
                    completion = {
                        val output: TOutput = effect.function();
                        { model -> model to Maybe.Some(effect.message(output)) }
                    },
                )
            runAsyncEffect(
                model = model,
                effect = effect,
            )
        }

        is Effect.InitTCPClient -> {
            val result: Result<TCPToken, Error> =
                runCatching {
                    val context = ZContext()
                    val socket = context.createSocket(SocketType.SUB)

                    socket.connect("tcp://${effect.host}:${effect.port}")
                    socket.subscribe(effect.topic.toByteArray())

                    TCPTokenImplementation(context, socket)
                }.fold(
                    onSuccess = { Result.Success(it) },
                    onFailure = { Result.Error(Error.TCPClientInitError(it.message ?: "")) },
                )
            EffectResult.Sync(model, Maybe.Some(effect.message(result)))
        }
    }
}
