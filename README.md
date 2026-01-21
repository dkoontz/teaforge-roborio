# RoboRio Platform Implementation and Usage Guide

This guide explains how to use the RoboRio platform implementation of the Teaforge framework to create applications for the RoboRio development board used in FIRST Robotics Competition (FRC).

## Introduction to the RoboRio Platform

The RoboRio implementation provides a bridge between the Teaforge framework and the WPILib framework used in FRC robotics. It allows you to build robot applications using the clean, functional architecture of Teaforge while leveraging the hardware capabilities of the RoboRio controller.

## Key Components of the RoboRio Platform

The RoboRio platform implementation consists of:

1. **RoboRio Hardware Types**: There are several types that enumerate the hardware available on the RoboRio such as DIO ports, Analog ports, and PWM ports.
2. **Resource Tokens**: Type-safe tokens that represent initialized hardware resources and must be obtained before using hardware.
3. **Effects**: Actions a program can tell the RoboRio to perform (initialize resources, control motors, turn on/off DIO pins, log messages, etc.)
4. **Subscriptions**: Input sources a program wants to be informed about (button presses, sensor readings, timers, etc.)
5. **WpiLib Integration**: Runs a program using one of the WPILib execution models such as TimedRobot

## Resource Token System

The RoboRio platform uses a resource token system to ensure type-safe and explicit hardware initialization. Before using any hardware resource, you must first initialize it to obtain a token. This token is then used for all subsequent operations on that resource.

Available resource tokens:
- `DigitalInputToken` - For reading digital input ports
- `DigitalOutputToken` - For controlling digital output ports
- `AnalogInputToken` - For reading analog input ports
- `AnalogOutputToken` - For controlling analog output ports
- `PwmOutputToken` - For controlling PWM devices (motors, servos)
- `HidInputToken` - For reading HID devices (controllers, joysticks)
- `CanDeviceToken` - For CAN bus devices (motors, encoders, IMUs)
- `OrchestraToken` - For playing music through motors

## Available Effects

The RoboRio platform provides effects that allow you to initialize and control the robot's hardware.

### Initialization Effects
- `InitDigitalPortForInput` - Initialize a digital port for reading input (returns `DigitalInputToken`)
- `InitDigitalPortForOutput` - Initialize a digital port for output (returns `DigitalOutputToken`)
- `InitAnalogPortForInput` - Initialize an analog port for reading input (returns `AnalogInputToken`)
- `InitAnalogPortForOutput` - Initialize an analog port for output (returns `AnalogOutputToken`)
- `InitPwmPortForOutput` - Initialize a PWM port for motor/servo control (returns `PwmOutputToken`)
- `InitHidPortForInput` - Initialize a HID device port for controller/joystick input (returns `HidInputToken`)
- `InitCanDevice` - Initialize a CAN bus device (returns `CanDeviceToken`)

### Control Effects
- `SetDigitalPortState` - Set the state (HIGH/LOW) of a digital output port
- `SetAnalogPortVoltage` - Set the voltage of an analog output port
- `SetPwmValue` - Set the speed/position of a PWM device
- `SetCanMotorSpeed` - Set the speed of a CAN motor
- `Log` - Log a message to the console

### File Effects
- `ReadFile` - Read a file from the filesystem

### Music Effects
- `LoadSong` - Load a music file to play through motors (returns `OrchestraToken`)
- `PlaySong` - Play loaded music
- `StopSong` - Stop playing music

### Port Effects
- `ForwardPort` - Forward local ports to another host or port

## Available Subscriptions

The RoboRio platform provides subscriptions to receive data on an ongoing basis.

### Port Subscriptions
- `DigitalPortValue` - Read digital port value at regular intervals
- `DigitalPortValueChanged` - Trigger when digital port value changes
- `AnalogPortValue` - Read analog port voltage at regular intervals
- `AnalogPortValueChanged` - Trigger when analog port voltage changes

### Input Device Subscriptions
- `HidPortValue` - Read HID device (controller/joystick) state at regular intervals
- `HidPortValueChanged` - Trigger when HID device state changes

