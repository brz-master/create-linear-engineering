# Verification

## Version 0.8.4

All 111 required native GameTests passed with the dependency versions listed below. The suite measures toothed carriage travel over 200 game ticks on rotating screw rails at 1 RPM near the coordinate origin, at 1 and -4 RPM around Z = ±8192, and at 4 RPM around Z = 32768. Existing tests cover wall contact, obstruction by a second carriage, reversal, unpowered holding and steady travel at 64 and 256 RPM.

## Version 0.8.3

- Environment: Windows, Java 21, Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10 and Sable 2.0.5.
- Command: `./gradlew.bat build runGameTestServer --console plain`.
- Result: **107 of 107 required native GameTests passed**; build completed in 1 minute 30 seconds and the server shut down normally.
- Package inspection: correct mod identity, English language resources, eleven Ponder storyboards, and no GameTest classes in the distribution JAR.
- Documentation checks: local links resolve and model renders have English captions.

These checks validate the declared dependency baseline. They are not a certification of every modpack, machine or supported version range. In-game behavior remains subject to the native physics solver's numerical tolerances.
