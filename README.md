# RoboRio Platform Implementation and Usage Guide

This guide explains how to use the RoboRio platform implementation of the Teaforge framework to create applications for the RoboRio development board used in FIRST Robotics Competition (FRC).

## Introduction to the RoboRio Platform

The RoboRio implementation provides a bridge between the Teaforge framework and the WPILib framework used in FRC robotics. It allows you to build robot applications using the clean, functional architecture of Teaforge while leveraging the hardware capabilities of the RoboRio controller.

## Key Components of the RoboRio Platform

The RoboRio platform implementation consists of:

1. **RoboRio Hardware Types**: There are several types that enumerate the hardware available on the RoboRio such as DIO ports, Analog ports, and PWM ports.
2. **Effects**: Actions a program can tell the RoboRio to perfor (control motors, turn on/off DIO pins, log messages, etc.)
3. **Subscriptions**: Input sources a program wants to be informed about (button presses, sensor readings, timers, etc.)
4. **WpiLib Integration**: Runs an a program using one of the WPILib execution models such as TimedRobot

## Available Effects

The RoboRio platform provides effects that allow you to control the robot's hardware.

- Set a PWM motor
- Log messages to the console

TODO: Add in reference list of effects as the platform is full built out

## Available Subscriptions

The RoboRio platform provides subscriptions to receive data on an ongoing basis.

- DIO and Analog port values
- Human Interface Device inputs (Controllers, Joysticks, etc.)
- Timer events

TODO: Add in reference list of effects as the platform is full built out

## Hardware Enumerations

The RoboRio platform provides typed enums for hardware ports:

```kotlin
// PWM ports for motor controllers
enum class PwmPort { Zero, One, Two, Three, Four, Five, Six, Seven, Eight, Nine }

// Digital I/O ports
enum class DioPort { Zero, One, Two, Three, Four, Five, Six, Seven, Eight, Nine }

// Analog input ports
enum class AnalogPort { Zero, One, Two, Three }
```

## Step-by-Step Guide to Create a RoboRio Robot Program

### 1. Define Your Robot's Model

First, define a data class representing your robot's state:

```kotlin
data class RobotModel(
    val motorEnabled: Boolean,
    val targetSpeed: Double,
    // Add other state variables your robot needs
)
```

### 2. Define Messages Your Robot Will Handle

Create a sealed interface for all the messages your robot can process:

```kotlin
sealed interface RobotMessage {
    // Button press event
    data class EnableButtonChanged(val state: DioPortStatus) : RobotMessage
    
    // Sensor reading event
    data class SpeedSensorReading(val voltage: Double) : RobotMessage
    
    // Controller input event
    data class DriverInput(val controllerState: HidValue) : RobotMessage
    
    // Add other message types
}
```

### 3. Implement the Init Function

Create an initialization function that sets up your robot's initial state:

```kotlin
fun initRobot(args: List<String>): Pair<RobotModel, List<Effect<RobotMessage>>> {
    // Create initial model
    val initialModel = RobotModel(
        motorEnabled = false,
        targetSpeed = 0.0
    )
    
    // Optional initial effects (like logging startup)
    val initialEffects = listOf(
        Effect.Log("Robot initialized")
    )
    
    return Pair(initialModel, initialEffects)
}
```

### 4. Implement the Update Function

Create an update function that handles messages and updates your robot's state:

```kotlin
fun updateRobot(
    msg: RobotMessage, 
    model: RobotModel
): Pair<RobotModel, List<Effect<RobotMessage>>> {
    return when (msg) {
        is RobotMessage.EnableButtonChanged -> {
            // Update model based on button state
            val newModel = when (msg.state) {
                DioPortStatus.Closed -> model.copy(motorEnabled = true)
                DioPortStatus.Open -> model.copy(motorEnabled = false)
            }
            
            // Return updated model and any effects
            Pair(newModel, emptyList())
        }
        
        is RobotMessage.SpeedSensorReading -> {
            // Calculate motor speed from sensor reading
            val normalizedSpeed = msg.voltage / 5.0 // Example normalization
            val updatedModel = model.copy(targetSpeed = normalizedSpeed)
            
            // Set motor speed if enabled
            val effects = if (model.motorEnabled) {
                listOf(
                    Effect.SetPwmMotorSpeed(PwmPort.Zero, normalizedSpeed),
                    Effect.Log("Setting motor speed to: $normalizedSpeed")
                )
            } else {
                emptyList()
            }
            
            Pair(updatedModel, effects)
        }
        
        is RobotMessage.DriverInput -> {
            // Process controller input
            // ...
            Pair(updatedModel, emptyList())
        }
    }
}
```

