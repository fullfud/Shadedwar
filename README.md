# Shadedwar

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=for-the-badge" alt="Minecraft 1.20.1" />
  <img src="https://img.shields.io/badge/Forge-47%2B-F58220?style=for-the-badge" alt="Forge 47+" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/GeckoLib-4.3.1-2D7FF9?style=for-the-badge" alt="GeckoLib 4.3.1" />
  <img src="https://img.shields.io/badge/Status-Beta-8A2BE2?style=for-the-badge" alt="Beta" />
  <img src="https://img.shields.io/badge/License-Custom-lightgrey?style=for-the-badge" alt="Custom License" />
</p>

Shadedwar is a Forge mod for Minecraft 1.20.1 focused on realistic drone gameplay.
It combines Shahed-style UAV systems, FPV drones, controller support, custom HUD and
shader work, long-range chunk-aware control, and REB gameplay elements in one package.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Prerequisites](#prerequisites)
- [Build and Run](#build-and-run)
- [In-Game Usage](#in-game-usage)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

## Overview

This repository contains the source for the `fullfud` mod id, published in-game as
`Shadedwar`. The project is built on Forge 1.20.1 with GeckoLib animation support.

The current gameplay focus includes:

- Shahed drones, launcher tools, and monitoring workflows
- FPV drones with goggles, controller linking, and first-person flight
- Controller hot-plug detection with in-game calibration flow
- Custom FPV HUD, post-processing shaders, and drone camera hooks
- REB emitter and battery systems
- Server-authoritative drone entities with chunk-aware long-range control

## Features

### Flight and Drone Systems

- Multiple FPV drone presets:
  - `7 Inch 6S`
  - `Tiny Whoop`
  - `7 Inch 6S Strike`
- FPV goggles and controller linking flow
- Arm/disarm controls for active drones
- Custom FPV flight camera, HUD telemetry, and post shader effects
- Local controller calibration with automatic popup when a new controller is connected

### Combat and Utility Systems

- Shahed drone variants:
  - white
  - black
  - slow variants
- Shahed launcher entity and monitor UI
- REB emitter and battery items

## Prerequisites

Before building or running the mod, make sure you have:

- Java 17 installed
- A Forge 1.20.1 modding environment
- Internet access for Gradle dependency resolution

## Build and Run

### Build the Mod

```bat
.\gradlew.bat build
```

The reobfuscated mod jar is generated under `build\libs\`.

### Run a Development Client

```bat
.\gradlew.bat runClient
```

Or use the helper script:

```bat
runTestClient.bat
```

### Useful Gradle Tasks

```bat
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runData
.\gradlew.bat buildClean
```

## In-Game Usage

### FPV Workflow

1. Obtain an FPV drone, FPV controller, and FPV goggles.
2. Link the controller and goggles to the drone.
3. If a controller is connected for the first time, the calibration screen opens automatically.
4. Calibrate sticks and arm input, then reopen calibration later with the calibration keybind if needed.
5. Arm the drone and enter FPV control mode.

### Shahed Workflow

1. Place or deploy a Shahed drone.
2. Use the monitor item to link to the drone.
3. Open the monitor and control the drone remotely.

## Project Structure

The most important paths for contributors are:

- `src/main/java/com/fullfud/fullfud/client`
  - HUD, camera hooks, calibration screens, client input, render integration
- `src/main/java/com/fullfud/fullfud/common/entity`
  - drone entities and gameplay state
- `src/main/java/com/fullfud/fullfud/common/entity/drone`
  - FPV presets and flight physics runtime
- `src/main/java/com/fullfud/fullfud/core`
  - registries, networking, chunk loading, config wiring
- `src/main/resources`
  - assets, lang files, shaders, and `mods.toml`

## Contributing

Contributions are welcome, especially for:

- FPV physics parity and controller handling
- bug fixes and gameplay polish
- localization improvements
- UI, HUD, and shader refinements

If you want to help:

1. Fork the repository.
2. Create a feature branch.
3. Make focused changes with clear commit messages.
4. Open a pull request with a short explanation of what changed and why.

When changing gameplay or FPV systems, include testing notes when possible.

## License

This project uses a custom license. See [LICENSE](LICENSE) for the full text.
For bundled third-party fonts, OSD glyph resources, and other imported vendor
materials, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Short version:

- personal gameplay use is allowed
- non-commercial servers are allowed under the license terms
- redistribution and derivatives must keep attribution and the same license terms
- commercial use requires explicit written permission from `fullfud`
- `FrontlineMC` has a specific additional grant described in the license
- third-party fonts, OSD font/glyph assets, and other imported vendor resources are not covered by the custom license and remain subject to their upstream terms
