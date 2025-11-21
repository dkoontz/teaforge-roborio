package teaforge.platform.RoboRio.internal

import edu.wpi.first.hal.HALUtil
import edu.wpi.first.wpilibj.DigitalInput
import edu.wpi.first.wpilibj.RobotState
import teaforge.platform.RoboRio.AnalogPort
import teaforge.platform.RoboRio.DioPort
import teaforge.platform.RoboRio.DioPortState
import teaforge.platform.RoboRio.PwmPort
import teaforge.platform.RoboRio.RunningRobotState

internal fun digitalIoPortToInt(port: DioPort): Int {
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

internal fun analogPortToInt(port: AnalogPort): Int {
    return when (port) {
        AnalogPort.Zero -> 0
        AnalogPort.One -> 1
        AnalogPort.Two -> 2
        AnalogPort.Three -> 3
    }
}

internal fun pwmPortToInt(port: PwmPort): Int {
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

internal fun log(msg: String) {
    val elapsedMicroseconds = HALUtil.getFPGATime()
    val elapsedMilliseconds = elapsedMicroseconds.div(1_000L).mod(1_000L)
    val elapsedSeconds = elapsedMicroseconds.div(1_000_000L).mod(60L)
    val elapsedMinutes = elapsedMicroseconds.div(60_000_000L).mod(60L)
    val elapsedHours = elapsedMicroseconds.div(3_600_000_000L)

    println(
        "[${String.format(
            "%02d:%02d:%02d:%03d",
            elapsedHours,
            elapsedMinutes,
            elapsedSeconds,
            elapsedMilliseconds,
        )}] $msg",
    )
}

internal fun getDioPortValue(port: DigitalInput): DioPortState =
    if (port.get()) {
        DioPortState.HIGH
    } else {
        DioPortState.LOW
    }

internal fun getRunningRobotState(): RunningRobotState {
    val state: RunningRobotState =
        if (RobotState.isDisabled()) {
            RunningRobotState.Disabled
        } else if (RobotState.isTeleop()) {
            RunningRobotState.Teleop
        } else if (RobotState.isAutonomous()) {
            RunningRobotState.Autonomous
        } else if (RobotState.isTest()) {
            RunningRobotState.Test
        } else if (RobotState.isEStopped()) {
            RunningRobotState.EStopped
        } else {
            RunningRobotState.Unknown
        }

    return state
}