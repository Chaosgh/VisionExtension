# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the extension
./gradlew build

# Build and copy JAR to local server extensions folder
./gradlew copyJarToServer -PtypewriterExtensionDir="C:/path/to/Typewriter/extensions"
```

The JAR outputs to `build/libs/VisionExtension-{version}.jar` by default.
`copyJarToServer` also accepts the `TYPEWRITER_EXTENSION_DIR` environment variable.

## Project Overview

VisionExtension is a Kotlin-based Typewriter extension that provides NPC vision detection and patrol systems for Paper/Bukkit servers. It enables NPCs to detect players using field-of-view calculations, raycasts, and progressive detection mechanics.

**Key dependencies:**
- Typewriter engine (0.9.0-beta-174)
- EntityExtension and RoadNetworkExtension (compile-only)
- PacketEvents + EntityLib for client-side rendering

## Architecture

### Entry Points (Typewriter Entries)

All activities are registered via `@Entry` annotations:

| Entry ID | Class | Purpose |
|----------|-------|---------|
| `vision_activity` | `VisionActivityEntry` | Core vision detection |
| `patrol_vision_activity` | `PatrolVisionActivityEntry` | Sequential patrol + vision |
| `random_patrol_vision_activity` | `RandomPatrolVisionActivityEntry` | Random patrol + vision |
| `on_player_seen` | `PlayerSeenEntry` | Event trigger when player detected |
| `on_player_lost` | `PlayerLostEntry` | Event trigger when player is lost |

### Core Components

**de.chaos.entry** - Typewriter entry classes:
- `VisionActivityEntry`, `ActivityVisionEntry`, deprecated patrol wrappers
- `VisionConfigProvider`: maps entry fields to `VisionConfig`

**de.chaos.vision** - Core vision runtime:
- `VisionActivity`: per-tick detection orchestration
- `VisionConfig` / `NormalizedVisionConfig`: raw and runtime-normalized config
- `VisionSensor`: shape/FOV/raycast checks
- `DetectionTracker`, `DetectionProgressCalculator`, `DetectionIndicatorFormatter`

**de.chaos.activity** - Patrol + Vision composition:
- `PausableActivity<C>`: Wrapper that adds pause/resume to any EntityActivity
- `PatrolVisionActivity`: Combines patrol + vision, pauses patrol when seeing players

**de.chaos.display** - Packet-based rendering:
- Creates client-side ITEM_DISPLAY entities via PacketEvents/EntityLib
- Per-viewer entity pools with reuse
- Point displays, line displays, and text indicators
- `DisplayRuntime`: injectable PacketEvents/EntityLib adapter

### Detection Algorithm

The vision system runs per-tick with these steps:
1. Find players within radius
2. Check if player is within FOV (cone/line/sphere geometry)
3. Raycast for line-of-sight blocking
4. Apply progressive detection (separate rates for sneaking vs walking)
5. Decay detection progress when player leaves vision
6. Fire `PlayerSeenEvent` when detection completes (progress reaches 1.0)

Progressive detection formula:
```
time = base_min + (base_max - base_min) * distance_factor * angle_multiplier
progress_per_tick = 1 / (time * 20)
```

### Patrol System

Uses patrol activities from EntityExtension (not reimplemented locally):
- `PatrolActivity`: Sequential patrol from EntityExtension
- `RandomPatrolActivity`: Random patrol from EntityExtension
- Wrapped in `PausableActivity` to enable pause/resume
- Combined with vision via `PatrolVisionActivity`
- `stopWhenLooking` pauses patrol when NPC detects a player

## Configuration Notes

- `forcedLookEnabled/Yaw/Pitch`: Override NPC orientation when not tracking a player
- `showDisplays`: Enables debug visualization of vision cone
- `showDetectionIndicator`: Shows progress bar above NPC during detection
- Detection indicator uses `\u2588`/`\u2591` Unicode blocks for progress bars
