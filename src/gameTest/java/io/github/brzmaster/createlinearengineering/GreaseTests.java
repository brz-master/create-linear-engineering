package io.github.brzmaster.createlinearengineering;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.function.Consumer;

/** Exercise lubrication through real item use, persistence and the native Sable solver. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class GreaseTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativePassiveFrictionOrdersDryHighAndLow(GameTestHelper h) {
        Grease[] finishes = {Grease.NONE, Grease.HIGH, Grease.LOW};
        ServerSubLevel[] bodies = new ServerSubLevel[finishes.length];
        double[] starts = new double[finishes.length];
        for (int i = 0; i < finishes.length; i++) {
            bodies[i] = carriage(h, 210 + i, false);
            finish(h, bodies[i], finishes[i]);
        }
        var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
        h.runAfterDelay(10, () -> {
            for (int i = 0; i < bodies.length; i++) {
                starts[i] = anchor(bodies[i]).z;
                physics.getPipeline().addLinearAndAngularVelocity(bodies[i], new Vector3d(0, 0, 4), new Vector3d());
            }
        });
        h.runAfterDelay(55, () -> {
            double[] travel = new double[bodies.length], velocities = new double[bodies.length];
            for (int i = 0; i < bodies.length; i++) {
                travel[i] = anchor(bodies[i]).z - starts[i];
                h.assertTrue(entity(bodies[i]).hasJoint(), "Friction fixture lost its guiding joint");
                double velocity = velocities[i] = physics.getPhysicsHandle(bodies[i]).getLinearVelocity(new Vector3d()).z;
                h.assertTrue(velocity > -.05, "Passive friction reversed the body instead of resisting it: " + finishes[i]);
            }
            String measured = java.util.Arrays.toString(travel);
            LOGGER.info("Native passive grease (dry/high/low): travel={}, finalVelocity={}",
                    measured, java.util.Arrays.toString(velocities));
            // A 4 m/s push must stop a dry carriage well inside one block, without
            // locking it. Native solver discretization prevents an exact 2:1 distance
            // ratio, but halving the brake should approximately double the travel.
            h.assertTrue(travel[0] > .03 && travel[0] < .35,
                    "Dry carriage must slide briefly and brake strongly: " + measured);
            double distanceRatio = travel[1] / travel[0];
            h.assertTrue(distanceRatio > 1.45 && distanceRatio < 2.55,
                    "Half-strength High grease brake has the wrong travel ratio: " + distanceRatio + "; " + measured);
            h.assertTrue(Math.abs(velocities[0]) < .03 && Math.abs(velocities[1]) < .03,
                    "Dry or High carriage kept drifting after its impulse: " + java.util.Arrays.toString(velocities));
            h.assertTrue(travel[2] > 6 && velocities[2] > 2.5,
                    "Low grease added a brake instead of retaining native Sable drag: " + measured
                            + "; velocity=" + velocities[2]);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void dryVerticalCarriageDescendsSlowlyInsteadOfLocking(GameTestHelper h) {
        ServerSubLevel[] bodies = new ServerSubLevel[2];
        double[] initialY = new double[2];
        for (int i = 0; i < bodies.length; i++) {
            BlockPos origin = origin(h, 222 + i);
            for (int height = -8; height <= 2; height++)
                h.getLevel().setBlock(origin.above(height), CreateLinearEngineering.RAIL.get().defaultBlockState()
                        .setValue(LinearBlock.FACING, Direction.UP).setValue(LinearBlock.NORMAL, Direction.NORTH), 3);
            bodies[i] = CarriagePlacement.place(h.getLevel(), origin);
            h.assertTrue(bodies[i] != null, "Cannot create vertical friction carriage");
            finish(h, bodies[i], i == 0 ? Grease.NONE : Grease.LOW);
            initialY[i] = anchor(bodies[i]).y;
        }
        h.runAfterDelay(40, () -> {
            double dryFall = initialY[0] - anchor(bodies[0]).y;
            double lowFall = initialY[1] - anchor(bodies[1]).y;
            LOGGER.info("Native vertical grease: dryFall={}, lowFall={}", dryFall, lowFall);
            h.assertTrue(dryFall > .05 && dryFall < 1.5 && entity(bodies[0]).hasJoint(),
                    "Dry vertical carriage should descend slowly, not lock or free-fall: " + dryFall);
            h.assertTrue(lowFall > 4 && lowFall > dryFall + 3,
                    "Low grease did not allow gravity-driven vertical travel: dry=" + dryFall + ", low=" + lowFall);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void dryFrictionRespectsCommonMotionOnRotatedSublevel(GameTestHelper h) {
        BlockPos origin = origin(h, 221);
        java.util.List<BlockPos> rails = new java.util.ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
            rails.add(pos);
        }
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, rails,
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ() - 2,
                        origin.getX(), origin.getY(), origin.getZ() + 2));
        BlockPos mount = parent.getPlot().getCenterBlock();
        Vector3d initial = RailContact.anchor(parent, mount);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(initial.sub(RailContact.anchor(parent, mount)));
        var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
        pipeline.teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        Vector3d commonVelocity = parent.logicalPose().orientation().transform(new Vector3d(0, 0, 3));
        pipeline.addLinearAndAngularVelocity(parent, commonVelocity, new Vector3d());
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), mount);
        h.assertTrue(child != null, "Cannot place dry carriage on moving parent");
        h.runAfterDelay(30, () -> {
            Vector3d local = parent.logicalPose().transformPositionInverse(anchor(child));
            double drift = local.distance(new Vector3d(mount.getX() + .5, mount.getY() + .5, mount.getZ() + .5));
            h.assertTrue(drift < .10 && entity(child).hasJoint(),
                    "Friction opposed shared world motion instead of relative sliding: " + drift);
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void greaseItemConsumesOnlyOnRealCarriageChanges(GameTestHelper h) {
        FakePlayer player = player(h);
        for (boolean screw : new boolean[]{false, true}) {
            ServerSubLevel body = carriage(h, screw ? 214 : 213, screw);
            BlockPos pos = body.getPlot().getCenterBlock();
            player.setGameMode(GameType.SURVIVAL);
            ItemStack low = new ItemStack(CreateLinearEngineering.LOW_GREASE.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, low);
            h.assertTrue(click(h, player, pos).consumesAction(), "Grease refused a carriage");
            h.assertTrue(low.getCount() == 2 && state(h, body).getValue(LinearBlock.GREASE) == Grease.LOW,
                    "First application did not persist or consume exactly one item");
            h.assertTrue(click(h, player, pos).consumesAction() && low.getCount() == 2,
                    "Repeated identical grease consumed another item");
            ItemStack high = new ItemStack(CreateLinearEngineering.HIGH_GREASE.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, high);
            h.assertTrue(click(h, player, pos).consumesAction(), "Switching grease failed");
            h.assertTrue(high.getCount() == 2 && state(h, body).getValue(LinearBlock.GREASE) == Grease.HIGH,
                    "Switch did not replace the coating or consume exactly one item");
            player.setGameMode(GameType.CREATIVE);
            player.setItemInHand(InteractionHand.MAIN_HAND, low);
            h.assertTrue(click(h, player, pos).consumesAction() && low.getCount() == 2,
                    "Creative application consumed grease");
            h.assertTrue(entity(body).hasJoint(), "Applying grease destroyed the native joint");
        }
        player.setGameMode(GameType.SURVIVAL);
        ItemStack low = new ItemStack(CreateLinearEngineering.LOW_GREASE.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, low);
        h.assertTrue(!click(h, player, origin(h, 213)).consumesAction() && low.getCount() == 3,
                "Grease applied to a rail instead of a carriage");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledGreaseChangePreservesStateAndInventory(GameTestHelper h) {
        ServerSubLevel body = carriage(h, 215, false);
        BlockPos pos = body.getPlot().getCenterBlock();
        Vector3d point = anchor(body);
        BlockPos world = BlockPos.containing(point.x, point.y, point.z);
        FakePlayer player = player(h);
        ItemStack stack = new ItemStack(CreateLinearEngineering.LOW_GREASE.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockState before = state(h, body);
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() != player) return;
            events[0]++;
            h.assertTrue(event.getPos().equals(world), "Grease protection event exposed plot coordinates");
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(!click(h, player, pos).consumesAction(), "Canceled grease application reported success");
            h.assertTrue(events[0] == 1, "Expected one projected protection event, got " + events[0]);
            h.assertTrue(stack.getCount() == 3 && state(h, body) == before,
                    "Canceled grease application changed inventory or carriage");
            h.assertTrue(entity(body).hasJoint(), "Canceled grease application removed its guiding joint");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void greasePersistsThroughVanillaStateAndEntityReload(GameTestHelper h) {
        ServerSubLevel[] bodies = {carriage(h, 216, false), carriage(h, 217, true)};
        finish(h, bodies[0], Grease.HIGH);
        finish(h, bodies[1], Grease.LOW);
        h.runAfterDelay(10, () -> {
            for (ServerSubLevel body : bodies) {
                CarriageEntity old = entity(body);
                BlockPos pos = old.getBlockPos();
                var savedState = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, old.getBlockState()).getOrThrow();
                BlockState restoredState = BlockState.CODEC.parse(NbtOps.INSTANCE, savedState).getOrThrow();
                var savedEntity = old.saveWithFullMetadata(h.getLevel().registryAccess());
                h.getLevel().removeBlockEntity(pos);
                h.getLevel().setBlock(pos, restoredState, 3);
                CarriageEntity restored = new CarriageEntity(pos, restoredState);
                restored.loadWithComponents(savedEntity, h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(restored);
                body.getPlot().onBlockChange(pos, restoredState);
            }
        });
        h.runAfterDelay(100, () -> {
            h.assertTrue(state(h, bodies[0]).getValue(LinearBlock.GREASE) == Grease.HIGH,
                    "High coating was lost or expired after reload");
            h.assertTrue(state(h, bodies[1]).getValue(LinearBlock.GREASE) == Grease.LOW,
                    "Low coating was lost or expired after reload");
            for (ServerSubLevel body : bodies)
                h.assertTrue(entity(body).hasJoint(), "Reloaded lubricated carriage lost its native joint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void lubricationDoesNotBackDriveAnUnpoweredScrew(GameTestHelper h) {
        ServerSubLevel[] bodies = {carriage(h, 218, true), carriage(h, 219, true)};
        finish(h, bodies[0], Grease.HIGH);
        finish(h, bodies[1], Grease.LOW);
        double[] starts = new double[2], peak = new double[2];
        boolean[] active = {false};
        int[] samples = {0};
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!active[0] || physics.getLevel() != h.getLevel()) return;
            samples[0]++;
            for (int i = 0; i < bodies.length; i++) {
                if (!bodies[i].isRemoved()) peak[i] = Math.max(peak[i], Math.abs(anchor(bodies[i]).z - starts[i]));
            }
        });
        h.runAfterDelay(10, () -> {
            var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
            for (int i = 0; i < bodies.length; i++) {
                starts[i] = anchor(bodies[i]).z;
                pipeline.addLinearAndAngularVelocity(bodies[i], new Vector3d(0, 0, i == 0 ? 8 : -8), new Vector3d());
            }
            active[0] = true;
        });
        h.runAfterDelay(40, () -> {
            active[0] = false;
            h.assertTrue(samples[0] >= 30, "Too few native screw impulse samples");
            for (int i = 0; i < bodies.length; i++) {
                h.assertTrue(!bodies[i].isRemoved() && entity(bodies[i]).hasJoint(), "Lubricated screw lost its guiding joint");
                h.assertTrue(peak[i] <= .035, "Grease allowed an external impulse to back-drive the screw: " + peak[i]);
            }
            h.succeed();
        });
    }

    private static ServerSubLevel carriage(GameTestHelper h, int index, boolean screw) {
        BlockPos origin = origin(h, index);
        for (int i = -16; i <= 16; i++) {
            BlockPos pos = origin.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, (screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().defaultBlockState(), 3);
        }
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), origin);
        h.assertTrue(body != null, "Cannot create grease test carriage");
        return body;
    }

    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }

    private static void finish(GameTestHelper h, ServerSubLevel body, Grease grease) {
        h.getLevel().setBlock(body.getPlot().getCenterBlock(), state(h, body).setValue(LinearBlock.GREASE, grease), 3);
    }
    private static BlockState state(GameTestHelper h, ServerSubLevel body) {
        return h.getLevel().getBlockState(body.getPlot().getCenterBlock());
    }
    private static CarriageEntity entity(ServerSubLevel body) {
        return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "GreaseTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.setPos(0, 200, 0);
        return player;
    }
    private static net.minecraft.world.InteractionResult click(GameTestHelper h, FakePlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
