package teaforge.platform.RoboRio.internal

import edu.wpi.first.wpilibj.TimedRobot
import teaforge.ProgramRunnerInstance
import teaforge.platform.RoboRio.Effect
import teaforge.platform.RoboRio.RoboRioProgram
import teaforge.platform.RoboRio.Subscription

class TimedRobotBasedPlatform<TMessage, TModel>(val program: RoboRioProgram<TMessage, TModel>) :
        TimedRobot() {

    private var runner:
            ProgramRunnerInstance<
                    Effect<TMessage>,
                    TMessage,
                    TModel,
                    RoboRioModel<TMessage, TModel>,
                    Subscription<TMessage>,
                    SubscriptionState<TMessage>>? =
            null

    override fun robotInit() {
        val roboRioArgs = listOf<String>()
        val programArgs = listOf<String>()
        runner = createRoboRioRunner(program, roboRioArgs, programArgs)
    }

    override fun robotPeriodic() {
        runner = runner?.let { teaforge.platform.stepProgram(it) }
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
