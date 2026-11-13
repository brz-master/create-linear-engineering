# Lubrication, mass and stress

## Three treatments

| Treatment | Added brake on free rails | Screw stress factor | Sliding surface |
|---|---:|---:|---|
| Dry | 100% | 1 | Metal |
| High Viscosity | 50% | 1/4 | Amber |
| Low Viscosity | 0% | 1/16 | Pale |

A dry carriage slows rapidly after a push. High Viscosity retains half of that added brake; Low Viscosity removes it. Zero refers only to the brake added by this mod: Sable's dimension drag and physical contacts remain active. Gravity still acts, so a free vertical guide is not a parking brake.

Apply grease by right-clicking a Linear Carriage or Toothed Carriage. It cannot be applied to a rail. One item is consumed in survival only when the treatment changes; creative application is free. Treatment persists indefinitely through saves, transfers and merges. Reapplying the same type consumes nothing. Breaking the carriage removes the treatment without returning grease. Modification permissions are checked at the body's actual world position.

Both carriages are exactly one block long along the guide. A faded trace at the exposed contact edge identifies grease on a mounted carriage. The underside of both feet uses the same smooth finish as the inner channel. Treatment changes the surface texture, not the carriage geometry.

## Screw stress calculation

Empty screw rails transmit rotation without a fixed stress cost. The segment currently driving a carriage accounts for its load, including when it crosses between separate kinetic networks.

For each engaged Toothed Carriage:

```text
shared_mass = body_mass / engaged_screw_carriages_on_that_body
impact = (0.25 + shared_mass / 128) * grease_factor
stress_SU = impact * abs(RPM)
```

The base impact represents each mechanism. Body mass is counted once: two carriages supporting the same body divide its mass, while separate bodies each contribute their own mass. Different treatments apply their own factors to their respective carriage shares.

Mass comes from Sable's actual mass tracker, rather than an estimated block count. Material configuration and Create contraptions included in that tracker are respected. Under standard settings an ordinary full block has one mass unit; heavier materials may contribute more. Separate bodies connected through other joints are not recursively added to this formula.

| Body mass, one carriage | Dry SU at 32 RPM | High SU at 32 RPM | Low SU at 32 RPM |
|---:|---:|---:|---:|
| 1 | 8.25 | 2.0625 | 0.515625 |
| 128 | 40 | 10 | 2.5 |
| 1,024 | 264 | 66 | 16.5 |
| 4,096 | 1,032 | 258 | 64.5 |

With standard Create settings, a Hand Crank supplies 256 SU at 32 RPM, or 8 SU per RPM of capacity. It comfortably drives a light dry carriage. With one carriage and no other consumers, the theoretical mass limits are 992 units dry, 4,064 with High Viscosity and 16,352 with Low Viscosity. Other machines, gearing and configuration changes affect available capacity. These are kinetic-network limits, not a guarantee that a load can pass through a physical obstacle.

The grease factors follow the geometric progression 1, 1/4, 1/16. High reduces stress fourfold and Low reduces it sixteenfold. Cost remains proportional to carried mass, and repeated application cannot stack discounts.

Load is refreshed even when the network is overstressed. Removing payload or applying grease can allow it to recover. Create goggles report stress on the currently loaded segment. At zero RPM or during overstress, the joint holds the carriage with every grease treatment. Low Viscosity does not allow a stationary screw to be back-driven by a push.

## Native physics implementation

Treatment is a vanilla blockstate property shared by appearance, persistence and behavior. Model variants inherit oriented geometry and replace texture references. Cached collision shapes normalize treatment because geometry does not change.

The free-carriage brake uses the Sable joint motor with zero stiffness and damping 64 for dry or 32 for High Viscosity. The force cap is proportional to body mass and halved for High; Low sets that added force to zero. Braking acts on velocity relative to the joint parent, so guides on moving vehicles work without a separate physics integrator or direct velocity corrections.

Only the inner collision surfaces have a mechanical clearance of 1/1024 block to avoid snagging at voxel seams. Visible geometry and axial length remain unchanged. Placement and Approximate sensing use the same tolerance for solver settling.

Screw drives retain a native motion window that prevents impact-driven travel beyond the commanded step. Stress uses Create's network-update API. Transient load references follow the supporting segment, separately from the persistent references used by Sable's body topology. Mass, grease and support changes are picked up during normal server ticks, including while stationary.

Wall-flush placement uses Minecraft's actual collision contact plane without an artificial setback. The joint and native contacts retain their numerical tolerances.

## Verification

The native GameTest suite covers item use, permissions, persistence, braking on moving and rotated parents, rail exit and re-entry, fast contacts, sensors, lubricated screw impacts, shared loads and separate kinetic networks. A dedicated regression pushes ten independent carriages twice across alternating rail-segment seams on two axes.

In a controlled horizontal trial starting at four blocks per second, travel after 45 ticks was approximately 0.13 blocks dry, 0.27 with High Viscosity and 8.16 with Low Viscosity. These measurements compare treatments under one setup; they are not universal stopping distances.
