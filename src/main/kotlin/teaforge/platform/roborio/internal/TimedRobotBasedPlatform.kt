package teaforge.platform.roborio.internal

import edu.wpi.first.wpilibj.TimedRobot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import teaforge.ProgramRunnerInstance
import teaforge.platform.roborio.Effect
import teaforge.platform.roborio.RoboRioProgramConfig
import teaforge.platform.roborio.Subscription

class TimedRobotBasedPlatform<TMessage, TModel>(
    val config: RoboRioProgramConfig<TMessage, TModel>,
) : TimedRobot() {
    private var runner:
        ProgramRunnerInstance<
            Effect<TMessage>,
            TMessage,
            TModel,
            RoboRioModel<TMessage, TModel>,
            Subscription<TMessage>,
            SubscriptionState<TMessage>,
            >? =
        null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun robotInit() {
        val roboRioArgs = listOf<String>()
        val programArgs = listOf<String>()
        runner = createRoboRioRunner(config.program, config.debugLogging, roboRioArgs, programArgs)
    }

    override fun robotPeriodic() {
        runner = runner?.let { teaforge.platform.stepProgram(it, scope) }
    }

    override fun disabledInit() {}

    override fun disabledPeriodic() {}

    override fun disabledExit() {}

    override fun autonomousInit() {}

    override fun autonomousPeriodic() {}

    override fun autonomousExit() {}

    override fun teleopInit() {}

    override fun teleopPeriodic() {}

    override fun teleopExit() {}

    override fun testInit() {}

    override fun testPeriodic() {}

    override fun testExit() {}
}
