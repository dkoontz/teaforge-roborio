package teaforge.platform.RoboRio.internal

import com.ctre.phoenix6.hardware.TalonFX
import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.AnalogInput
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.motorcontrol.Spark
import teaforge.HistoryEntry
import teaforge.ProgramRunnerConfig
import teaforge.ProgramRunnerInstance
import teaforge.platform.*
import teaforge.platform.RoboRio.*
import teaforge.utils.Maybe



data class RoboRioModel<TMessage, TModel>(
        val messageHistory: List<HistoryEntry<TMessage, TModel>>,
        val pwmPorts: PwmPorts,
        val dioPorts: DioPorts,
        val analogInputs: AnalogPorts,
        val canMotors: CANMotors,
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

data class DioPorts(
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

data class AnalogPorts(
        val zero: AnalogInput,
        val one: AnalogInput,
        val two: AnalogInput,
        val three: AnalogInput,
)

data class CANMotors(
    val zero: TalonFX,
    val one: TalonFX,
    val two: TalonFX,
    val three: TalonFX,
    val four: TalonFX,
    val five: TalonFX,
)

fun <TMessage, TModel> initRoboRioRunner(args: List<String>): RoboRioModel<TMessage, TModel> {
    // Do any hardware initialization here
    val createDioEntry = { port: DioPort -> DigitalInput(digitalIoPortToInt(port)) }
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

    val dioPorts =
            DioPorts(
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

    val analogInputs =
            AnalogPorts(
                    zero = createAnalogPortEntry(AnalogPort.Zero),
                    one = createAnalogPortEntry(AnalogPort.One),
                    two = createAnalogPortEntry(AnalogPort.Two),
                    three = createAnalogPortEntry(AnalogPort.Three),
            )

    val canMotors =
        CANMotors(
            zero = TalonFX(canIdToInt(CANMotorID.Zero)),
            one = TalonFX(canIdToInt(CANMotorID.One)),
            two = TalonFX(canIdToInt(CANMotorID.Two)),
            three = TalonFX(canIdToInt(CANMotorID.Three)),
            four = TalonFX(canIdToInt(CANMotorID.Four)),
            five = TalonFX(canIdToInt(CANMotorID.Five)),
        )



    return RoboRioModel(
            messageHistory = emptyList(),
            pwmPorts = pwmPorts,
            dioPorts = dioPorts,
            analogInputs = analogInputs,
            canMotors = canMotors,
    )
}

fun <TMessage, TModel> processEffect(
        model: RoboRioModel<TMessage, TModel>,
        effect: Effect<TMessage>,
): Pair<RoboRioModel<TMessage, TModel>, Maybe<Nothing>> {
    return when (effect) {
        is Effect.Log -> {
            log(effect.msg)
            Pair(model, Maybe.None)
        }
        is Effect.SetPwmMotorSpeed -> {
            getPwmPort(effect.pwmSlot, model).set(effect.value)

            Pair(model, Maybe.None)
        }
        is Effect.SetCANBusMotorSpeed -> {
            getCANDevice(effect.deviceId, model).set(effect.value)

            Pair(model, Maybe.None)
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

fun canIdToInt(id : CANMotorID): Int {
    return when (id) {
        CANMotorID.Zero -> 0
        CANMotorID.One -> 1
        CANMotorID.Two -> 2
        CANMotorID.Three -> 3
        CANMotorID.Four -> 4
        CANMotorID.Five -> 5
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

fun <TMessage, TModel> getDioPort(
        port: DioPort,
        model: RoboRioModel<TMessage, TModel>
): DigitalInput {
    return when (port) {
        DioPort.Zero -> {
            model.dioPorts.zero
        }
        DioPort.One -> {
            model.dioPorts.one
        }
        DioPort.Two -> {
            model.dioPorts.two
        }
        DioPort.Three -> {
            model.dioPorts.three
        }
        DioPort.Four -> {
            model.dioPorts.four
        }
        DioPort.Five -> {
            model.dioPorts.five
        }
        DioPort.Six -> {
            model.dioPorts.six
        }
        DioPort.Seven -> {
            model.dioPorts.seven
        }
        DioPort.Eight -> {
            model.dioPorts.eight
        }
        DioPort.Nine -> {
            model.dioPorts.nine
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

fun <TMessage, TModel> getCANDevice(id : CANMotorID, model: RoboRioModel<TMessage, TModel>): TalonFX{
    return when (id){
        CANMotorID.Zero -> {
            model.canMotors.zero
        }
        CANMotorID.One -> {
            model.canMotors.one
        }
        CANMotorID.Two -> {
            model.canMotors.two
        }
        CANMotorID.Three -> {
            model.canMotors.three
        }
        CANMotorID.Four -> {
            model.canMotors.four
        }
        CANMotorID.Five -> {
            model.canMotors.five
        }
    }

}
