# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build          # Build (includes ktlint format check)
./gradlew test           # Run tests
./gradlew ktlintCheck    # Check code formatting
./gradlew ktlintFormat   # Auto-format code
./gradlew publish        # Publish to Maven registry
./gradlew githubRelease  # Create GitHub release
./gradlew installGitHooks  # Install pre-push ktlint hook
```

**Code style:** ktlint official style, 120-char line limit, 4-space indent. Pre-push hook enforces formatting.

## Architecture

This is a Kotlin library that bridges the **Teaforge functional framework** with **WPILib** for FIRST Robotics Competition (FRC) robots running on RoboRio hardware.

### Core pattern: Model-Update-Effects

User code defines three functions:
- `init` → initial `(RobotModel, List<Effect>)`
- `update(Msg, Model)` → `(Model, List<Effect>)`
- `subscriptions(Model)` → `List<Subscription>`

The runtime loop in `TimedRobotBasedPlatform` (extends WPILib's `TimedRobot`) calls `stepProgram` every `robotPeriodic()` cycle (~20ms). Effects are executed and subscriptions are polled by `TeaforgeImplementation`.

### Token-based resource management

Hardware must be initialized before use. An init `Effect` (e.g. `Effect.InitTalonFx(...)`) triggers allocation and returns a token via a `Message`. The token is stored in the model and passed to subsequent effects/subscriptions. Attempting to use hardware without a token is a type error.

```kotlin
// 1. Request init
Effect.InitTalonFx(canId = 1, canBus = "rio")

// 2. Receive token in update()
is Message.TalonFxInitResult -> model.copy(talonToken = msg.result)

// 3. Use token
Effect.SetCanMotorSpeed(token = model.talonToken.value, speed = 0.5)
```

### Key files

| File | Purpose |
|------|---------|
| `src/main/kotlin/teaforge/platform/RoboRio/Main.kt` | All `Effect` and `Subscription` sealed interfaces (~30+ effects, ~20+ subscriptions) |
| `src/main/kotlin/teaforge/platform/RoboRio/Types.kt` | Error types, hardware port enums (`DioPort`, `AnalogPort`, `PwmPort`), token data classes |
| `src/main/kotlin/teaforge/platform/RoboRio/internal/TeaforgeImplementation.kt` | Effect execution and subscription state management |
| `src/main/kotlin/teaforge/platform/RoboRio/internal/Subscriptions.kt` | Subscription polling and event detection logic |
| `src/main/kotlin/teaforge/platform/RoboRio/internal/TimedRobotBasedPlatform.kt` | WPILib integration entry point |

### Hardware support

- **Motors:** CTRE Talon FX (Phoenix 6 + legacy v5), REV SparkMax (NEO)
- **Sensors:** CANcoder, Pigeon 2 IMU, CANrange distance sensor, analog/digital I/O, PWM
- **HID:** Joysticks, gamepads
- **Network:** WebSocket (Ktor), TCP (ZeroMQ), WPILib NetworkTables, Serial
- **Other:** PWM servos with configurable pulse width, Talon FX Orchestra (music/sounds), port forwarding

### Publishing

Version is set in `build.gradle.kts`. Releases are created via the manual GitHub Actions workflow (`release.yml`), which validates the version tag, builds, publishes to GitHub Packages Maven registry, and creates a GitHub release. The Teaforge core framework is included as a local jar (`teaforge-0.1.8.jar`).
