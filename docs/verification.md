# Verification

## Version 0.8.7

- All 113 native GameTests passed on both Sable 2.0.0 and 2.0.6. The new regression builds a screw rail on a rotated Sable structure, obstructs one end with terrain and the other with a separate body, and checks the placement offset, inventory count and final block position after the wall is removed.

## Version 0.8.6

- Each NeoForge release of Sable from 2.0.0 through 2.0.6 passed all 112 native GameTests on Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10 and Java 21 (784 passes). The tests exercised the same compiled mod classes on every release.
- On each of the seven versions, a NeoForge client opened a saved world with the mod JAR installed, received the placed Screw Rail, and resolved its block entity renderer and Flywheel visual.
- The seven Sable 1.x builds listed on Modrinth for this loader and game version lack the generic constraint configuration and handle used by the carriage joints. Supporting those builds would require a separate physics implementation.

## Version 0.8.5

- The dedicated GameTest server runs 112 required tests, including recovery with a distant unloaded origin, late loading of an obsolete reverse link, and rollback of a rail carrying a separate physical body.
- A separate server process reopened a saved 0.8.4 world with the carriage 512 blocks from its original rail. The carriage recreated its joint on the loaded local rail while the original chunk stayed unloaded.
- Isolated native tests also exercised rollback of an external descendant and transfer of a stale origin before its first tick.
- Two isolated client runs placed a screw rail in a world. With Flywheel enabled the shaft visual was created and the fallback emitted no draws. With the backend disabled the fallback drew the shaft and accepted a distant diagonal camera position.
- Client integration was reviewed against Create, Flywheel, Sable and Ponder. The fallback renderer follows the visible rail section and the Ponder animation activates the incoming approximate sensor on its first overlap tick.

## Version 0.8.4

All 111 required native GameTests passed with the dependency versions listed below. The suite measures toothed carriage travel over 200 game ticks on rotating screw rails at 1 RPM near the coordinate origin, at 1 and -4 RPM around Z = ±8192, and at 4 RPM around Z = 32768. Existing tests cover wall contact, obstruction by a second carriage, reversal, unpowered holding and steady travel at 64 and 256 RPM.

## Version 0.8.3

- Environment: Windows, Java 21, Minecraft 1.21.1, NeoForge 21.1.259, Create 6.0.10 and Sable 2.0.5.
- Command: `./gradlew.bat build runGameTestServer --console plain`.
- Result: **107 of 107 required native GameTests passed**; build completed in 1 minute 30 seconds and the server shut down normally.
- Package inspection: correct mod identity, English language resources, eleven Ponder storyboards, and no GameTest classes in the distribution JAR.
- Documentation checks: local links resolve and model renders have English captions.

These checks validate the declared dependency baseline. They are not a certification of every modpack, machine or supported version range. In-game behavior remains subject to the native physics solver's numerical tolerances.
