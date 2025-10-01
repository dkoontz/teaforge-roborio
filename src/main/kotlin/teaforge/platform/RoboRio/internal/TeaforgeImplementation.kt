package teaforge.platform.RoboRio.internal

import com.ctre.phoenix6.CANBus
import com.ctre.phoenix6.Orchestra
import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
import com.revrobotics.REVLibError
import com.revrobotics.spark.SparkLowLevel
import com.revrobotics.spark.SparkMax
import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.DigitalOutput
import edu.wpi.first.wpilibj.Filesystem
import edu.wpi.first.wpilibj.RobotState
import edu.wpi.first.wpilibj.motorcontrol.Spark
import teaforge.HistoryEntry
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
import teaforge.platform.RoboRio.*
import teaforge.utils.Maybe
import teaforge.utils.Result
import teaforge.utils.map
import teaforge.utils.valueOrDefault
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.util.*

data class RoboRioModel<TMessage, TModel>(
    val messageHistory: List<HistoryEntry<TMessage, TModel>>,
    val pwmPorts: PwmPorts,
    val dioInputs: Map<DioPort, DigitalInput>,
    val dioOutputs: Map<DioPort, DigitalOutput>,
    val analogInputs: AnalogPorts,
    val motorControllers: MotorRegistry,
    val pigeonControllers: Map<Pigeon, Pigeon2>,
    val encoderControllers: Map<Encoder, CANcoder>,
    val loadedOrchestras: Map<MotorToken.TalonMotorToken, Orchestra>,
    val currentlyPlaying: Map<MotorToken.TalonMotorToken, Orchestra>,
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
        SubscriptionState<TMessage>> {
    val runnerConfig:
            ProgramRunnerConfig<
                    Effect<TMessage>,
                    TMessage,
                    TModel,
                    RoboRioModel<TMessage, TModel>,
                    Subscription<TMessage>,
                    SubscriptionState<TMessage>> =
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

fun <TMessage, TModel> startOfUpdateCycle(
    model: RoboRioModel<TMessage, TModel>
): RoboRioModel<TMessage, TModel> {
    return model
}

fun <TMessage, TModel> endOfUpdateCycle(
    model: RoboRioModel<TMessage, TModel>
): RoboRioModel<TMessage, TModel> {
    return model
}

fun <TMessage, TModel> processHistoryEntry(
    roboRioModel: RoboRioModel<TMessage, TModel>,
    event: HistoryEntry<TMessage, TModel>
): RoboRioModel<TMessage, TModel> {
    return roboRioModel.copy(messageHistory = roboRioModel.messageHistory + event)
}

data class PwmPorts(
    val zero: Spark,
    val one: Spark,
    val two: Spark,
    val three: Spark,
    val four: Spark,
    val five: Spark,
    val six: Spark,
    val seven: Spark,
    val eight: Spark,
    val nine: Spark,
)

data class DioInputs(
    val zero: DigitalInput,
    val one: DigitalInput,
    val two: DigitalInput,
    val three: DigitalInput,
    val four: DigitalInput,
    val five: DigitalInput,
    val six: DigitalInput,
    val seven: DigitalInput,
    val eight: DigitalInput,
    val nine: DigitalInput,
)

/*data class DioOutputs(
    val zero: DigitalOutput,
    val one: DigitalOutput,
    val two: DigitalOutput,
    val three: DigitalOutput,
    val four: DigitalOutput,
    val five: DigitalOutput,
    val six: DigitalOutput,
    val seven: DigitalOutput,
    val eight: DigitalOutput,
    val nine: DigitalOutput,
)*/

data class AnalogPorts(
    val zero: AnalogInput,
    val one: AnalogInput,
    val two: AnalogInput,
    val three: AnalogInput,
)

fun <TMessage, TModel> initRoboRioRunner(args: List<String>): RoboRioModel<TMessage, TModel> {
    // Do any hardware initialization here
    CANBus()
    val createAnalogPortEntry = { port: AnalogPort -> AnalogInput(analogPortToInt(port)) }

    val pwmPorts =
        PwmPorts(
            zero = Spark(pwmPortToInt(PwmPort.Zero)),
            one = Spark(pwmPortToInt(PwmPort.One)),
            two = Spark(pwmPortToInt(PwmPort.Two)),
            three = Spark(pwmPortToInt(PwmPort.Three)),
            four = Spark(pwmPortToInt(PwmPort.Four)),
            five = Spark(pwmPortToInt(PwmPort.Five)),
            six = Spark(pwmPortToInt(PwmPort.Six)),
            seven = Spark(pwmPortToInt(PwmPort.Seven)),
            eight = Spark(pwmPortToInt(PwmPort.Eight)),
            nine = Spark(pwmPortToInt(PwmPort.Nine)),
        )

    val dioInputs: Map<DioPort, DigitalInput> = emptyMap()
    val dioOutputs: Map<DioPort, DigitalOutput> = emptyMap()

    val analogInputs =
        AnalogPorts(
            zero = createAnalogPortEntry(AnalogPort.Zero),
            one = createAnalogPortEntry(AnalogPort.One),
            two = createAnalogPortEntry(AnalogPort.Two),
            three = createAnalogPortEntry(AnalogPort.Three),
        )

    val motorControllers = MotorRegistry()
    val encoderControllers = Encoder.entries.associateWith { CANcoder(it.id) }
    val pigeonControllers = Pigeon.entries.associateWith { Pigeon2(it.id) }

    return RoboRioModel(
        messageHistory = emptyList(),
        pwmPorts = pwmPorts,
        dioInputs = dioInputs,
        dioOutputs = dioOutputs,
        analogInputs = analogInputs,
        motorControllers = motorControllers,
        encoderControllers = encoderControllers,
        pigeonControllers = pigeonControllers,
        loadedOrchestras = emptyMap(),
        currentlyPlaying = emptyMap()
    )
}

fun <TMessage, TModel> processEffect(
    model: RoboRioModel<TMessage, TModel>,
    effect: Effect<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
    return when (effect) {
        is Effect.Log -> {
            log(effect.msg)
            Pair(model, Maybe.None)
        }
        is Effect.SetPwmMotorSpeed -> {
            getPwmPort(effect.pwmSlot, model).set(effect.value)

            Pair(model, Maybe.None)
        }

        is Effect.LoadSong -> {
            val musicFile = File("/home/lvuser/${model.currentlyPlaying.size}.chrp")
            try {
                musicFile.createNewFile()
                musicFile.writeBytes(effect.songData)
            } catch (_: Exception) {
                val error: Maybe<Error> = Maybe.Some(Error.ReadOnlyFileSystem)
                model to Maybe.Some(effect.message(effect.motor, error))
            }

            val motor: TalonFX = getTalonFX(effect.motor, model)
            val orchestra = Orchestra(listOf(motor), musicFile.absolutePath)

            val loadedOrcs = model.loadedOrchestras.plus(effect.motor to orchestra)
            val msg = effect.message(effect.motor, Maybe.None)

            model.copy(loadedOrchestras = loadedOrcs) to Maybe.Some(msg)
        }

        is Effect.PlaySong -> {

            val orcAndError: Pair<Maybe<Orchestra>, Maybe<Error>> = model.loadedOrchestras[effect.motor]?.let {
                val result: StatusCode = it.play()
                val error: Maybe<Error> = if (!result.isOK) {
                    Maybe.Some(Error.PhoenixError(result.name))
                } else {
                    Maybe.None
                }
                Maybe.Some(it) to error
            } ?: (Maybe.None to Maybe.Some(Error.SongNotLoaded))

            val currentlyPlaying: Map<MotorToken.TalonMotorToken, Orchestra> = orcAndError.first.map {
                model.currentlyPlaying + (effect.motor to it)
            }.valueOrDefault(model.currentlyPlaying)

            model.copy(
                loadedOrchestras = model.loadedOrchestras - effect.motor,
                currentlyPlaying = currentlyPlaying
            ) to Maybe.Some(effect.message(effect.motor, orcAndError.second))
        }

        is Effect.StopSong -> {

            val error: Maybe<Error> = model.currentlyPlaying[effect.motor]?.let {
                val result: StatusCode = it.stop()
                it.close()
                if (!result.isOK) Maybe.Some(Error.PhoenixError(result.name)) else Maybe.None
            } ?: Maybe.Some(Error.SongNotPlaying)

            model.copy(
                currentlyPlaying = model.currentlyPlaying - effect.motor
            ) to Maybe.Some(effect.message(effect.motor, error))
        }

        is Effect.SetCanMotorSpeed -> {
            when (effect.motor) {
                is MotorToken.TalonMotorToken -> getTalonFX(effect.motor, model).set(effect.value)
                is MotorToken.NeoMotorToken -> getSparkMax(effect.motor, model).set(effect.value)
            }
            return model to Maybe.None
        }

        is Effect.ReadFile -> {
            val result: Result<ByteArray, Error> = try {
                val path: Path = Paths.get(effect.path)
                val data: ByteArray = Files.readAllBytes(path)
                Result.Success(data)
            } catch (_: Exception) {
                Result.Error(Error.InvalidFilename(effect.path))
            }

            model to Maybe.Some(effect.message(result))
        }

        is Effect.SetDioPort -> {
            val errorMessage = effect.message(Result.Error(Error.AlreadyInitialized))
            val successMessage = effect.message(Result.Success(effect.port))
            val isInput: Boolean = model.dioInputs.containsKey(effect.port)
            val alreadyInitialized: Boolean = model.dioOutputs.containsKey(effect.port)

            if (isInput) model to Maybe.Some(errorMessage)

            val newModel: RoboRioModel<TMessage, TModel> = if (!alreadyInitialized) {
                model.copy(dioOutputs = model.dioOutputs + (effect.port to DigitalOutput(effect.port.id)))
            } else {
                model
            }



            return newModel.dioOutputs[effect.port]?.let {
                val power: Boolean = when (effect.value) {
                    DioPortState.LOW -> false
                    DioPortState.HIGH -> true
                }
                it.set(power)
                newModel to Maybe.Some(successMessage)
            } ?: (newModel to Maybe.Some(errorMessage))


        }

        is Effect.InitMotor -> {
            // Local helpers keep success/error boilerplate out of the branches.
            fun <C : Any> success(token: MotorToken<C>, controller: C): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
                val result = Result.Success<MotorToken<*>, Error>(token)
                val newModel = model.copy(motorControllers = model.motorControllers.plus(token, controller))
                val msg = effect.message(effect, result)
                return newModel to Maybe.Some(msg)
            }

            fun failure(error: Error): Pair<RoboRioModel<TMessage, TModel>, Maybe<TMessage>> {
                val result = Result.Error<MotorToken<*>, Error>(error)
                return model to Maybe.Some(effect.message(effect, result))
            }

            when (effect.type) {
                MotorType.Neo -> {
                    val motor = SparkMax(effect.id, SparkLowLevel.MotorType.kBrushless)
                    val connected = motor.lastError == REVLibError.kOk && !motor.firmwareString.isNullOrEmpty()
                    if (connected) {
                        success(MotorToken.NeoMotorToken(effect.id), motor)
                    } else {
                        failure(Error.RevError(motor.lastError.name))
                    }
                }

                MotorType.Talon -> {
                    val motor = TalonFX(effect.id)
                    val status = motor.deviceTemp.refresh().status
                    if (status.isOK) {
                        success(MotorToken.TalonMotorToken(effect.id), motor)
                    } else {
                        failure(Error.PhoenixError(status.name))
                    }
                }
            }
        }
    }
}

// Utility functions

private fun pwmPortToInt(port: PwmPort): Int {
    return when (port) {
        PwmPort.Zero -> 0
        PwmPort.One -> 1
        PwmPort.Two -> 2
        PwmPort.Three -> 3
        PwmPort.Four -> 4
        PwmPort.Five -> 5
        PwmPort.Six -> 6
        PwmPort.Seven -> 7
        PwmPort.Eight -> 8
        PwmPort.Nine -> 9
    }
}

private fun analogPortToInt(port: AnalogPort): Int {
    return when (port) {
        AnalogPort.Zero -> 0
        AnalogPort.One -> 1
        AnalogPort.Two -> 2
        AnalogPort.Three -> 3
    }
}

private fun log(msg: String) {
    val elapsedMicroseconds = HALUtil.getFPGATime()
    val elapsedMilliseconds = elapsedMicroseconds.div(1_000L).mod(1_000L)
    val elapsedSeconds = elapsedMicroseconds.div(1_000_000L).mod(60L)
    val elapsedMinutes = elapsedMicroseconds.div(60_000_000L).mod(60L)
    val elapsedHours = elapsedMicroseconds.div(3_600_000_000L)

    println(
        "[${String.format("%02d:%02d:%02d:%03d", elapsedHours, elapsedMinutes,
            elapsedSeconds, elapsedMilliseconds)}] $msg"
    )
}

