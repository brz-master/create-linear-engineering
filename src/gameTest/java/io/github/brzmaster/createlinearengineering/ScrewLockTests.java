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

/** External impulses must not back-drive an unpowered lead screw. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class ScrewLockTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void unpoweredScrewRejectsForwardAxialImpulse(GameTestHelper h) {
        axialImpulse(h, 193, true, 8);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void unpoweredScrewRejectsBackwardAxialImpulse(GameTestHelper h) {
        axialImpulse(h, 194, true, -8);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void passiveRailRemainsFreeUnderTheSameAxialImpulse(GameTestHelper h) {
        axialImpulse(h, 195, false, 8);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void poweredScrewRejectsForwardSpeedBeyondItsRotation(GameTestHelper h) {
        poweredImpulse(h, 196, 8);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void poweredScrewRejectsBackwardMotionAgainstItsRotation(GameTestHelper h) {
        poweredImpulse(h, 197, -8);
    }

    private static void poweredImpulse(GameTestHelper h, int index, double impulse) {
        BlockPos origin = poweredTrack(h, index, 33);
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), origin.south(2));
        h.assertTrue(body != null, "Cannot create powered impulse carriage");
        PoweredSamples samples = new PoweredSamples();
        double commanded = 256 * ScrewDrive.PITCH / 60d;
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!samples.active || !samples.launched || physics.getLevel() != h.getLevel() || body.isRemoved()) return;
            double position = anchor(body).z;
            double delta = position - samples.previous;
            samples.count++;
            samples.elapsed += dt;
            samples.peakReverse = Math.max(samples.peakReverse, -delta);
            samples.peakExcessAdvance = Math.max(samples.peakExcessAdvance, delta - commanded * dt);
            samples.cumulativeOverrun = Math.max(samples.cumulativeOverrun, position - samples.start - commanded * samples.elapsed);
            samples.previous = position;
        });
        h.runAfterDelay(40, () -> {
            assertPowered(h, origin.south(2));
            h.assertTrue(entity(body).hasJoint(), "Powered impulse fixture has no native joint");
            samples.previous = samples.start = anchor(body).z;
            samples.launched = true;
            SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                    .addLinearAndAngularVelocity(body, new Vector3d(0, 0, impulse), new Vector3d());
        });
        h.runAfterDelay(70, () -> {
            samples.active = false;
            LOGGER.info("Powered screw external deltaV={} native samples: {}", impulse, samples);
            h.assertTrue(samples.count >= 30, "Too few powered native impulse samples: " + samples);
            h.assertTrue(!body.isRemoved() && entity(body).hasJoint(), "Powered impulse released its screw joint: " + samples);
            h.assertTrue(samples.peakReverse <= .035, "External impulse moved against screw rotation: " + samples);
            h.assertTrue(samples.peakExcessAdvance <= .035, "External impulse outran screw rotation in a native substep: " + samples);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 700)
    public static void heavyFollowingScrewCannotRatchetThroughBlockedLeaderDuringLongStall(GameTestHelper h) {
        BlockPos origin = poweredTrack(h, 198, 12);
        BlockPos wall = origin.south(7);
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        ServerSubLevel leader = CarriagePlacement.place(h.getLevel(), origin.south(6));
        h.assertTrue(leader != null, "Cannot create blocked leading screw carriage");
        ServerSubLevel[] follower = {null};
        StallSamples samples = new StallSamples();
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!samples.active || physics.getLevel() != h.getLevel() || follower[0] == null
                    || leader.isRemoved() || follower[0].isRemoved()) return;
            double front = anchor(leader).z, rear = anchor(follower[0]).z;
            double gap = front - rear - 1;
            samples.count++;
            samples.minimumGap = Math.min(samples.minimumGap, gap);
            samples.wallPenetration = Math.max(samples.wallPenetration, front + .5 - wall.getZ());
            if (gap < .03 && samples.contactTime < 0) samples.contactTime = h.getLevel().getGameTime();
            if (samples.stalled) {
                samples.leaderDrift = Math.max(samples.leaderDrift, Math.abs(front - samples.leaderStart));
                samples.followerDrift = Math.max(samples.followerDrift, Math.abs(rear - samples.followerStart));
            }
        });
        h.runAfterDelay(30, () -> {
            assertPowered(h, origin.south(3));
            double wallGap = wall.getZ() - anchor(leader).z - .5;
            h.assertTrue(wallGap > -.035 && wallGap < .08, "Leading fixture did not reach its wall: " + wallGap);
            follower[0] = CarriagePlacement.place(h.getLevel(), origin.south(3));
            h.assertTrue(follower[0] != null && !follower[0].getUniqueId().equals(leader.getUniqueId()),
                    "Cannot create independent heavy follower");
            BlockPos carriage = follower[0].getPlot().getCenterBlock();
            // Nine blocks increase only the follower's mass. No forward overhang
            // can touch the end wall before the two carriage sleeves touch.
            for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++)
                h.getLevel().setBlock(carriage.offset(x, y, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        });
        h.runAfterDelay(140, () -> {
            samples.leaderStart = anchor(leader).z;
            samples.followerStart = anchor(follower[0]).z;
            samples.stalled = true;
        });
        h.runAfterDelay(650, () -> {
            samples.active = false;
            LOGGER.info("Long powered screw heavy-follower native stall samples: {}", samples);
            h.assertTrue(!leader.isRemoved() && follower[0] != null && !follower[0].isRemoved()
                    && entity(leader).hasJoint() && entity(follower[0]).hasJoint(), "Long stall lost a screw body/joint: " + samples);
            h.assertTrue(samples.contactTime >= 0 && h.getLevel().getGameTime() - samples.contactTime >= 500,
                    "Fixture did not sustain at least 500 game ticks after first contact: " + samples);
            h.assertTrue(samples.minimumGap >= -.035 && samples.wallPenetration <= .035,
                    "Heavy screw follower cumulatively ratcheted through its leader/end wall: " + samples);
            h.assertTrue(samples.leaderDrift <= .035 && samples.followerDrift <= .035,
                    "Screw pair kept creeping during sustained physical obstruction: " + samples);
            h.succeed();
        });
    }

    private static BlockPos poweredTrack(GameTestHelper h, int index, int length) {
        BlockPos origin = new BlockPos(index * 48 + 8, 100, 8);
        for (int i = 0; i < length; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
        }
        h.getLevel().setBlock(origin.north(2), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
        CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(origin.north(2));
        motor.generatedSpeed.setValue(256);
        h.getLevel().setBlock(origin.north(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        return origin;
    }

    private static void assertPowered(GameTestHelper h, BlockPos pos) {
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof RailEntity rail && Math.abs(rail.getSpeed() - 256) < .01,
                "Create fixture did not supply the required 256 RPM");
    }

    private static void axialImpulse(GameTestHelper h, int index, boolean screw, double speed) {
        BlockPos origin = new BlockPos(index * 48 + 8, 100, 8);
        var rail = (screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().defaultBlockState();
        for (int i = -16; i <= 16; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, rail, 3);
        }
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), origin);
        h.assertTrue(body != null, "Cannot create axial-impulse carriage");
        if (!screw) h.getLevel().setBlock(entity(body).getBlockPos(), entity(body).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Samples samples = new Samples();
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!samples.active || !samples.launched || physics.getLevel() != h.getLevel() || body.isRemoved()) return;
            double travel = anchor(body).z - samples.start;
            samples.count++;
            samples.peakExcursion = Math.max(samples.peakExcursion, Math.abs(travel));
            samples.forwardTravel = Math.max(samples.forwardTravel, travel);
        });
        h.runAfterDelay(10, () -> {
            h.assertTrue(entity(body).hasJoint(), "Impulse fixture has no native guiding joint");
            if (screw) h.assertTrue(h.getLevel().getBlockEntity(origin) instanceof RailEntity railEntity
                    && railEntity.getSpeed() == 0, "Self-lock fixture is unexpectedly powered");
            samples.start = anchor(body).z;
            samples.launched = true;
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            // Apply a real velocity impulse before native actor callbacks/solver.
            // Measuring every subsequent substep prevents later recovery masking slip.
            physics.getPipeline().addLinearAndAngularVelocity(body, new Vector3d(0, 0, speed), new Vector3d());
        });
        h.runAfterDelay(40, () -> {
            samples.active = false;
            LOGGER.info("Axial impulse screw={} deltaV={} native samples: {}", screw, speed, samples);
            h.assertTrue(samples.count >= 30, "Too few native impulse samples: " + samples);
            h.assertTrue(!body.isRemoved() && entity(body).hasJoint(), "Impulse released its guiding joint: " + samples);
            if (screw) h.assertTrue(samples.peakExcursion <= .035,
                    "An external impulse back-drove the unpowered screw: " + samples);
            else h.assertTrue(samples.forwardTravel > 1,
                    "Passive control was incorrectly locked instead of sliding: " + samples);
            h.succeed();
        });
    }

    private static final class Samples {
        private boolean active = true, launched;
        private int count;
        private double start, peakExcursion, forwardTravel;
        @Override public String toString() {
            return "samples=" + count + ", peakExcursion=" + peakExcursion + ", forwardTravel=" + forwardTravel;
        }
    }

    private static final class PoweredSamples {
        private boolean active = true, launched;
        private int count;
        private double start, previous, elapsed, peakReverse, peakExcessAdvance, cumulativeOverrun;
        @Override public String toString() {
            return "samples=" + count + ", peakReverse=" + peakReverse + ", peakExcessAdvance=" + peakExcessAdvance
                    + ", cumulativeOverrun=" + cumulativeOverrun;
        }
    }

    private static final class StallSamples {
        private boolean active = true, stalled;
        private int count;
        private long contactTime = -1;
        private double minimumGap = Double.POSITIVE_INFINITY, wallPenetration;
        private double leaderStart, followerStart, leaderDrift, followerDrift;
        @Override public String toString() {
            return "samples=" + count + ", contactTime=" + contactTime + ", minimumGap=" + minimumGap
                    + ", wallPenetration=" + wallPenetration + ", leaderDrift=" + leaderDrift + ", followerDrift=" + followerDrift;
        }
    }

    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static CarriageEntity entity(ServerSubLevel body) { return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock()); }
}
