# Technical specification

## Identity and platform

- Display name: **Create: Linear Engineering**.
- Registry namespace: `create_linear_engineering`.
- Java package: `io.github.brzmaster.createlinearengineering`.
- Main entry point: `CreateLinearEngineering`.
- Artifact base name: `create-linear-engineering`.
- Baseline: Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10, Sable 2.0.5 and Java 21.

Registry identity is persistent world data. There is no automatic namespace migration. Install the same build and compatible dependencies on both sides of a multiplayer connection.

## Geometry and placement

Rails have an inverted-T profile; carriages wrap around it with a complementary U. Rail and carriage together fit within one block in height. Carriages span 16/16 along the guide. Screw variants retain the base design, with a four-pixel shaft and a wider toothed inner channel.

Facing and mounting normal form a shared orthogonal frame for models, collision shapes and physics joints. Twenty-four signed frames support floor, ceiling and wall mounting. The implementation constructs frame quaternions explicitly to avoid the affected `Quaterniond.rotationX` implementation in JOML 1.10.5.

Create's `PoleHelper` and `PlacementHelpers` provide arrows, previews, line extension and Extendo Grip behavior. Carriage placement checks the matching rail family, occupancy, permissions and precise oriented overlap before allocating a Sable body. No temporary floating carriage block is placed in the ordinary world.

## Body and joint lifecycle

Sable/Rapier owns mass, gravity, collision response, synchronization and body transforms. A native `GenericConstraint` constrains two translations and all three rotations, leaving the rail axis available for motion. The screw variant controls that remaining axis with the same native joint.

Support checks inspect rail segments under the moving carriage, rather than scanning an entire line. Once the body fully leaves its guide, the joint is released. Compatible aligned contact can create a new joint using Sable's spatial lookup and pose APIs. Opposite signed directions along the same rail axis are accepted when the mounting normal agrees.

Persistent, bidirectional references let Sable reconstruct connected groups. Transfers preserve descendant bodies. Absolute, serialized snapshots are applied after Sable finalizes the transferred pose, avoiding repeated transforms.

## Physical stops and drive behavior

Collision geometry in the world or rail parent's frame constrains the native joint before the physics step. Payload blocks are included. Empty open ends remain unrestricted. Native Sable contacts handle independent moving bodies; the mod does not provide a universal replacement for continuous collision detection.

The screw target is refreshed on native physics steps. A finite mass-dependent force cap allows physical obstacles to stop the drive. A native motion window prevents free impact-driven displacement or cumulative back-driving while the screw is stopped. Reverse rotation reverses commanded travel. One revolution corresponds to one quarter of a block.

Free-carriage drag and screw stress are specified in [lubrication and stress](lubrication-and-stress.md). No second physics engine or direct integration of body position is introduced.

## Joining bodies

Two wrench selections merge touching, grid-aligned bodies with no overlapping blocks. The first body survives; native Sable transfer moves the second body's blocks, inventories, Create glue and tracking data. NeoForge snapshots verify transfer results and restore blocks if the transfer is incomplete.

Each operation moves at most 2,048 blocks, with a bounding-box volume at most 129 cubed. Relative quarter turns around local Y are accepted. A supporting body cannot merge with its own descendant. There is no fixed two-carriage limit, but all retained joints must permit compatible motion.

Shift-right-click clears the pending selection. Invalid selections are discarded when their target is no longer usable. Breaking a physical connection delegates body separation to Sable.

## Sensors

A Rail Sensor is an item-installed rail state, not a separate placed block. Rail loot includes an installed sensor when appropriate. Installation and mode changes respect modification events at world coordinates, including moving parents.

Approximate mode tests longitudinal carriage overlap. Precise mode tests the carriage center against a half-width of 1/32 block at the rail center. Both require a compatible guide frame. Native-step motion samples detect crossings between server ticks; a fresh activation lasts at least two game ticks. Persistent occupancy keeps it active.

Only the face behind the base provides weak redstone power at strength 15. Static opaque block textures represent the circuit. No sensor block-entity renderer or coplanar overlay is needed, and sensor state does not change collision geometry.

## Persistence, events and client presentation

Blockstates hold orientation, sensor mode/power and grease state. Body and joint references use Sable persistence. Installation, coupling and modifications use the existing server event and permission paths, with real world positions for sublevels.

The client uses Create's kinetic rendering for the rotating screw. The creative tab, item messages and eleven Ponder storyboards are supplied in English. Ponder scenes illustrate behavior through animation; they are not live physics test fixtures.

## Explicit limits

No curved guides, static disassembly tool or independent cross-dimension transport is implemented. Legacy static assembly uses a 2,048-block payload limit, a 64-block coordinate reach and at most 256 initial rail segments. Unloaded or immovable payloads are refused. Numerical tolerance and distant-coordinate precision remain properties of the native backend.
