# Changelog

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
