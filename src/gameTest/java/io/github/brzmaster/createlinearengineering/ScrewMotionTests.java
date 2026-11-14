package io.github.brzmaster.createlinearengineering;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.slf4j.Logger;

/** Measure actual native velocity within each game tick, not just average travel. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class ScrewMotionTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    @GameTest(template = "empty", timeoutTicks = 250)
    public static void screw64RpmHasSteadyNativeSubstepVelocity(GameTestHelper h) {
        steadyVelocity(h, 190, 64);
    }

    @GameTest(template = "empty", timeoutTicks = 250)
    public static void screw256RpmHasSteadyNativeSubstepVelocity(GameTestHelper h) {
        steadyVelocity(h, 191, 256);
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void slowScrewKeepsPitchNearOrigin(GameTestHelper h) {
        slowTravel(h, 8, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void slowScrewKeepsPitchAtDistance(GameTestHelper h) {
        slowTravel(h, 8192, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void slowScrewKeepsPitchInReverseAtDistance(GameTestHelper h) {
        slowTravel(h, -8192, -4);
    }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void slowScrewKeepsPitchFurtherFromOrigin(GameTestHelper h) {
        slowTravel(h, 32768, 4);
    }

    private static void slowTravel(GameTestHelper h, int z, int rpm) {
        Fixture fixture = fixture(h, new BlockPos(-8, 100, z), rpm, 8);
        BlockPos rail = fixture.origin().south(3);
        awaitNetwork(h, java.util.List.of(rail), rpm, () -> {
            ServerSubLevel body = CarriagePlacement.place(h.getLevel(), rail);
            h.assertTrue(body != null, "Cannot install carriage on rotating screw");
            double start = anchor(body).z;
            h.runAfterDelay(200, () -> {
                assertRpm(h, rail, rpm);
                double actual = anchor(body).z - start;
                double expected = rpm * ScrewDrive.PITCH / 6d;
                LOGGER.info("Low-speed screw at Z={} RPM={} travel={} expected={}", z, rpm, actual, expected);
                h.assertTrue(entity(body).hasJoint() && Math.abs(actual - expected) < Math.abs(expected) * .15,
                        "Incorrect low-speed travel at Z=" + z + " RPM=" + rpm + ": " + actual + " vs " + expected);
                h.succeed();
            });
        });
    }

    private static void steadyVelocity(GameTestHelper h, int index, int rpm) {
        Fixture fixture = fixture(h, index, rpm, 40);
        awaitNetwork(h, java.util.stream.IntStream.range(-2, 40).mapToObj(fixture.origin()::south).toList(), rpm, () -> {
            ServerSubLevel body = CarriagePlacement.place(h.getLevel(), fixture.origin().south(2));
            h.assertTrue(body != null, "Cannot create smooth-drive carriage");
            double expected = rpm * ScrewDrive.PITCH / 60d;
            VelocitySamples samples = new VelocitySamples(expected);
            boolean[] measuring = {false}, active = {true};
            Vector3d[] start = {null};
            SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
                if (!active[0] || !measuring[0] || physics.getLevel() != h.getLevel() || body.isRemoved()) return;
                samples.add(physics.getPhysicsHandle(body).getLinearVelocity(new Vector3d()).z);
            });
            h.runAfterDelay(20, () -> assertRpm(h, fixture.origin().south(2), rpm));
            h.runAfterDelay(40, () -> {
                assertRpm(h, fixture.origin().south(2), rpm);
                start[0] = anchor(body);
                measuring[0] = true;
            });
            h.runAfterDelay(90, () -> {
                active[0] = false;
                measuring[0] = false;
                LOGGER.info("Screw motion {} RPM native samples: {}", rpm, samples);
                h.assertTrue(samples.count >= 30, "Too few actual Sable substeps: " + samples);
                h.assertTrue(!body.isRemoved() && entity(body).hasJoint(), "Smooth-drive fixture left its 40-segment track");
                h.assertTrue(Math.abs(samples.mean() - expected) <= expected * .12,
                        "Native mean speed disagrees with the actual Create RPM: " + samples);
                h.assertTrue(samples.max - samples.min <= Math.max(.10, expected * .25),
                        "Game-tick position steps create visible native velocity ripple: " + samples);
                h.assertTrue(samples.min > expected * .70, "Screw repeatedly stalled/reversed during steady drive: " + samples);
                double travelled = anchor(body).z - start[0].z;
                h.assertTrue(Math.abs(travelled - expected * 2.5) < Math.max(.08, expected * 2.5 * .12),
                        "Stable velocity fixture has wrong pitch/travel: " + travelled + "; " + samples);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 290)
    public static void followingScrewCarriageSettlesAgainstIndependentlyBlockedLeader(GameTestHelper h) {
        Fixture fixture = fixture(h, 192, 256, 12);
        BlockPos wall = fixture.origin().south(7);
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        awaitNetwork(h, java.util.stream.IntStream.range(-2, 7).mapToObj(fixture.origin()::south).toList(), 256, () -> {
            ServerSubLevel leader = CarriagePlacement.place(h.getLevel(), fixture.origin().south(6));
            h.assertTrue(leader != null, "Cannot create leading screw carriage");
            ServerSubLevel[] follower = {null};
            boolean[] active = {true}, measuring = {false};
            ContactSamples samples = new ContactSamples();
            SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
                if (!active[0] || physics.getLevel() != h.getLevel() || follower[0] == null
                        || follower[0].isRemoved() || leader.isRemoved()) return;
                Vector3d front = anchor(leader), rear = anchor(follower[0]);
                double gap = front.z - rear.z - 1;
                samples.minimumGap = Math.min(samples.minimumGap, gap);
                double speed = physics.getPhysicsHandle(follower[0]).getLinearVelocity(new Vector3d()).z;
                // The reported rebound happens at the first impact, before the
                // late stationary window. Keep measuring it after contact separates.
                if (gap < .03) samples.touched = true;
                if (samples.touched) samples.recordImpact(rear.z, speed);
                if (!measuring[0]) return;
                samples.add(rear.z, speed, gap);
            });
            h.runAfterDelay(30, () -> {
                assertRpm(h, fixture.origin().south(3), 256);
                double wallGap = wall.getZ() - (anchor(leader).z + .5);
                h.assertTrue(wallGap > -.035 && wallGap < .08, "Leader fixture did not settle at the real wall: " + wallGap);
                follower[0] = CarriagePlacement.place(h.getLevel(), fixture.origin().south(3));
                h.assertTrue(follower[0] != null, "Cannot create independent following carriage");
                h.assertTrue(!follower[0].getUniqueId().equals(leader.getUniqueId()), "Fixture unexpectedly joined both carriages");
            });
            h.runAfterDelay(100, () -> measuring[0] = true);
            h.runAfterDelay(135, () -> {
                active[0] = false;
                LOGGER.info("Independent screw carriage native contact samples: {}", samples);
                h.assertTrue(follower[0] != null && !follower[0].isRemoved() && !leader.isRemoved(), "Contact removed a carriage body");
                h.assertTrue(samples.count >= 30, "Too few native stalled-contact samples: " + samples);
                h.assertTrue(samples.minimumGap >= -.035, "Independent screw carriages penetrated each other: " + samples);
                h.assertTrue(samples.touched, "Follower never reached physical contact with its leader: " + samples);
                h.assertTrue(samples.maxBackwardSpeed <= .15 && samples.maxRetreat <= .04,
                        "Initial impact bounced the following screw carriage backwards: " + samples);
                h.assertTrue(samples.maxGap < .10, "Follower did not stay at the leading carriage: " + samples);
                h.assertTrue(samples.maxAbsSpeed <= .12,
                        "Powered follower kept bouncing after contact instead of stopping: " + samples);
                h.assertTrue(samples.maxPosition - samples.minPosition <= .06,
                        "Powered follower visibly oscillated against its leader: " + samples);
                h.assertTrue(entity(leader).hasJoint() && entity(follower[0]).hasJoint(), "Independent contact broke the guiding joints");
                h.succeed();
            });
        });
    }

    /** Start a measured scenario only after vanilla ticks have built its actual Create network. */
    static void awaitNetwork(GameTestHelper h, java.util.List<BlockPos> positions, int rpm, Runnable action) {
        long started = h.getLevel().getGameTime();
        LOGGER.info("Kinetic fixture before readiness: {}", networkDiagnostic(h, positions, rpm));
        awaitNetwork(h, positions, rpm, action, started, 120);
    }

    private static void awaitNetwork(GameTestHelper h, java.util.List<BlockPos> positions, int rpm,
                                     Runnable action, long started, int remaining) {
        if (positions.stream().allMatch(pos -> networkReady(h, pos, rpm))) {
            LOGGER.info("Kinetic fixture ready after {} game ticks; RPM={} blocks={}", h.getLevel().getGameTime() - started, rpm, positions.size());
            action.run();
        } else if (remaining > 0) {
            h.runAfterDelay(1, () -> awaitNetwork(h, positions, rpm, action, started, remaining - 1));
        } else {
            h.assertTrue(false, "Native Create network did not become ready within 120 game ticks: " + networkDiagnostic(h, positions, rpm));
        }
    }

    private static boolean networkReady(GameTestHelper h, BlockPos pos, int rpm) {
        return h.getLevel().isPositionEntityTicking(pos)
                && h.getLevel().getBlockEntity(pos) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic
                && Math.abs(kinetic.getSpeed() - rpm) < .01;
    }

    private static String networkDiagnostic(GameTestHelper h, java.util.List<BlockPos> positions, int rpm) {
        StringBuilder result = new StringBuilder("time=" + h.getLevel().getGameTime() + " expectedRPM=" + rpm);
        int pending = 0;
        for (BlockPos pos : positions) {
            if (networkReady(h, pos, rpm)) continue;
            if (++pending > 3) continue;
            var chunk = h.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var entity = h.getLevel().getBlockEntity(pos);
            result.append(" [").append(pos).append(" status=").append(chunk == null ? "missing" : chunk.getFullStatus())
                    .append(" entitiesLoaded=").append(h.getLevel().areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos)))
                    .append(" entityTicking=").append(h.getLevel().isPositionEntityTicking(pos))
                    .append(" speed=").append(entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic ? kinetic.getSpeed() : "missing")
                    .append("]");
        }
        return result.append(" pending=").append(pending).toString();
    }

    private record Fixture(BlockPos origin, CreativeMotorBlockEntity motor) {}

    private static Fixture fixture(GameTestHelper h, int index, int rpm, int length) {
        return fixture(h, new BlockPos(index * 48 + 8, 100, 8), rpm, length);
    }

    private static Fixture fixture(GameTestHelper h, BlockPos origin, int rpm, int length) {
        for (int i = 0; i < length; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
        }
        h.getLevel().setBlock(origin.north(2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
        CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(origin.north(2));
        motor.generatedSpeed.setValue(rpm);
        h.getLevel().setBlock(origin.north(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        h.assertTrue(motor.generatedSpeed.getValue() == rpm, "Create clamped the requested fixture RPM");
        return new Fixture(origin, motor);
    }

    private static void assertRpm(GameTestHelper h, BlockPos pos, int rpm) {
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof RailEntity rail && Math.abs(rail.getSpeed() - rpm) < .01,
                "Native Create network did not supply the requested " + rpm + " RPM at " + pos);
    }

    private static final class VelocitySamples {
        private final double expected;
        private int count;
        private double sum, squaredError;
        private double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        private VelocitySamples(double expected) { this.expected = expected; }
        private void add(double velocity) {
            count++; sum += velocity; squaredError += (velocity - expected) * (velocity - expected);
            min = Math.min(min, velocity); max = Math.max(max, velocity);
        }
        private double mean() { return sum / count; }
        @Override public String toString() {
            return "samples=" + count + ", expected=" + expected + ", mean=" + mean() + ", min=" + min + ", max=" + max
                    + ", ripple=" + (max - min) + ", rmsError=" + Math.sqrt(squaredError / count);
        }
    }

    private static final class ContactSamples {
        private int count;
        private double minimumGap = Double.POSITIVE_INFINITY, maxGap = Double.NEGATIVE_INFINITY, maxAbsSpeed;
        private double minPosition = Double.POSITIVE_INFINITY, maxPosition = Double.NEGATIVE_INFINITY;
        private boolean touched;
        private double furthestPosition = Double.NEGATIVE_INFINITY, maxRetreat, maxBackwardSpeed;
        private void recordImpact(double position, double speed) {
            furthestPosition = Math.max(furthestPosition, position);
            maxRetreat = Math.max(maxRetreat, furthestPosition - position);
            maxBackwardSpeed = Math.max(maxBackwardSpeed, -speed);
        }
        private void add(double position, double speed, double gap) {
            count++; maxGap = Math.max(maxGap, gap); maxAbsSpeed = Math.max(maxAbsSpeed, Math.abs(speed));
            minPosition = Math.min(minPosition, position); maxPosition = Math.max(maxPosition, position);
        }
        @Override public String toString() {
            return "samples=" + count + ", minGap=" + minimumGap + ", maxGap=" + maxGap + ", speedPeak=" + maxAbsSpeed
                    + ", positionRipple=" + (maxPosition - minPosition) + ", touched=" + touched
                    + ", initialReboundSpeed=" + maxBackwardSpeed + ", maxRetreat=" + maxRetreat;
        }
    }

    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static CarriageEntity entity(ServerSubLevel body) { return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock()); }
}