### CAN Device Subscriptions
- `CANcoderValue` - Read CANcoder position at regular intervals
- `PigeonValue` - Read Pigeon IMU orientation at regular intervals

### System Subscriptions
- `RobotState` - Read current robot state (Disabled/Teleop/Autonomous/Test)
- `RobotStateChanged` - Trigger when robot state changes
- `Interval` - Trigger at regular time intervals

## Hardware Enumerations

The RoboRio platform provides typed enums for hardware ports:

```kotlin
// Digital I/O ports
enum class DioPort { Zero, One, Two, Three, Four, Five, Six, Seven, Eight, Nine }

// Analog ports
enum class AnalogPort { Zero, One, Two, Three }

// PWM ports for motor controllers and servos
enum class PwmPort { Zero, One, Two, Three, Four, Five, Six, Seven, Eight, Nine }

// Digital port states
enum class DioPortState { HIGH, LOW }
```

## Step-by-Step Guide to Create a RoboRio Robot Program

### 1. Define Your Robot's Model

First, define a data class representing your robot's state. This should include any hardware tokens you'll need to use:

```kotlin
data class RobotModel(
    val motorToken: Maybe<PwmOutputToken>,
    val enableButtonToken: Maybe<DigitalInputToken>,
    val speedSensorToken: Maybe<AnalogInputToken>,
    val controllerToken: Maybe<HidInputToken>,
    val motorEnabled: Boolean,
    val targetSpeed: Double,
    // Add other state variables your robot needs
)
```

### 2. Define Messages Your Robot Will Handle

Create a sealed interface for all the messages your robot can process. Include messages for hardware initialization results:

```kotlin
sealed interface RobotMessage {
    // Hardware initialization results
    data class MotorInitialized(val result: Result<PwmOutputToken, Error>) : RobotMessage
    data class ButtonInitialized(val result: Result<DigitalInputToken, Error>) : RobotMessage
    data class SensorInitialized(val result: Result<AnalogInputToken, Error>) : RobotMessage
    data class ControllerInitialized(val result: Result<HidInputToken, Error>) : RobotMessage
    
    // Input events
    data class EnableButtonChanged(val oldState: DioPortState, val newState: DioPortState) : RobotMessage
    data class SpeedSensorReading(val voltage: Double) : RobotMessage
    data class DriverInput(val controllerState: HidValue) : RobotMessage
    
    // Add other message types
}
```

### 3. Implement the Init Function

Create an initialization function that sets up your robot's initial state and initializes hardware resources:

```kotlin
fun initRobot(args: List<String>): Pair<RobotModel, List<Effect<RobotMessage>>> {
    // Create initial model with no tokens yet
    val initialModel = RobotModel(
        motorToken = Maybe.None,
        enableButtonToken = Maybe.None,
        speedSensorToken = Maybe.None,
        controllerToken = Maybe.None,
        motorEnabled = false,
        targetSpeed = 0.0
    )
    
    // Initialize hardware resources
    val initialEffects = listOf(
        Effect.Log("Robot initializing..."),
        Effect.InitPwmPortForOutput(
            port = PwmPort.Zero,
            initialSpeed = 0.0,
            message = { result -> RobotMessage.MotorInitialized(result) }
        ),
        Effect.InitDigitalPortForInput(
            port = DioPort.Zero,
            message = { result -> RobotMessage.ButtonInitialized(result) }
        ),
        Effect.InitAnalogPortForInput(
            port = AnalogPort.Zero,
            message = { result -> RobotMessage.SensorInitialized(result) }
        ),
        Effect.InitHidPortForInput(
            port = 0,
            message = { result -> RobotMessage.ControllerInitialized(result) }
        )
    )
    
    // Initialize hardware resources
    val initialEffects = listOf(
        Effect.Log("Robot initializing..."),
        Effect.InitPwmPortForOutput(
            port = PwmPort.Zero,
            initialSpeed = 0.0,
            message = { result -> RobotMessage.MotorInitialized(result) }
        ),
        Effect.InitDigitalPortForInput(
            port = DioPort.Zero,
            message = { result -> RobotMessage.ButtonInitialized(result) }
        ),
        Effect.InitAnalogPortForInput(
            port = AnalogPort.Zero,
            message = { result -> RobotMessage.SensorInitialized(result) }
        )
    )
    
    return Pair(initialModel, initialEffects)
}
```