### 5. Define Subscriptions

Create a function that returns the hardware subscriptions your robot needs:

```kotlin
fun robotSubscriptions(model: RobotModel): List<Subscription<RobotMessage>> {
    return listOf(
        // Subscribe to a button on digital input 0
        Subscription.DioPortValueChanged(
            port = DioPort.Zero,
            message = { state -> RobotMessage.EnableButtonChanged(state) }
        ),
        
        // Subscribe to an analog sensor on analog input 0
        Subscription.AnalogInputValue(
            port = AnalogPort.Zero,
            millisecondsBetweenReads = 100, // Poll every 100ms
            useAverageValue = true, // Use built-in RoboRio analog input averaging
            message = { voltage -> RobotMessage.SpeedSensorReading(voltage) }
        ),
        
        // Subscribe to driver controller on HID port 0
        Subscription.HidPortValue(
            port = 0,
            message = { state -> RobotMessage.DriverInput(state) }
        )
    )
}
```

### 6. Create the Robot Program

Combine all components into a RoboRio program:

```kotlin
val robotProgram = RoboRioProgram(
    init = ::initRobot,
    update = ::updateRobot, 
    subscriptions = ::robotSubscriptions
)
```

### 7. Connect to WPILib's Main Entry Point

In your `Main.kt` file, connect your robot program to the WPILib entry point:

```kotlin
package frc.robot

import edu.wpi.first.wpilibj.RobotBase
import teaforge.platform.RoboRio.timedRobotProgram

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        RobotBase.startRobot {
            // This connects your Teaforge robot program to the WPILib framework
            teaforge.platform.RoboRio.timedRobotProgram(robotProgram)
        }
    }
}
```

## Advanced Usage

### Working with Controllers

TODO: Section on HidValue and mapping it to a specific controller type as part of the message or in the update

### Handling Digital Inputs

You can monitor digital inputs in two ways:

1. **Polling**: Using `DioPortValue` to read at regular intervals
2. **Event-based**: Using `DioPortValueChanged` to trigger only on state changes

Example of combining both approaches:

```kotlin
// In your subscriptions function:
Subscription.DioPortValue(
    port = DioPort.Zero,
    millisecondsBetweenReads = 1000, // Read and log every second
    message = { state -> RobotMessage.ButtonStatus(state) } // For logging
),
Subscription.DioPortValueChanged(
    port = DioPort.Zero,
    message = { state -> RobotMessage.ButtonStatusChanged(state) } // For immediate response
)
```

### Using Analog Inputs

The `AnalogInputValue` subscription lets you read analog sensors:

```kotlin
Subscription.AnalogInputValue(
    port = AnalogPort.Zero,
    millisecondsBetweenReads = 50, // 20Hz reading rate
    useAverageValue = true, // Use averaged readings to reduce noise
    message = { voltage -> RobotMessage.DistanceSensorReading(voltage) }
)
```

## Best Practices for RoboRio Applications

1. **Separate Concerns**: Keep sensor reading, decision-making, and actuation logic separate.
2. **Use Meaningful Types**: Create domain-specific types for your robot's components.
3. **Test in Simulation**: Test your robot code in simulation before deploying to hardware.
4. **Throttle Updates**: Choose appropriate polling intervals to avoid overwhelming the CPU.
5. **Log Strategically**: Use logging for debugging during development but avoid excessive messages during competition.

## Troubleshooting Common Issues

- **CPU Overutilization**: Increase your polling intervals if the robot becomes unresponsive.
- **Unexpected Motor Behavior**: Verify the correct PWM ports and check motor controller wiring.
- **Missing Sensor Data**: Confirm analog/digital port configurations and wiring connections.
- **Controller Not Responding**: Ensure you are in Teleop mode in FRC Driver Station.
