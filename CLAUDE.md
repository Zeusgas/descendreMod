# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

NeoForge mod for Minecraft 1.21.11 (NeoForge 21.11.42, Java 21) that extends the world's vertical range far beyond vanilla limits — from Y=-15000 to Y=+5000 — using a custom cubic chunk system layered on top of the standard world.

## Build & Run

```bash
# Build the mod JAR
./gradlew build

# Launch Minecraft client with the mod
./gradlew runClient

# Launch a headless Minecraft server
./gradlew runServer
```

The Gradle daemon uses up to 10 GB of RAM (`gradle.properties`). First build downloads NeoForge and decompiles Minecraft — it takes several minutes.

## Architecture

### Height system

The target player range is Y=-15000 to Y=+5000. Internally, limits are snapped to multiples of 16: -15008 to +5007. The `DimensionTypeHeightMixin` overrides `DimensionType.minY()`, `height()`, and `logicalHeight()` to expose this full range to Minecraft.

Constants are currently duplicated across three classes — `DescendreConstants`, `DescendreHeight`, and `DescendreCubicConfig` — and should eventually be consolidated.

### Cubic chunk data model

Blocks outside vanilla's build height are stored in a parallel in-memory data structure, not in Minecraft's chunk system:

- `DescendreCube` — a 16×16×16 array of `BlockState` (null = air). Tracks `nonAirCount` so empty cubes can be skipped.
- `CubePos` — cube-space coordinates (`blockCoord / 16`, using `Math.floorDiv`). Converts between block coords and local (0–15) coords.
- `CubeMap` — `HashMap<CubePos, DescendreCube>` for one dimension. Provides `getBlock` / `setBlock` by `BlockPos`, and `forEachNonAirBlock(AABB)` for collision queries.
- `DescendreCubeManager` — static registry mapping `ResourceKey<Level>` → `CubeMap`, one per loaded dimension.
- `DescendreCubicMode` — global boolean toggle for the system.

### Collision

`EntityCollisionMixin` redirects `Entity.collideBoundingBox` to append cubic `VoxelShape`s from `DescendreCollision.collect()` alongside vanilla block collisions. This mixin **is not currently registered** in `descendre.mixins.json` (only `DimensionTypeHeightMixin` and `EntityVoidKillMixin` are active).

### Void-kill prevention

`DescendreVoidKillMixin` and `EntityVoidKillMixin` cancel `checkBelowWorld` and `outOfWorld` for entities within the extended range. Only `EntityVoidKillMixin` is registered in `descendre.mixins.json`.

### In-game commands

All debug commands are under `/descendre cubic`:

| Command | Effect |
|---|---|
| `info` | Shows loaded cubes count, non-air blocks, height range |
| `clear` | Wipes the current dimension's CubeMap from RAM |
| `tp <x> <y> <z>` | Teleports the player and creates a stone platform |
| `get <x> <y> <z>` | Reads a block from the CubeMap |
| `set <x> <y> <z> <block>` | Writes a block to the CubeMap (and to the real level if in build height) |
| `platform <cx> <y> <cz> <radius> <block>` | Fills a square platform |

### Stub / unimplemented packages

Several packages exist as empty placeholders for planned features:

- `fr.descendre.storage` — `CubeSerializer`, `CubeStorage`, `Region3DFile`, `Region3DStorage` (disk persistence)
- `fr.descendre.network` — `DescendreNetwork`, `ClientboundCubeDataPacket`, `ClientboundCubeBlockUpdatePacket`, `ClientboundForgetCubePacket` (server→client sync)
- `fr.descendre.client` — `DescendreClientCubeCache`, `DescendreCubeRenderer`, `DescendreRenderSection`, `DescendreRenderDispatcher` (client rendering)
- `fr.descendre.server` — `DescendreServerHooks` (server lifecycle events)
- `fr.descendre.world.column` — `DescendreColumn`, `ColumnPos`, `ColumnHeightmap`
- `extended_backup/` — old prototype code, kept for reference, not on the active classpath

### Mixin registration

Mixins must be declared in `src/main/resources/descendre.mixins.json` to take effect. Adding a new mixin class without registering it there has no impact at runtime.