### 4. Implement the Update Function

Create an update function that handles messages and updates your robot's state. Handle hardware initialization results and store tokens:

```kotlin
fun updateRobot(
    msg: RobotMessage, 
    model: RobotModel
): Pair<RobotModel, List<Effect<RobotMessage>>> {
    return when (msg) {
        is RobotMessage.MotorInitialized -> {
            when (msg.result) {
                is Result.Ok -> {
                    val newModel = model.copy(motorToken = Maybe.Some(msg.result.value))
                    Pair(newModel, listOf(Effect.Log("Motor initialized successfully")))
                }
                is Result.Err -> {
                    Pair(model, listOf(Effect.Log("Motor initialization failed: ${msg.result.error}")))
                }
            }
        }
        
        is RobotMessage.ButtonInitialized -> {
            when (msg.result) {
                is Result.Ok -> {
                    val newModel = model.copy(enableButtonToken = Maybe.Some(msg.result.value))
                    Pair(newModel, listOf(Effect.Log("Button initialized successfully")))
                }
                is Result.Err -> {
                    Pair(model, listOf(Effect.Log("Button initialization failed: ${msg.result.error}")))
                }
            }
        }
        
        is RobotMessage.SensorInitialized -> {
            when (msg.result) {
                is Result.Ok -> {
                    val newModel = model.copy(speedSensorToken = Maybe.Some(msg.result.value))
                    Pair(newModel, listOf(Effect.Log("Sensor initialized successfully")))
                }
                is Result.Err -> {
                    Pair(model, listOf(Effect.Log("Sensor initialization failed: ${msg.result.error}")))
                }
            }
        }
        
        is RobotMessage.ControllerInitialized -> {
            when (msg.result) {
                is Result.Ok -> {
                    val newModel = model.copy(controllerToken = Maybe.Some(msg.result.value))
                    Pair(newModel, listOf(Effect.Log("Controller initialized successfully")))
                }
                is Result.Err -> {
                    Pair(model, listOf(Effect.Log("Controller initialization failed: ${msg.result.error}")))
                }
            }
        }
        
        is RobotMessage.EnableButtonChanged -> {
            // Update model based on button state
            val newModel = when (msg.newState) {
                DioPortState.HIGH -> model.copy(motorEnabled = true)
                DioPortState.LOW -> model.copy(motorEnabled = false)
            }
            
            // Return updated model and any effects
            Pair(newModel, emptyList())
        }
        
        is RobotMessage.SpeedSensorReading -> {
            // Calculate motor speed from sensor reading
            val normalizedSpeed = msg.voltage / 5.0 // Example normalization
            val updatedModel = model.copy(targetSpeed = normalizedSpeed)
            
            // Set motor speed if enabled and token is available
            val effects = when {
                model.motorEnabled && model.motorToken is Maybe.Some -> {
                    listOf(
                        Effect.SetPwmValue(
                            token = model.motorToken.value,
                            value = normalizedSpeed,
                            message = { result -> 
                                when (result) {
                                    is Result.Ok -> RobotMessage.Log("Motor speed set")
                                    is Result.Err -> RobotMessage.Log("Failed to set motor: ${result.error}")
                                }
                            }
                        ),
                        Effect.Log("Setting motor speed to: $normalizedSpeed")
                    )
                }
                else -> emptyList()
            }
            
            Pair(updatedModel, effects)
        }
        
        is RobotMessage.DriverInput -> {
            // Process controller input
            // ...
            Pair(model, emptyList())
        }
    }
}
```

### 5. Define Subscriptions

Create a function that returns the hardware subscriptions your robot needs. Subscriptions require tokens, so only subscribe to resources that have been initialized:

```kotlin
fun robotSubscriptions(model: RobotModel): List<Subscription<RobotMessage>> {
    val subscriptions = mutableListOf<Subscription<RobotMessage>>()
    
    // Only subscribe if the button token has been initialized
    if (model.enableButtonToken is Maybe.Some) {
        subscriptions.add(
            Subscription.DigitalPortValueChanged(
                token = model.enableButtonToken.value,
                message = { oldState, newState -> 
                    RobotMessage.EnableButtonChanged(oldState, newState) 
                }
            )
        )
    }
    
    // Only subscribe if the sensor token has been initialized
    if (model.speedSensorToken is Maybe.Some) {
        subscriptions.add(
            Subscription.AnalogPortValue(
                token = model.speedSensorToken.value,
                millisecondsBetweenReads = 100, // Poll every 100ms
                useAverageValue = true, // Use built-in RoboRio analog input averaging
                message = { voltage -> RobotMessage.SpeedSensorReading(voltage) }
            )
        )
    }
    
    // Only subscribe if the controller token has been initialized
    if (model.controllerToken is Maybe.Some) {
        subscriptions.add(
            Subscription.HidPortValue(
                token = model.controllerToken.value,
                message = { state -> RobotMessage.DriverInput(state) }
            )
        )
    }
    
    return subscriptions
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

## Understanding the Token System Workflow

The resource token system follows a specific workflow:

1. **Request Initialization**: Send an initialization effect with a message callback
2. **Receive Token**: Handle the initialization result message in your update function
3. **Store Token**: Save the token in your model if initialization succeeded
4. **Use Token**: Pass the token to subscriptions and control effects

Example workflow for a digital input:

```kotlin
// Step 1: Request initialization (in init function)
Effect.InitDigitalPortForInput(
    port = DioPort.Zero,
    message = { result -> RobotMessage.ButtonTokenReceived(result) }
)

// Step 2 & 3: Receive and store token (in update function)
is RobotMessage.ButtonTokenReceived -> {
    when (msg.result) {
        is Result.Ok -> {
            val newModel = model.copy(buttonToken = Maybe.Some(msg.result.value))
            Pair(newModel, listOf(Effect.Log("Button ready")))
        }
        is Result.Err -> {
            Pair(model, listOf(Effect.Log("Button init failed: ${msg.result.error}")))
        }
    }
}

// Step 4: Use token in subscriptions (in subscriptions function)
if (model.buttonToken is Maybe.Some) {
    Subscription.DigitalPortValueChanged(
        token = model.buttonToken.value,
        message = { old, new -> RobotMessage.ButtonChanged(old, new) }
    )
}
```

This pattern ensures that:
- Hardware is explicitly initialized before use
- Initialization failures are handled properly
- Port conflicts are prevented at runtime
- Type safety is maintained throughout

### Error Handling

The platform provides detailed error types for different failure scenarios:

**File Errors:**
- `Error.FileNotFound` - Requested file doesn't exist
- `Error.FileAccessDenied` - Permission denied to access file
- `Error.InvalidPath` - File path is malformed
- `Error.FileReadError` - Error occurred while reading file
- `Error.ReadOnlyFileSystem` - Attempted write on read-only filesystem

**Hardware Initialization Errors:**
- `Error.AlreadyInitialized` - Port or device already initialized
- `Error.PortInitializationError` - Generic port initialization failure
- `Error.DigitalPortError` - Digital port specific error
- `Error.AnalogPortError` - Analog port specific error
- `Error.PwmPortError` - PWM port specific error

**CAN Device Errors:**
- `Error.PhoenixError` - CTRE Phoenix library error (includes CAN ID and StatusCode)
- `Error.RevError` - REV Robotics library error (includes CAN ID and REVLibError)

Always handle errors in your update function to provide feedback and graceful degradation:

```kotlin
is RobotMessage.MotorInitialized -> {
    when (msg.result) {
        is Result.Ok -> {
            // Success case
            val newModel = model.copy(motorToken = msg.result.value)
            Pair(newModel, listOf(Effect.Log("Motor initialized successfully")))
        }
        is Result.Err -> {
            // Log the specific error
            val errorMsg = when (val err = msg.result.error) {
                is Error.PwmPortError -> 
                    "Failed to init PWM port ${err.port}: ${err.details}"
                is Error.AlreadyInitialized -> 
                    "Port already in use"
                else -> 
                    "Initialization error: $err"
            }
            Pair(model, listOf(Effect.Log(errorMsg)))
        }
    }
}
```

## Advanced Usage

### Working with CAN Devices

CAN devices (motors, encoders, IMUs) must also be initialized to obtain tokens:

```kotlin
// Initialize a Talon FX motor on CAN ID 1
Effect.InitCanDevice(
    type = CanDeviceType.Talon,
    id = 1,
    message = { deviceType, canId, result -> 
        RobotMessage.TalonMotorInitialized(result) 
    }
)