private fun getDioPortValue(port: DigitalInput): DioPortStatus {
    return if (port.get()) {
        DioPortStatus.Open
    } else {
        DioPortStatus.Closed
    }
}

private fun <TMessage, TModel> getAnalogPort(
    port: AnalogPort,
    model: RoboRioModel<TMessage, TModel>
): AnalogInput {
    return when (port) {
        AnalogPort.Zero -> {
            model.analogInputs.zero
        }
        AnalogPort.One -> {
            model.analogInputs.one
        }
        AnalogPort.Two -> {
            model.analogInputs.two
        }
        AnalogPort.Three -> {
            model.analogInputs.three
        }
    }
}

private fun <TMessage, TModel> getPwmPort(port: PwmPort, model: RoboRioModel<TMessage, TModel>): Spark {
    return when (port) {
        PwmPort.Zero -> {
            model.pwmPorts.zero
        }
        PwmPort.One -> {
            model.pwmPorts.one
        }
        PwmPort.Two -> {
            model.pwmPorts.two
        }
        PwmPort.Three -> {
            model.pwmPorts.three
        }
        PwmPort.Four -> {
            model.pwmPorts.four
        }
        PwmPort.Five -> {
            model.pwmPorts.five
        }
        PwmPort.Six -> {
            model.pwmPorts.six
        }
        PwmPort.Seven -> {
            model.pwmPorts.seven
        }
        PwmPort.Eight -> {
            model.pwmPorts.eight
        }
        PwmPort.Nine -> {
            model.pwmPorts.nine
        }
    }
}

