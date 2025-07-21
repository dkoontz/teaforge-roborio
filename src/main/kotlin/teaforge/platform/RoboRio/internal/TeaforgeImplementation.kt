package teaforge.platform.RoboRio.internal

import com.ctre.phoenix6.Orchestra
import com.ctre.phoenix6.hardware.CANcoder
import com.ctre.phoenix6.hardware.Pigeon2
import com.ctre.phoenix6.hardware.TalonFX
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
import teaforge.utils.*
import java.io.File
import java.io.IOException
import java.nio.file.*

data class RoboRioModel<TMessage, TModel>(
    val messageHistory: List<HistoryEntry<TMessage, TModel>>,
    val pwmPorts: PwmPorts,
    val dioInputs: DioInputs,
    val dioOutputs: DioOutputs,
    val analogInputs: AnalogPorts,
    val talonFXControllers: Map<Motor, TalonFX>,
    val pigeonControllers: Map<Pigeon, Pigeon2>,
    val encoderControllers: Map<Encoder, CANcoder>,
    val currentlyPlaying: List<Orchestra>,
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

data class DioOutputs(
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
)

data class AnalogPorts(
    val zero: AnalogInput,
    val one: AnalogInput,
    val two: AnalogInput,
    val three: AnalogInput,
)

fun <TMessage, TModel> initRoboRioRunner(args: List<String>): RoboRioModel<TMessage, TModel> {
    // Do any hardware initialization here
    val createDioEntry = { port: DioPort -> DigitalInput(digitalIoPortToInt(port)) }
    val createDioOutput = { port: DioPort -> DigitalOutput(digitalIoPortToInt(port)) }
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

    val dioInputs =
        DioInputs(
            zero = createDioEntry(DioPort.Zero),
            one = createDioEntry(DioPort.One),
            two = createDioEntry(DioPort.Two),
            three = createDioEntry(DioPort.Three),
            four = createDioEntry(DioPort.Four),
            five = createDioEntry(DioPort.Five),
            six = createDioEntry(DioPort.Six),
            seven = createDioEntry(DioPort.Seven),
            eight = createDioEntry(DioPort.Eight),
            nine = createDioEntry(DioPort.Nine),
        )


    val dioOutputs =
        DioOutputs(
            zero = createDioOutput(DioPort.Zero),
            one = createDioOutput(DioPort.One),
            two = createDioOutput(DioPort.Two),
            three = createDioOutput(DioPort.Three),
            four = createDioOutput(DioPort.Four),
            five = createDioOutput(DioPort.Five),
            six = createDioOutput(DioPort.Six),
            seven = createDioOutput(DioPort.Seven),
            eight = createDioOutput(DioPort.Eight),
            nine = createDioOutput(DioPort.Nine),
        )

    val analogInputs =
        AnalogPorts(
            zero = createAnalogPortEntry(AnalogPort.Zero),
            one = createAnalogPortEntry(AnalogPort.One),
            two = createAnalogPortEntry(AnalogPort.Two),
            three = createAnalogPortEntry(AnalogPort.Three),
        )

    val talonFXControllers = Motor.entries.associateWith { TalonFX(it.id) }
    val encoderControllers = Encoder.entries.associateWith { CANcoder(it.id) }
    val pigeonControllers = Pigeon.entries.associateWith { Pigeon2(it.id) }

    return RoboRioModel(
        messageHistory = emptyList(),
        pwmPorts = pwmPorts,
        dioInputs = dioInputs,
        dioOutputs = dioOutputs,
        analogInputs = analogInputs,
        talonFXControllers = talonFXControllers,
        encoderControllers = encoderControllers,
        pigeonControllers = pigeonControllers,
        currentlyPlaying = emptyList()
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
        is Effect.PlaySong -> { // TODO: This effect is blocking (File read)!!! Add Orchestra initialization at robot init
            val musicFile = File("${Filesystem.getDeployDirectory().absolutePath}/temp.chrp")
            try {
                if (!musicFile.createNewFile()) {
                    if (!musicFile.delete() || !musicFile.createNewFile()) {
                        throw Exception()
                    }
                }
                musicFile.writeBytes(effect.songData)
            } catch (_: Exception) {
                model to effect.message(Error.ReadOnlyFileSystem)
            }

            val orchestra = Orchestra(listOf(getTalonFX(effect.motor, model)), musicFile.absolutePath)
            orchestra.play()

            model.copy(currentlyPlaying = model.currentlyPlaying.plus(orchestra)) to Maybe.None
        }

        is Effect.StopSong -> {
            for (o in model.currentlyPlaying) {
                o.stop()
                o.close()
            }
            model.copy(currentlyPlaying = emptyList()) to Maybe.None
        }

        is Effect.SetCanMotorSpeed -> {
            getTalonFX(effect.motor, model).set(effect.value)
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
            val power: Boolean = when (effect.value) {
                DioPortVoltage.LOW -> false
                DioPortVoltage.HIGH -> true
            }
            getDioOutput(effect.port, model).set(power)

            model to Maybe.None
        }

    }
}

// Utility functions

fun pwmPortToInt(port: PwmPort): Int {
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

fun digitalIoPortToInt(port: DioPort): Int {
    return when (port) {
        DioPort.Zero -> 0
        DioPort.One -> 1
        DioPort.Two -> 2
        DioPort.Three -> 3
        DioPort.Four -> 4
        DioPort.Five -> 5
        DioPort.Six -> 6
        DioPort.Seven -> 7
        DioPort.Eight -> 8
        DioPort.Nine -> 9
    }
}

fun analogPortToInt(port: AnalogPort): Int {
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

fun getDioPortValue(port: DigitalInput): DioPortStatus {
    return if (port.get()) {
        DioPortStatus.Open
    } else {
        DioPortStatus.Closed
    }
}

fun <TMessage, TModel> getDioInput(
    port: DioPort,
    model: RoboRioModel<TMessage, TModel>
): DigitalInput {
    return when (port) {
        DioPort.Zero -> {
            model.dioInputs.zero
        }
        DioPort.One -> {
            model.dioInputs.one
        }
        DioPort.Two -> {
            model.dioInputs.two
        }
        DioPort.Three -> {
            model.dioInputs.three
        }
        DioPort.Four -> {
            model.dioInputs.four
        }
        DioPort.Five -> {
            model.dioInputs.five
        }
        DioPort.Six -> {
            model.dioInputs.six
        }
        DioPort.Seven -> {
            model.dioInputs.seven
        }
        DioPort.Eight -> {
            model.dioInputs.eight
        }
        DioPort.Nine -> {
            model.dioInputs.nine
        }
    }
}

fun <TMessage, TModel> getDioOutput(
    port: DioPort,
    model: RoboRioModel<TMessage, TModel>
): DigitalOutput {
    return when (port) {
        DioPort.Zero -> {
            model.dioOutputs.zero
        }
        DioPort.One -> {
            model.dioOutputs.one
        }
        DioPort.Two -> {
            model.dioOutputs.two
        }
        DioPort.Three -> {
            model.dioOutputs.three
        }
        DioPort.Four -> {
            model.dioOutputs.four
        }
        DioPort.Five -> {
            model.dioOutputs.five
        }
        DioPort.Six -> {
            model.dioOutputs.six
        }
        DioPort.Seven -> {
            model.dioOutputs.seven
        }
        DioPort.Eight -> {
            model.dioOutputs.eight
        }
        DioPort.Nine -> {
            model.dioOutputs.nine
        }
    }
}

fun <TMessage, TModel> getAnalogPort(
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

fun <TMessage, TModel> getPwmPort(port: PwmPort, model: RoboRioModel<TMessage, TModel>): Spark {
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

fun <TMessage, TModel> getTalonFX(motor: Motor, model: RoboRioModel<TMessage, TModel>) : TalonFX {
    return model.talonFXControllers[motor]!!
}

fun <TMessage, TModel> getCANcoder(encoder: Encoder, model: RoboRioModel<TMessage, TModel>) : CANcoder {
    return model.encoderControllers[encoder]!!
}

fun <TMessage, TModel> getPigeon2(pigeon: Pigeon, model: RoboRioModel<TMessage, TModel>) : Pigeon2 {
    return model.pigeonControllers[pigeon]!!
}

fun getRunningRobotState() : RunningRobotState {
    val state: RunningRobotState =
        if (RobotState.isTeleop()) { RunningRobotState.Teleop }
        else if (RobotState.isAutonomous()) { RunningRobotState.Autonomous }
        else if (RobotState.isTest()) { RunningRobotState.Test }
        else if (RobotState.isEStopped()) { RunningRobotState.EStopped }
        else { RunningRobotState.Disabled }

    return state
}