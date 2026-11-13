# Development

## Build

Use JDK 21. The Gradle wrapper downloads the declared build tooling and dependencies.

```powershell
./gradlew.bat build
./gradlew.bat runGameTestServer
./gradlew.bat runClient
```

Use `./gradlew` on Linux and macOS. `build/libs/` contains the mod and sources JARs. GameTest code is isolated in `src/gameTest/` and is excluded from the distribution JAR. The GameTest task recreates its generated world before each run.

## Source layout

| Path | Contents |
|---|---|
| `src/main/java/` | Registrations, blocks, items, body lifecycle, native physics integration and client tutorials. |
| `src/main/resources/` | Metadata, English text, recipes, loot, tags, models, textures and Ponder structures. |
| `src/gameTest/` | 107 integration tests with the real Sable/Rapier backend. |
| `tools/art/` | Reproducible pixel textures, model generators and documentation renderers. |
| `tools/generate_ponder.py` | Ponder baseplate and English storyboard entries extracted from Java scenes. |
| `docs/` | Product specifications, stress formulas and generated renders. |

## Asset tools

Install Pillow and nbtlib into your Python environment to use the asset tooling. Assets are checked in, so Python is not required for a normal Gradle build.

```text
python -m pip install Pillow nbtlib
python tools/art/generate_textures.py
python tools/art/generate_models.py
python tools/art/generate_screw_texture.py
python tools/art/generate_screw_models.py --render
python tools/art/generate_sensor_textures.py
python tools/art/generate_sensor_models.py --render
python tools/art/generate_grease_assets.py
python tools/art/render_blocks.py
python tools/generate_ponder.py
```

Run generators in the order shown: derived screw, sensor and grease models depend on earlier base assets. Review generated changes before committing. English Ponder entries are extracted from scene titles and text calls; item names and other messages remain in `en_us.json`.

Documentation images are renders of the actual distributed models and textures, not screenshots. Preview geometry is for documentation only and does not replace runtime collision shapes.

## Validation

The CI workflow builds and runs the native suite on Java 21, with bounded runtime and thread diagnostics for stalled shutdowns. Uploaded artifacts include JARs and GameTest logs.

Physics fixtures wait for normal chunk ticking and kinetic-network readiness. They exercise the real event dispatch, body assembly and solver rather than manually calling production ticks or replacing physics with mocks. Fixtures run near the origin to avoid distant-coordinate precision effects.

When changing registry names, inspect JSON references, tags, recipes, language keys, Ponder structures, test templates and the namespace configured for GameTest discovery. A metadata-only build does not establish world migration compatibility.
