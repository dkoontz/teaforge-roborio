package teaforge.platform.RoboRio.internal

import edu.wpi.first.hal.HALUtil

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