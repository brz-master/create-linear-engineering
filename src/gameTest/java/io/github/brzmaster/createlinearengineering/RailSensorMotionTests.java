package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Real Sable integration must detect passages skipped by both endpoint queries. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class RailSensorMotionTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void preciseFastForwardCrossing(GameTestHelper h) {
        crossing(h, 160, RailSensor.Mode.PRECISE, 1, false);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void preciseFastReverseCrossing(GameTestHelper h) {
        crossing(h, 161, RailSensor.Mode.PRECISE, -1, false);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void approximateFastForwardCrossing(GameTestHelper h) {
        crossing(h, 162, RailSensor.Mode.APPROXIMATE, 1, false);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void approximateFastReverseCrossing(GameTestHelper h) {
        crossing(h, 163, RailSensor.Mode.APPROXIMATE, -1, false);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fastCarriageCannotSkipThreePreciseSensors(GameTestHelper h) {
        crossing(h, 164, RailSensor.Mode.PRECISE, 1, true);
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void gameTickTeleportDoesNotSimulateSensorCrossing(GameTestHelper h) {
        Fixture fixture = fixture(h, 165, RailSensor.Mode.APPROXIMATE, List.of(0, 1), -2, false);
        BlockPos precise = fixture.sensors().get(1);
        h.getLevel().setBlock(precise, h.getLevel().getBlockState(precise)
                .setValue(LinearBlock.SENSOR, RailSensor.Mode.PRECISE), 3);
        Probe probe = new Probe(h.getLevel(), fixture, 0);
        h.runAfterDelay(5, () -> whenReady(h, probe, () -> {
            stop(h.getLevel(), fixture.body());
            Vector3d target = RailContact.anchor(null, fixture.origin()).add(0, 0, 3);
            fixture.body().logicalPose().position().add(target.sub(anchor(fixture.body())));
            var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
            pipeline.teleport(fixture.body(), fixture.body().logicalPose().position(), fixture.body().logicalPose().orientation());
            fixture.body().updateLastPose();
            h.runAfterDelay(14, () -> {
                probe.active = false;
                h.assertTrue(!probe.sawPower, "A between-tick teleport created a false crossing pulse");
                h.assertTrue(!probe.crossedEverySensor, "Teleport fixture unexpectedly crossed during actual integration");
                h.assertTrue(anchor(fixture.body()).distance(RailContact.anchor(null, fixture.origin()).add(0, 0, 3)) < .08,
                        "Teleport fixture did not remain beyond both sensors");
                h.succeed();
            });
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fastParallelCarriageDoesNotPulseEitherMode(GameTestHelper h) {
        Fixture fixture = fixture(h, 166, RailSensor.Mode.APPROXIMATE, List.of(0, 1), -2, true);
        BlockPos precise = fixture.sensors().get(1);
        h.getLevel().setBlock(precise, h.getLevel().getBlockState(precise)
                .setValue(LinearBlock.SENSOR, RailSensor.Mode.PRECISE), 3);
        Probe probe = new Probe(h.getLevel(), fixture, 5);
        h.runAfterDelay(5, () -> whenReady(h, probe, () -> {
            probe.armed = true;
            h.runAfterDelay(14, () -> {
                probe.active = false;
                h.assertTrue(probe.crossedEverySensor, "Parallel fixture never passed both sensor Z coordinates in one substep");
                h.assertTrue(!probe.sawPower, "Swept detection leaked into an adjacent parallel lane");
                h.succeed();
            });
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void preciseFastCrossingOnRotatedMovingParent(GameTestHelper h) {
        BlockPos source = origin(h, 167);
        List<BlockPos> rails = new ArrayList<>();
        for (int i = -7; i <= 7; i++) {
            BlockPos pos = source.south(i);
            h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState()
                    .setValue(LinearBlock.SENSOR, i == 0 ? RailSensor.Mode.PRECISE : RailSensor.Mode.NONE), 3);
            rails.add(pos);
        }
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), source, rails,
                new BoundingBox3i(source.getX(), source.getY(), source.getZ() - 7, source.getX(), source.getY(), source.getZ() + 7));
        BlockPos sensor = parent.getPlot().getCenterBlock();
        Vector3d anchor = RailContact.anchor(parent, sensor);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(anchor.sub(RailContact.anchor(parent, sensor)));
        var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
        physics.getPipeline().teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), sensor.north(2));
        h.assertTrue(body != null, "Cannot place carriage on rotated rail");
        Fixture fixture = new Fixture(sensor, List.of(sensor), body, parent);
        Probe probe = new Probe(h.getLevel(), fixture, 4);
        Vector3d initial = new Vector3d(parent.logicalPose().position());
        h.runAfterDelay(5, () -> {
            physics.getPipeline().addLinearAndAngularVelocity(parent, new Vector3d(1, 0, 0), new Vector3d());
            probe.armed = true;
        });
        pollGameUpdates(h, probe, 16);
        h.runAfterDelay(18, () -> {
            probe.active = false;
            h.assertTrue(probe.crossedEverySensor && probe.allPoweredAtCrossing,
                    "Relative sweep on a 45-degree moving parent missed its precise sensor");
            h.assertTrue(Math.abs(parent.logicalPose().position().x - initial.x) > .15,
                    "Rotated parent fixture never translated");
            h.assertTrue(probe.firstGameUpdate && probe.secondGameUpdate && !probe.shortPulse,
                    "Moving parent's pulse did not last two game updates");
            h.assertTrue(!powered(h.getLevel(), sensor), "Moving-parent crossing pulse stayed on");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void pulseSurvivesBlockEntityNbtRecreation(GameTestHelper h) {
        BlockPos pos = origin(h, 169);
        h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState()
                .setValue(LinearBlock.SENSOR, RailSensor.Mode.PRECISE), 3);
        long requested = h.getLevel().getGameTime();
        LOGGER.info("Pulse NBT fixture before readiness: time={} {}", requested, sensorStates(h.getLevel(), List.of(pos)));
        awaitTicking(h, List.of(pos), () -> {
            LOGGER.info("Pulse NBT fixture ready after {} game ticks: {}", h.getLevel().getGameTime() - requested,
                    sensorStates(h.getLevel(), List.of(pos)));
            recreatePulse(h, pos);
        }, 120, () -> {});
    }

    private static void recreatePulse(GameTestHelper h, BlockPos pos) {
        RailSensor.crossed(h.getLevel(), pos);
        RailEntity original = (RailEntity) h.getLevel().getBlockEntity(pos);
        long deadline = original.sensorPulseUntil;
        CompoundTag saved = original.saveWithFullMetadata(h.getLevel().registryAccess());
        BlockState poweredState = h.getLevel().getBlockState(pos);
        h.getLevel().removeBlockEntity(pos);
        RailEntity restored = new RailEntity(pos, poweredState);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        h.assertTrue(restored.sensorPulseUntil == deadline, "NBT recreation lost the absolute pulse deadline");
        h.assertTrue(powered(h.getLevel(), pos), "NBT recreation immediately discarded a live crossing pulse");
        h.runAfterDelay(1, () -> h.assertTrue(powered(h.getLevel(), pos), "Restored pulse lost its first game update"));
        h.runAfterDelay(2, () -> h.assertTrue(powered(h.getLevel(), pos), "Restored pulse lost its second game update"));
        h.runAfterDelay(5, () -> {
            h.assertTrue(!powered(h.getLevel(), pos), "Restored pulse deadline never expired; time="
                    + h.getLevel().getGameTime() + " savedDeadline=" + deadline + " " + sensorStates(h.getLevel(), List.of(pos)));
            h.succeed();
        });
    }

    private static void crossing(GameTestHelper h, int index, RailSensor.Mode mode, int sign, boolean chain) {
        List<Integer> offsets = chain ? List.of(-1, 0, 1) : List.of(0);
        int start = -sign * (chain ? 3 : 2);
        Fixture fixture = fixture(h, index, mode, offsets, start, false);
        Probe probe = new Probe(h.getLevel(), fixture, sign * (chain ? 6 : 4));
        h.runAfterDelay(5, () -> whenReady(h, probe, () -> {
            probe.armed = true;
            pollGameUpdates(h, probe, 16);
            h.runAfterDelay(18, () -> {
                probe.active = false;
                h.assertTrue(probe.crossedEverySensor,
                        "Fixture did not skip every complete sensor window in one actual Sable substep: " + probe.distance);
                h.assertTrue(probe.allPoweredAtCrossing, "High-speed crossing skipped an installed " + mode + " sensor");
                h.assertTrue(probe.firstGameUpdate && probe.secondGameUpdate,
                        "Pulse was not observed for both game updates after crossing");
                h.assertTrue(!probe.shortPulse, "Physics crossing pulse lasted less than two game updates");
                for (BlockPos sensor : fixture.sensors()) h.assertTrue(!powered(h.getLevel(), sensor),
                        "Transient crossing pulse did not clear; " + diagnostic(h.getLevel(), probe));
                h.succeed();
            });
        }));
    }

    private static void whenReady(GameTestHelper h, Probe probe, Runnable action) {
        awaitTicking(h, probe, action, 120);
    }

    /** A forced FULL chunk may still await entity loading, which gates vanilla BE ticking. */
    private static void awaitTicking(GameTestHelper h, Probe probe, Runnable action, int remaining) {
        awaitTicking(h, probe.fixture.sensors(), action, remaining, () -> probe.active = false);
    }

    private static void awaitTicking(GameTestHelper h, List<BlockPos> sensors, Runnable action, int remaining, Runnable failed) {
        if (sensors.stream().allMatch(h.getLevel()::isPositionEntityTicking)) {
            action.run();
        } else if (remaining > 0) {
            h.runAfterDelay(1, () -> awaitTicking(h, sensors, action, remaining - 1, failed));
        } else {
            failed.run();
            h.assertTrue(false, "Sensor fixture did not become entity-ticking within 120 game updates; "
                    + sensorStates(h.getLevel(), sensors));
        }
    }

    /** GameTest callbacks run after world updates; sampling physics alone misses tickTime's increment. */
    private static void pollGameUpdates(GameTestHelper h, Probe probe, int remaining) {
        if (remaining == 0) return;
        h.runAfterDelay(1, () -> {
            if (!probe.active) return;
            if (probe.crossingTime != Long.MIN_VALUE) {
                long age = h.getLevel().getGameTime() - probe.crossingTime;
                boolean allPowered = probe.fixture.sensors().stream().allMatch(pos -> powered(h.getLevel(), pos));
                if (age == 1) probe.firstGameUpdate = true;
                if (age == 2) probe.secondGameUpdate = true;
                if (age >= 1 && age <= 2 && !allPowered) probe.shortPulse = true;
            }
            pollGameUpdates(h, probe, remaining - 1);
        });
    }

    private record Fixture(BlockPos origin, List<BlockPos> sensors, ServerSubLevel body, ServerSubLevel parent) {}

    private static Fixture fixture(GameTestHelper h, int index, RailSensor.Mode mode, List<Integer> sensors,
                                   int start, boolean parallel) {
        BlockPos origin = origin(h, index);
        BlockState rail = CreateLinearEngineering.RAIL.get().defaultBlockState();
        for (int i = -7; i <= 7; i++) {
            h.getLevel().setBlock(origin.south(i), rail, 3);
            if (parallel) h.getLevel().setBlock(origin.south(i).east(), rail, 3);
        }
        List<BlockPos> positions = new ArrayList<>();
        for (int i : sensors) {
            BlockPos pos = origin.south(i);
            h.getLevel().setBlock(pos, rail.setValue(LinearBlock.SENSOR, mode), 3);
            positions.add(pos);
        }
        BlockPos mount = origin.south(start);
        if (parallel) mount = mount.east();
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), mount);
        h.assertTrue(body != null, "Cannot create high-speed sensor carriage");
        BlockPos carriage = body.getPlot().getCenterBlock();
        h.getLevel().setBlock(carriage, h.getLevel().getBlockState(carriage).setValue(LinearBlock.GREASE, Grease.LOW), 3);
        return new Fixture(origin, List.copyOf(positions), body, null);
    }

    /** Event callbacks only record evidence; assertions stay in ordinary GameTest callbacks. */
    private static final class Probe {
        private final Fixture fixture;
        private boolean active = true, armed;
        private int steps;
        private Vector3d before;
        private boolean crossedEverySensor, allPoweredAtCrossing, sawPower, shortPulse;
        private boolean firstGameUpdate, secondGameUpdate;
        private long crossingTime = Long.MIN_VALUE;
        private long lastTraceTime = Long.MIN_VALUE;
        private double distance;
        private final StringBuilder trace = new StringBuilder();

        private Probe(ServerLevel level, Fixture fixture, double displacement) {
            this.fixture = fixture;
            SableEventPlatform.INSTANCE.onPhysicsTick((physics, timeStep) -> {
                if (!active || physics.getLevel() != level || fixture.body().isRemoved()) return;
                before = samplePoint(fixture);
                if (!armed) return;
                // One genuine integration step moves across the whole window. The
                // next substep stops the body beyond the sensor, isolating its latch.
                if (steps < 2) {
                    stop(level, fixture.body());
                    Vector3d velocity = new Vector3d(0, 0, steps == 0 ? displacement / timeStep : 0);
                    if (fixture.parent() != null) {
                        fixture.parent().logicalPose().orientation().transform(velocity);
                        velocity.add(physics.getPhysicsHandle(fixture.parent()).getLinearVelocity(new Vector3d()));
                    }
                    physics.getPipeline().addLinearAndAngularVelocity(fixture.body(), velocity, new Vector3d());
                    steps++;
                }
            });
            SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, timeStep) -> {
                if (!active || physics.getLevel() != level || before == null || fixture.body().isRemoved()) return;
                boolean allPowered = true;
                for (BlockPos sensor : fixture.sensors()) {
                    boolean output = powered(level, sensor);
                    sawPower |= output;
                    allPowered &= output;
                }
                Vector3d after = samplePoint(fixture);
                if (armed && level.getGameTime() != lastTraceTime) {
                    lastTraceTime = level.getGameTime();
                    trace.append("[").append(lastTraceTime).append(" z=")
                            .append(after.z - fixture.origin().getZ() - .5).append(" ")
                            .append(sensorStates(level, fixture)).append("]");
                }
                boolean skipped = true;
                for (BlockPos sensor : fixture.sensors()) {
                    // 1 block is the wider approximate window, so this also proves a
                    // precise test was not rescued by an endpoint inside tolerance.
                    double a = before.z - (sensor.getZ() + .5), b = after.z - (sensor.getZ() + .5);
                    skipped &= Math.min(a, b) < -1 && Math.max(a, b) > 1;
                }
                if (skipped && !crossedEverySensor) {
                    crossedEverySensor = true;
                    allPoweredAtCrossing = allPowered;
                    crossingTime = level.getGameTime();
                    distance = after.z - before.z;
                }
            });
        }
    }

    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }

    private static Vector3d samplePoint(Fixture fixture) {
        Vector3d point = anchor(fixture.body());
        if (fixture.parent() != null) fixture.parent().logicalPose().transformPositionInverse(point);
        return point;
    }

    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }

    private static boolean powered(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(LinearBlock.POWERED);
    }

    private static String sensorStates(ServerLevel level, Fixture fixture) {
        return sensorStates(level, fixture.sensors());
    }

    private static String sensorStates(ServerLevel level, List<BlockPos> sensors) {
        StringBuilder result = new StringBuilder();
        for (BlockPos pos : sensors) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var entity = level.getBlockEntity(pos);
            result.append("power=").append(powered(level, pos)).append(" until=")
                    .append(entity instanceof RailEntity rail ? rail.sensorPulseUntil : "missingBE")
                    .append(" chunkStatus=").append(chunk == null ? "missing" : chunk.getFullStatus())
                    .append(" entitiesLoaded=").append(level.areEntitiesLoaded(ChunkPos.asLong(pos)))
                    .append(" entityTicking=").append(level.isPositionEntityTicking(pos))
                    .append(" blockTicking=").append(level.shouldTickBlocksAt(ChunkPos.asLong(pos)))
                    .append(" beRemoved=").append(entity == null ? "missing" : entity.isRemoved()).append(";");
        }
        return result.toString();
    }

    private static String diagnostic(ServerLevel level, Probe probe) {
        String message = "time=" + level.getGameTime() + " crossing=" + probe.crossingTime + " anchor=" + samplePoint(probe.fixture)
                + " occupancy=" + probe.fixture.sensors().stream().map(pos -> RailSensor.detects(level, pos, level.getBlockState(pos))).toList()
                + " " + sensorStates(level, probe.fixture) + " trace=" + probe.trace;
        // GameTest also serializes failures into written books, whose page limit is 1024.
        return message.length() <= 900 ? message : message.substring(0, 900);
    }

    private static void stop(ServerLevel level, ServerSubLevel body) {
        var physics = SubLevelContainer.getContainer(level).physicsSystem();
        var handle = physics.getPhysicsHandle(body);
        physics.getPipeline().addLinearAndAngularVelocity(body,
                handle.getLinearVelocity(new Vector3d()).negate(), handle.getAngularVelocity(new Vector3d()).negate());
    }
}