// Handle the initialization result
is RobotMessage.TalonMotorInitialized -> {
    when (msg.result) {
        is Result.Ok -> {
            val token = msg.result.value as CanDeviceToken.MotorToken.TalonMotorToken
            val newModel = model.copy(driveMotorToken = Maybe.Some(token))
            Pair(newModel, listOf(Effect.Log("Talon motor initialized")))
        }
        is Result.Err -> {
            Pair(model, listOf(Effect.Log("Talon init failed: ${msg.result.error}")))
        }
    }
}

// Control the motor using the token
if (model.driveMotorToken is Maybe.Some) {
    Effect.SetCanMotorSpeed(
        motor = model.driveMotorToken.value,
        value = 0.75
    )
}

// Subscribe to a CANcoder
if (model.encoderToken is Maybe.Some) {
    Subscription.CANcoderValue(
        token = model.encoderToken.value,
        millisecondsBetweenReads = 20,
        message = { position -> RobotMessage.EncoderPosition(position) }
    )
}
```

Available CAN device types:
- `CanDeviceType.Talon` - CTRE Talon FX motor controller
- `CanDeviceType.Neo` - REV Robotics NEO motor (via SparkMax)
- `CanDeviceType.Encoder` - CTRE CANcoder absolute encoder
- `CanDeviceType.Pigeon` - CTRE Pigeon 2 IMU

### Working with Controllers

HID devices (controllers and joysticks) follow the same initialization pattern as other hardware:

```kotlin
// First, initialize the HID port (in init or update)
Effect.InitHidPortForInput(
    port = 0, // Driver controller on port 0
    message = { result -> RobotMessage.ControllerInitialized(result) }
)

// Handle the initialization result
is RobotMessage.ControllerInitialized -> {
    when (msg.result) {
        is Result.Ok -> {
            val newModel = model.copy(controllerToken = msg.result.value)
            Pair(newModel, listOf(Effect.Log("Controller initialized")))
        }
        is Result.Err -> {
            Pair(model, listOf(Effect.Log("Controller init failed: ${msg.result.error}")))
        }
    }
}

// Then, in your subscriptions function (once token is available):
if (model.controllerToken is Maybe.Some) {
    // Subscribe to all controller updates
    Subscription.HidPortValue(
        token = model.controllerToken.value,
        message = { hidValue -> RobotMessage.DriverInput(hidValue) }
    )
    
    // Or subscribe to changes only
    Subscription.HidPortValueChanged(
        token = model.controllerToken.value,
        message = { oldValue, newValue -> 
            RobotMessage.DriverInputChanged(oldValue, newValue) 
        }
    )
}

// Access controller data in your update function
is RobotMessage.DriverInput -> {
    val leftStickY = msg.hidValue.axisValues[1] // Axis 1 is typically left Y
    val aButton = msg.hidValue.buttonValues[0] // Button 0 is typically A
    
    // Use the controller data to control your robot
    val effects = mutableListOf<Effect<RobotMessage>>()
    
    if (aButton && model.motorToken is Maybe.Some) {
        effects.add(
            Effect.SetPwmValue(
                token = model.motorToken.value,
                value = leftStickY,
                message = { result -> RobotMessage.MotorControlResult(result) }
            )
        )
    }
    
    Pair(model, effects)
}
```

The `HidValue` data class contains:
- `axisCount`: Number of axes on the device
- `buttonCount`: Number of buttons on the device
- `axisValues`: Array of axis values (typically -1.0 to 1.0)
- `buttonValues`: Array of button states (true = pressed, false = released)

### Handling Digital Inputs

Digital ports must be initialized before use. You can monitor digital inputs in two ways:

1. **Polling**: Using `DigitalPortValue` to read at regular intervals
2. **Event-based**: Using `DigitalPortValueChanged` to trigger only on state changes

Example of combining both approaches:

```kotlin
// First, initialize the digital port for input (in init or update)
Effect.InitDigitalPortForInput(
    port = DioPort.Zero,
    message = { result -> RobotMessage.ButtonTokenReceived(result) }
)

