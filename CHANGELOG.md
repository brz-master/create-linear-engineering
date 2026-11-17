# Changelog

## 0.8.7

- Linear Rail and Screw Rail placement arrows now check for blocks in the world and on nearby Sable structures. When the preferred end is blocked, the helper checks the opposite end.

## 0.8.6

- Added support for Sable 2.0.6 on Minecraft 1.21.1 with NeoForge. The same file also supports Sable 2.0.0 through 2.0.5.
- Existing rails, carriages, sensors and recipes use the same registry IDs and save format.

## 0.8.5

- Restored carriages reacquire a nearby aligned rail when their original static rail chunk is unloaded.
- Coupled screw carriages share one mass and support calculation per rigid body per game tick.
- Screw shafts use Create's Flywheel rotating visual, with extended visibility in the fallback renderer.
- The Rail Sensor Ponder scene lights the next approximate sensor on the first tick of overlap.

## 0.8.4

- Fixed toothed carriages stalling at low RPM on rotating screw rails away from the world origin. The drive retains fractional travel between physics steps, with bounded correction during contact and immediate reset on stop or reversal.

## 0.8.3

- Introduced the Create: Linear Engineering project identity, artifact name and `create_linear_engineering` namespace.
- Supplied English documentation, specifications, messages, tutorials and render captions.
- Included free and screw-driven guides, physical carriages, multi-carriage joining, automatic re-entry, rail sensors and two grease treatments.
- Included full-block-length carriages, strong dry braking, smooth inner and underside surfaces, and visible grease at exposed contact edges.
- Preserved native wall-flush placement, payload-aware physical stops, screw anti-backdrive behavior and mass-dependent Create stress.
- Included 107 native physics and integration tests.

This version uses a distinct registry namespace. It does not automatically convert blocks or items stored under another namespace. No gameplay or collision changes are introduced by the project-identity update.