private fun <TMessage, TModel> getTalonFX(
    motor: MotorToken.TalonMotorToken,
    model: RoboRioModel<TMessage, TModel>
) : TalonFX = model.motorControllers.get(motor)

private fun <TMessage, TModel> getSparkMax(
    motor: MotorToken.NeoMotorToken,
    model: RoboRioModel<TMessage, TModel>
) : SparkMax = model.motorControllers.get(motor)

private fun <TMessage, TModel> getCANcoder(encoder: Encoder, model: RoboRioModel<TMessage, TModel>) : CANcoder {
    return model.encoderControllers[encoder]!!
}

private fun <TMessage, TModel> getPigeon2(pigeon: Pigeon, model: RoboRioModel<TMessage, TModel>) : Pigeon2 {
    return model.pigeonControllers[pigeon]!!
}

fun getRunningRobotState() : RunningRobotState {
    val state: RunningRobotState =
        if (RobotState.isDisabled()) { RunningRobotState.Disabled }
        else if (RobotState.isTeleop()) { RunningRobotState.Teleop }
        else if (RobotState.isAutonomous()) { RunningRobotState.Autonomous }
        else if (RobotState.isTest()) { RunningRobotState.Test }
        else if (RobotState.isEStopped()) { RunningRobotState.EStopped }
        else RunningRobotState.Unknown


    return state
}

class MotorRegistry private constructor(
    private val backing: Map<MotorToken<*>, Any>
) {
    constructor() : this(emptyMap())

    @Suppress("UNCHECKED_CAST")
    fun <C : Any> get(token: MotorToken<C>): C =
        backing[token] as C

    fun <C : Any> plus(token: MotorToken<C>, controller: C): MotorRegistry =
        MotorRegistry(backing + (token to controller))

    fun contains(token: MotorToken<*>) = token in backing
}