// Then, in your subscriptions function (once token is available):
if (model.buttonToken is Maybe.Some) {
    listOf(
        Subscription.DigitalPortValue(
            token = model.buttonToken.value,
            millisecondsBetweenReads = 1000, // Read and log every second
            message = { state -> RobotMessage.ButtonStatus(state) } // For logging
        ),
        Subscription.DigitalPortValueChanged(
            token = model.buttonToken.value,
            message = { oldState, newState -> 
                RobotMessage.ButtonStatusChanged(oldState, newState) 
            } // For immediate response
        )
    )
}
```

### Using Analog Inputs

Analog ports must be initialized before use. The `AnalogPortValue` subscription lets you read analog sensors:

```kotlin
// First, initialize the analog port for input (in init or update)
Effect.InitAnalogPortForInput(
    port = AnalogPort.Zero,
    message = { result -> RobotMessage.SensorTokenReceived(result) }
)

// Then, in your subscriptions function (once token is available):
if (model.sensorToken is Maybe.Some) {
    Subscription.AnalogPortValue(
        token = model.sensorToken.value,
        millisecondsBetweenReads = 50, // 20Hz reading rate
        useAverageValue = true, // Use averaged readings to reduce noise
        message = { voltage -> RobotMessage.DistanceSensorReading(voltage) }
    )
}
```

### Working with PWM Motors

PWM ports must be initialized before use. After initialization, you can control motor speed:

```kotlin
// First, initialize the PWM port (in init or update)
Effect.InitPwmPortForOutput(
    port = PwmPort.Zero,
    initialSpeed = 0.0,
    message = { result -> RobotMessage.MotorTokenReceived(result) }
)

// Then, control the motor using the token
if (model.motorToken is Maybe.Some) {
    Effect.SetPwmValue(
        token = model.motorToken.value,
        value = 0.5, // Speed from -1.0 to 1.0
        message = { result ->
            when (result) {
                is Result.Ok -> RobotMessage.MotorCommandSuccess
                is Result.Err -> RobotMessage.MotorCommandFailed(result.error)
            }
        }
    )
}
```

## Best Practices for RoboRio Applications

1. **Initialize Hardware Resources Early**: Initialize all hardware resources in the `init` function to obtain tokens.
2. **Store Tokens in Model**: Keep all hardware tokens in your robot's model for access in subscriptions and effects.
3. **Handle Initialization Failures**: Always handle both success and failure cases when initializing hardware.
4. **Check Token Availability**: Before creating subscriptions or effects that use hardware, verify the token is available.
5. **Separate Concerns**: Keep sensor reading, decision-making, and actuation logic separate.
6. **Use Meaningful Types**: Create domain-specific types for your robot's components.
7. **Test in Simulation**: Test your robot code in simulation before deploying to hardware.
8. **Throttle Updates**: Choose appropriate polling intervals to avoid overwhelming the CPU.
9. **Log Strategically**: Use logging for debugging during development but avoid excessive messages during competition.

## Troubleshooting Common Issues

- **Subscriptions Not Working**: Verify that the hardware token has been initialized and stored in the model before creating subscriptions.
- **"Port Already Allocated" Errors**: Ensure you're not initializing the same port multiple times or using a port for both input and output.
- **Hardware Not Responding**: Check that initialization effects returned success and tokens were stored correctly.
- **CPU Overutilization**: Increase your polling intervals if the robot becomes unresponsive.
- **Unexpected Motor Behavior**: Verify the correct PWM ports and check motor controller wiring. Ensure the motor token is initialized.
- **Missing Sensor Data**: Confirm analog/digital port tokens are initialized and subscriptions are created after initialization.
- **Controller Not Responding**: Ensure you are in Teleop mode in FRC Driver Station and the HID token is initialized.
