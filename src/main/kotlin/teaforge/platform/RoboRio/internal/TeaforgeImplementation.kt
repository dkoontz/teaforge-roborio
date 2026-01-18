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
): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
    return when (effect) {
        is Effect.InitAnalogPortForInput -> {
            // Check if the analog port has already been initialized
            val alreadyInitialized = model.analogInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<AnalogInputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
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
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<AnalogInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing analog input"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }
        is Effect.InitAnalogPortForOutput -> {
            // Check if the analog port has already been initialized
            val alreadyInitialized = model.analogOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<AnalogOutputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
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
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<AnalogOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing analog output"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }

        is Effect.InitDigitalPortForInput -> {
            // Check if the digital port has already been initialized
            val alreadyInitialized = model.digitalInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<DigitalInputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
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
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<DigitalInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing digital input"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }

        is Effect.InitDigitalPortForOutput -> {
            // Check if the digital port has already been initialized
            val alreadyInitialized = model.digitalOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<DigitalOutputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
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
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<DigitalOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing digital output"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }

        // TODO: PWM ports should be configurable to use a variety of motors, not just a Spark
        is Effect.InitPwmPortForOutput -> {
            // Check if the PWM port has already been initialized
            val alreadyInitialized = model.pwmOutputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<PwmOutputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
            } else {
                // Configure the PWM port for output using WPILib
                try {
                    val portId = pwmPortToInt(effect.port)
                    val pwmOutput = Spark(portId)
                    // Set the port to the initial speed
                    pwmOutput.set(effect.initialSpeed)

                    val token = PwmOutputToken(effect.port, pwmOutput)
                    val newModel = model.copy(pwmOutputTokens = model.pwmOutputTokens + token)
                    val result = Result.Success<PwmOutputToken, Error>(token)
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<PwmOutputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing PWM output"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }

        is Effect.InitHidPortForInput -> {
            // Check if the HID port has already been initialized
            val alreadyInitialized = model.hidInputTokens.any { it.port == effect.port }
            if (alreadyInitialized) {
                val result = Result.Error<HidInputToken, Error>(Error.AlreadyInitialized)
                model to Maybe.Some(effect.message(result))
            } else {
                // HID devices don't require complex initialization - just create the token
                try {
                    val token = HidInputToken(effect.port)
                    val newModel = model.copy(hidInputTokens = model.hidInputTokens + token)
                    val result = Result.Success<HidInputToken, Error>(token)
                    newModel to Maybe.Some(effect.message(result))
                } catch (e: Exception) {
                    val result =
                        Result.Error<HidInputToken, Error>(
                            Error.PortInitializationError(e.message ?: "Unknown error initializing HID input"),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            }
        }

        is Effect.Log -> {
            log(effect.msg)
            Pair(model, Maybe.None)
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
                    model to Maybe.Some(effect.message(result))
                } else {
                    val result =
                        Result.Error<OrchestraToken, Error>(
                            Error.PhoenixError(effect.motor.id, status),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            } catch (e: Exception) {
                val result =
                    Result.Error<OrchestraToken, Error>(
                        Error.PhoenixError(effect.motor.id, com.ctre.phoenix6.StatusCode.GeneralError),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.PlaySong -> {
            try {
                val status = effect.token.orchestra.play()
                if (status.isOK) {
                    val result = Result.Success<Unit, Error>(Unit)
                    model to Maybe.Some(effect.message(result))
                } else {
                    val result =
                        Result.Error<Unit, Error>(
                            Error.PhoenixError(effect.token.motor.id, status),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            } catch (e: Exception) {
                // TODO: We should be able to catch Phoenix specific errors here to give a better status
                val result =
                    Result.Error<Unit, Error>(
                        Error.PhoenixError(effect.token.motor.id, com.ctre.phoenix6.StatusCode.GeneralError),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.StopSong -> {
            try {
                val status = effect.token.orchestra.stop()
                if (status.isOK) {
                    effect.token.orchestra.close()
                    val result = Result.Success<Unit, Error>(Unit)
                    model to Maybe.Some(effect.message(result))
                } else {
                    val result =
                        Result.Error<Unit, Error>(
                            Error.PhoenixError(effect.token.motor.id, status),
                        )
                    model to Maybe.Some(effect.message(result))
                }
            } catch (e: Exception) {
                // TODO: We should be able to catch Phoenix specific errors here to give a better status
                val result =
                    Result.Error<Unit, Error>(
                        Error.PhoenixError(effect.token.motor.id, com.ctre.phoenix6.StatusCode.GeneralError),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.SetCanMotorSpeed -> {
            when (effect.motor) {
                is CanDeviceToken.MotorToken.TalonMotorToken -> effect.motor.device.set(effect.value)
                is CanDeviceToken.MotorToken.NeoMotorToken -> effect.motor.device.set(effect.value)
            }
            return model to Maybe.None
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

            model to Maybe.Some(effect.message(result))
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
                model to Maybe.Some(effect.message(result))
            } catch (e: Exception) {
                val result =
                    Result.Error<DioPort, Error>(
                        Error.DigitalPortError(effect.token.port, e.message ?: "Failed to set digital port state"),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.SetAnalogPortVoltage -> {
            try {
                effect.token.device.voltage = effect.voltage
                val result = Result.Success<AnalogPort, Error>(effect.token.port)
                model to Maybe.Some(effect.message(result))
            } catch (e: Exception) {
                val result =
                    Result.Error<AnalogPort, Error>(
                        Error.AnalogPortError(effect.token.port, e.message ?: "Failed to set analog port voltage"),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.SetPwmValue -> {
            try {
                effect.token.device.set(effect.value)
                val result = Result.Success<PwmPort, Error>(effect.token.port)
                model to Maybe.Some(effect.message(result))
            } catch (e: Exception) {
                val result =
                    Result.Error<PwmPort, Error>(
                        Error.PwmPortError(effect.token.port, e.message ?: "Failed to set PWM motor speed"),
                    )
                model to Maybe.Some(effect.message(result))
            }
        }

        is Effect.InitCanDevice -> {
            // Local helpers for success and error cases
            fun success(token: CanDeviceToken): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
                val result = Result.Success<CanDeviceToken, Error>(token)
                val newModel = model.copy(canTokens = model.canTokens + token)
                val msg = effect.message(effect.type, effect.id, result)
                return newModel to Maybe.Some(msg)
            }

            fun failure(error: Error): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
                val result = Result.Error<CanDeviceToken, Error>(error)
                return model to Maybe.Some(effect.message(effect.type, effect.id, result))
            }

            when (effect.type) {
                CanDeviceType.Neo -> {
                    val motor = SparkMax(effect.id, SparkLowLevel.MotorType.kBrushless)
                    val connected = motor.lastError == REVLibError.kOk && !motor.firmwareString.isNullOrEmpty()
                    if (connected) {
                        success(CanDeviceToken.MotorToken.NeoMotorToken(effect.id, motor))
                    } else {
                        failure(Error.RevError(effect.id, motor.lastError))
                    }
                }

                CanDeviceType.Talon -> {
                    val motor = TalonFX(effect.id)
                    val status = motor.deviceTemp.waitForUpdate(CANBUS_INIT_TIMEOUT_SECONDS).status
                    if (status.isOK) {
                        success(CanDeviceToken.MotorToken.TalonMotorToken(effect.id, motor))
                    } else {
                        failure(Error.PhoenixError(effect.id, status))
                    }
                }

                CanDeviceType.Encoder -> {
                    val encoder = CANcoder(effect.id)
                    val status = encoder.supplyVoltage.status
                    if (status.isOK) {
                        success(CanDeviceToken.EncoderToken(effect.id, encoder))
                    } else {
                        failure(Error.PhoenixError(effect.id, status))
                    }
                }

                CanDeviceType.Pigeon -> {
                    val pigeon = Pigeon2(effect.id)
                    val status = pigeon.supplyVoltage.status
                    if (status.isOK) {
                        success(CanDeviceToken.PigeonToken(effect.id, pigeon))
                    } else {
                        failure(Error.PhoenixError(effect.id, status))
                    }
                }
            }
        }
    }
}
