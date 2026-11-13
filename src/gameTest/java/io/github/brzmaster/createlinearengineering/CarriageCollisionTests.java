package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Observe every native substep: recovering outside the wall later is still penetration. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class CarriageCollisionTests {
    private static final double MAX_PENETRATION = .035;

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void fastPassiveCarriageCannotTunnelSouthThroughOneBlockWall(GameTestHelper h) {
        fastPassive(h, 180, Direction.SOUTH);
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void fastPassiveCarriageCannotTunnelNorthThroughOneBlockWall(GameTestHelper h) {
        fastPassive(h, 181, Direction.NORTH);
    }

    private static void fastPassive(GameTestHelper h, int index, Direction axis) {
        Fixture fixture = fixture(h, index, axis, false);
        Monitor monitor = new Monitor(h.getLevel(), fixture);
        h.runAfterDelay(5, () -> {
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            // Four metres per native substep would completely skip a 1m wall.
            // Set velocity before actor callbacks, as ordinary player/game forces do.
            double speed = 4 * 20 * physics.getConfig().substepsPerTick;
            Vector3d target = new Vector3d(axis.getStepX(), axis.getStepY(), axis.getStepZ()).mul(speed);
            var handle = physics.getPhysicsHandle(fixture.body());
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    target.sub(handle.getLinearVelocity(new Vector3d())), handle.getAngularVelocity(new Vector3d()).negate());
            monitor.launched = true;
        });
        h.runAfterDelay(30, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxTravel > 1.8, "High-speed fixture never reached its wall: " + monitor);
            h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION,
                    "Native substep tunneled into/through the wall: " + monitor);
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture.body()).hasJoint(),
                    "Wall collision destroyed/released its mounted carriage: " + monitor);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void poweredScrewStallsWithoutPenetrationAndCanReverse(GameTestHelper h) {
        screwReverse(h, 182, Direction.SOUTH);
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void northFacingPoweredScrewStallsAndReverses(GameTestHelper h) {
        screwReverse(h, 186, Direction.NORTH);
    }

    private static void screwReverse(GameTestHelper h, int index, Direction axis) {
        Fixture fixture = fixture(h, index, axis, true);
        Monitor monitor = new Monitor(h.getLevel(), fixture);
        h.runAfterDelay(65, () -> {
            assertReachedWall(h, monitor);
            monitor.stallWindow = true;
        });
        h.runAfterDelay(90, () -> {
            assertStableStop(h, monitor);
            monitor.stallWindow = false;
            monitor.blockedPosition = projection(localAnchor(fixture), axis);
            fixture.motor().generatedSpeed.setValue(-256);
        });
        h.runAfterDelay(130, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION, "Screw entered its wall during pressure/reversal: " + monitor);
            h.assertTrue(projection(localAnchor(fixture), axis) < monitor.blockedPosition - .65,
                    "Reversed screw stayed stuck at its stop: " + monitor);
            h.assertTrue(entity(fixture.body()).hasJoint(), "Reversal lost the screw joint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void releasingPoweredScrewStopDoesNotLaunchStoredCommand(GameTestHelper h) {
        Fixture fixture = fixture(h, 183, Direction.SOUTH, true);
        Monitor monitor = new Monitor(h.getLevel(), fixture);
        h.runAfterDelay(65, () -> {
            assertReachedWall(h, monitor);
            monitor.stallWindow = true;
        });
        h.runAfterDelay(90, () -> {
            assertStableStop(h, monitor);
            monitor.stallWindow = false;
            monitor.blockedPosition = anchor(fixture.body()).z;
            // Restore the missing screw segment as well as removing the real wall.
            // This tests release with a continuous powered track, not free fall.
            removeWall(h, fixture.wall());
            h.getLevel().setBlock(fixture.wall(), fixture.rail(), 3);
            monitor.released = true;
        });
        h.runAfterDelay(115, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION, "Screw penetrated before release: " + monitor);
            h.assertTrue(anchor(fixture.body()).z > monitor.blockedPosition + .4, "Released screw did not resume travel: " + monitor);
            h.assertTrue(monitor.maxReleaseSpeed < 6,
                    "Blocked motor accumulated a launch impulse (nominal travel is 1.067m/s): " + monitor);
            h.assertTrue(entity(fixture.body()).hasJoint(), "Release lost the continuous screw track");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void fastCarriageStopsAtWallOnRotatedParent(GameTestHelper h) {
        BlockPos origin = new BlockPos(184 * 48 + 8, 100, 8), wall = origin.south(3);
        BlockState rail = CreateLinearEngineering.RAIL.get().defaultBlockState();
        var blocks = new java.util.LinkedHashSet<BlockPos>();
        for (int i = 0; i <= 8; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, rail, 3);
            blocks.add(pos);
        }
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) {
            BlockPos pos = wall.offset(x, y, 0);
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            blocks.add(pos);
        }
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, blocks,
                new BoundingBox3i(origin.getX() - 1, origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY() + 2, origin.getZ() + 8));
        BlockPos mount = parent.getPlot().getCenterBlock();
        Vector3d fixed = RailContact.anchor(parent, mount);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(fixed.sub(RailContact.anchor(parent, mount)));
        var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
        physics.getPipeline().teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), mount);
        h.assertTrue(body != null, "Cannot place carriage on rotated parent");
        Fixture fixture = new Fixture(mount, mount.south(3), Direction.SOUTH, rail, body, null, parent);
        Monitor monitor = new Monitor(h.getLevel(), fixture);
        h.runAfterDelay(5, () -> {
            double speed = 4 * 20 * physics.getConfig().substepsPerTick;
            Vector3d target = parent.logicalPose().orientation().transform(new Vector3d(0, 0, speed));
            target.add(Sable.HELPER.getVelocity(h.getLevel(), parent,
                    new Vector3d(mount.getX() + .5, mount.getY() + .5, mount.getZ() + .5), new Vector3d()));
            var handle = physics.getPhysicsHandle(body);
            physics.getPipeline().addLinearAndAngularVelocity(body, target.sub(handle.getLinearVelocity(new Vector3d())), new Vector3d());
            monitor.launched = true;
        });
        h.runAfterDelay(30, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxTravel > 1.8 && monitor.maxPenetration <= MAX_PENETRATION,
                    "Rotated-parent collision limit used the wrong coordinate frame: " + monitor);
            h.assertTrue(!body.isRemoved() && entity(body).hasJoint(), "Rotated-parent impact released the carriage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void sleeveCanPassEndRodInsideItsCentralOpening(GameTestHelper h) {
        Fixture fixture = fixture(h, 185, Direction.SOUTH, false);
        h.getLevel().setBlock(entity(fixture.body()).getBlockPos(), entity(fixture.body()).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        removeWall(h, fixture.wall());
        // The Z-aligned rod is [6,6,0]..[10,10,16]: wholly inside the U's
        // [6,4,0]..[10,12,16] opening. A bounding-box sweep would falsely stop it.
        h.getLevel().setBlock(fixture.wall(), Blocks.END_ROD.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH), 3);
        Monitor monitor = new Monitor(h.getLevel(), fixture);
        monitor.haltBeyond = 3;
        h.runAfterDelay(5, () -> {
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            double speed = 4 * 20 * physics.getConfig().substepsPerTick;
            var handle = physics.getPhysicsHandle(fixture.body());
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    new Vector3d(0, 0, speed).sub(handle.getLinearVelocity(new Vector3d())), new Vector3d());
        });
        h.runAfterDelay(18, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxTravel > 3.5, "Non-intersecting partial shape became an artificial end stop: " + monitor);
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture.body()).hasJoint(), "Open passage lost its carriage joint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void fastCoupledFrontPayloadCannotPenetrateEndWall(GameTestHelper h) {
        Fixture fixture = fixture(h, 187, Direction.SOUTH, false);
        BlockPos carriage = fixture.body().getPlot().getCenterBlock();
        BlockPos top = carriage.above(), front = top.south();
        // The bridge sits above the track rather than overlapping the T profile.
        // Its leading block reaches the wall before the sleeve's own front does.
        h.getLevel().setBlock(top, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(front, Blocks.STONE.defaultBlockState(), 3);
        Monitor monitor = new Monitor(h.getLevel(), fixture, java.util.List.of(carriage, top, front));
        h.runAfterDelay(5, () -> {
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            double speed = 4 * 20 * physics.getConfig().substepsPerTick;
            var handle = physics.getPhysicsHandle(fixture.body());
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    new Vector3d(0, 0, speed).sub(handle.getLinearVelocity(new Vector3d())),
                    handle.getAngularVelocity(new Vector3d()).negate());
            monitor.launched = true;
        });
        h.runAfterDelay(30, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxTravel > .7, "Coupled-payload fixture never reached its wall: " + monitor);
            h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION,
                    "Forward coupled payload penetrated before the sleeve reached its own limit: " + monitor);
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture.body()).hasJoint(), "Payload impact lost its mounted carriage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void carriageStaysInLegalPayloadHoleAroundFixedObstacle(GameTestHelper h) {
        Fixture fixture = fixture(h, 189, Direction.SOUTH, false);
        removeWall(h, fixture.wall());
        h.getLevel().setBlock(fixture.wall(), fixture.rail(), 3);
        BlockPos carriage = fixture.body().getPlot().getCenterBlock();
        // Two sides bound a one-block axial opening. An upper bridge connects
        // them without intersecting the fixed world block inside that opening.
        for (BlockPos pos : java.util.List.of(carriage.above(), carriage.above().south(2),
                carriage.above(2), carriage.above(2).south(), carriage.above(2).south(2)))
            h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(fixture.mount().above().south(), Blocks.STONE.defaultBlockState(), 3);
        Vector3d initial = anchor(fixture.body());
        double[] maxDrift = {0};
        boolean[] active = {true};
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!active[0] || physics.getLevel() != h.getLevel() || fixture.body().isRemoved()) return;
            maxDrift[0] = Math.max(maxDrift[0], anchor(fixture.body()).distance(initial));
        });
        h.runAfterDelay(35, () -> {
            active[0] = false;
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture.body()).hasJoint(), "Legal payload hole lost its joint/body");
            h.assertTrue(maxDrift[0] < .03,
                    "Synthetic collision reference expelled a legitimate body from its opening: " + maxDrift[0]);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void thinForwardPayloadStopsAndRemovalUpdatesItsCollisionLimit(GameTestHelper h) {
        Fixture fixture = fixture(h, 188, Direction.SOUTH, false);
        BlockPos carriage = fixture.body().getPlot().getCenterBlock(), top = carriage.above(), front = top.south();
        h.getLevel().setBlock(top, Blocks.STONE.defaultBlockState(), 3);
        // Open SOUTH trapdoor occupies only Z[0,3/16] of its forward cell. Its
        // legal stop lies before a rounded supporting-segment centre.
        h.getLevel().setBlock(front, Blocks.IRON_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 3);
        Monitor monitor = new Monitor(h.getLevel(), fixture, java.util.List.of(carriage, top, front));
        h.runAfterDelay(5, () -> {
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            double speed = 4 * 20 * physics.getConfig().substepsPerTick;
            var handle = physics.getPhysicsHandle(fixture.body());
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    new Vector3d(0, 0, speed).sub(handle.getLinearVelocity(new Vector3d())),
                    handle.getAngularVelocity(new Vector3d()).negate());
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(monitor.maxTravel > 1.55 && monitor.maxPenetration <= MAX_PENETRATION,
                    "Thin protruding payload was ignored or started from an intersecting reference: " + monitor);
            h.getLevel().removeBlock(front, false);
            var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
            var handle = physics.getPhysicsHandle(fixture.body());
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    new Vector3d(0, 0, 4).sub(handle.getLinearVelocity(new Vector3d())), new Vector3d());
        });
        h.runAfterDelay(35, () -> {
            monitor.active = false;
            h.assertTrue(monitor.maxTravel > 1.93 && monitor.maxTravel < 2.035 && monitor.maxPenetration <= MAX_PENETRATION,
                    "Removing the thin payload kept an obsolete stop or lost the remaining block's stop: " + monitor);
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture.body()).hasJoint(), "Payload edit released the carriage");
            h.succeed();
        });
    }

    private static void assertReachedWall(GameTestHelper h, Monitor monitor) {
        h.assertTrue(monitor.maxTravel > .85, "Powered screw never reached its physical stop: " + monitor);
        h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION, "Powered screw compressed/entered its wall: " + monitor);
        h.assertTrue(Math.abs(Math.abs(((RailEntity) h.getLevel().getBlockEntity(monitor.fixture.mount())).getSpeed()) - 256) < .01,
                "Fixture motor did not supply 256 RPM");
    }

    private static void assertStableStop(GameTestHelper h, Monitor monitor) {
        h.assertTrue(monitor.maxPenetration <= MAX_PENETRATION, "Sustained motor pressure penetrated its stop: " + monitor);
        h.assertTrue(Double.isFinite(monitor.stallMin) && monitor.stallMax - monitor.stallMin < .05,
                "Motor jittered against the stop: " + monitor);
    }

    private record Fixture(BlockPos mount, BlockPos wall, Direction axis, BlockState rail,
                           ServerSubLevel body, CreativeMotorBlockEntity motor, ServerSubLevel parent) {}

    private static Fixture fixture(GameTestHelper h, int index, Direction axis, boolean screw) {
        BlockPos origin = new BlockPos(index * 48 + 8, 100, 8);
        BlockState rail = (screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().defaultBlockState()
                .setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, Direction.UP);
        for (int i = screw ? 0 : -2; i <= 8; i++) {
            BlockPos pos = origin.relative(axis, i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, rail, 3);
        }
        CreativeMotorBlockEntity motor = null;
        if (screw) {
            BlockPos motorPos = origin.relative(axis.getOpposite(), 2);
            h.getLevel().setBlock(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(BlockStateProperties.FACING, axis), 3);
            motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(motorPos);
            // CreativeMotorBlockEntity already converts its UI value to the
            // facing's signed shaft speed. Applying that sign here would double it.
            motor.generatedSpeed.setValue(256);
            h.getLevel().setBlock(origin.relative(axis.getOpposite()), AllBlocks.SHAFT.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        }
        BlockPos wall = origin.relative(axis, 3);
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        BlockPos mount = screw ? origin.relative(axis) : origin;
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), mount);
        h.assertTrue(body != null, "Cannot create wall-collision carriage");
        return new Fixture(mount, wall, axis, rail, body, motor, null);
    }

    private static void removeWall(GameTestHelper h, BlockPos wall) {
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) h.getLevel().removeBlock(wall.offset(x, y, 0), false);
    }

    private static final class Monitor {
        private final Fixture fixture;
        private final java.util.List<BlockPos> collisionBlocks;
        private final double initialProjection, wallProjection;
        private boolean active = true, launched, stallWindow, released;
        private double maxTravel, maxPenetration, maxReleaseSpeed;
        private double stallMin = Double.POSITIVE_INFINITY, stallMax = Double.NEGATIVE_INFINITY;
        private double blockedPosition;
        private double haltBeyond = Double.POSITIVE_INFINITY;

        private Monitor(ServerLevel level, Fixture fixture) {
            this(level, fixture, java.util.List.of(fixture.body().getPlot().getCenterBlock()));
        }

        private Monitor(ServerLevel level, Fixture fixture, java.util.List<BlockPos> collisionBlocks) {
            this.fixture = fixture;
            this.collisionBlocks = collisionBlocks;
            initialProjection = projection(localAnchor(fixture), fixture.axis());
            wallProjection = projection(RailContact.anchor(null, fixture.wall()), fixture.axis()) - .5;
            SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
                if (!active || physics.getLevel() != level || fixture.body().isRemoved()) return;
                double position = projection(localAnchor(fixture), fixture.axis());
                maxTravel = Math.max(maxTravel, position - initialProjection);
                if (!released) maxPenetration = Math.max(maxPenetration, leadingProjection(fixture, collisionBlocks) - wallProjection);
                if (stallWindow) { stallMin = Math.min(stallMin, position); stallMax = Math.max(stallMax, position); }
                if (released) maxReleaseSpeed = Math.max(maxReleaseSpeed,
                        physics.getPhysicsHandle(fixture.body()).getLinearVelocity(new Vector3d()).length());
                if (position - initialProjection > haltBeyond) {
                    physics.getPipeline().resetVelocity(fixture.body());
                    haltBeyond = Double.POSITIVE_INFINITY;
                }
            });
        }

        @Override public String toString() {
            return "travel=" + maxTravel + ", penetration=" + maxPenetration + ", jitter=" + (stallMax - stallMin)
                    + ", releaseSpeed=" + maxReleaseSpeed + ", launched=" + launched
                    + ", removed=" + fixture.body().isRemoved() + ", anchor=" + anchor(fixture.body());
        }
    }

    private static double leadingProjection(Fixture fixture, java.util.List<BlockPos> blocks) {
        ServerSubLevel body = fixture.body();
        double result = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : blocks) {
            BlockState state = body.getLevel().getBlockState(pos);
            for (var box : state.getCollisionShape(body.getLevel(), pos).toAabbs()) for (int i = 0; i < 8; i++) {
                Vector3d corner = new Vector3d(pos.getX() + ((i & 1) == 0 ? box.minX : box.maxX),
                        pos.getY() + ((i & 2) == 0 ? box.minY : box.maxY),
                        pos.getZ() + ((i & 4) == 0 ? box.minZ : box.maxZ));
                body.logicalPose().transformPosition(corner);
                if (fixture.parent() != null) fixture.parent().logicalPose().transformPositionInverse(corner);
                result = Math.max(result, projection(corner, fixture.axis()));
            }
        }
        return result;
    }

    private static double projection(Vector3d v, Direction axis) { return v.dot(axis.getStepX(), axis.getStepY(), axis.getStepZ()); }
    private static Vector3d localAnchor(Fixture fixture) {
        Vector3d result = anchor(fixture.body());
        if (fixture.parent() != null) fixture.parent().logicalPose().transformPositionInverse(result);
        return result;
    }
    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static CarriageEntity entity(ServerSubLevel body) { return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock()); }
}
