package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class LinearPhysicsTests {
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void south(GameTestHelper h) { axis(h, Direction.SOUTH); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void north(GameTestHelper h) { axis(h, Direction.NORTH); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void east(GameTestHelper h) { axis(h, Direction.EAST); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void west(GameTestHelper h) { axis(h, Direction.WEST); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void up(GameTestHelper h) { axis(h, Direction.UP); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void down(GameTestHelper h) { axis(h, Direction.DOWN); }

    private static void axis(GameTestHelper h, Direction facing) {
        BlockPos origin = testOrigin(h, facing.ordinal());
        for (int i = -2; i <= 2; i++)
            h.getLevel().setBlock(origin.relative(facing, i), CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, facing), 3);
        ServerSubLevel child = assemble(h, origin, facing);
        h.assertTrue(entity(child).hasJoint(), "Missing initial prismatic joint " + facing);
        // This fixture verifies open-end release, independently of lubrication drag.
        if (facing.getAxis().isVertical()) h.getLevel().setBlock(entity(child).getBlockPos(),
                entity(child).getBlockState().setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Vector3d initial = anchor(child);
        h.runAfterDelay(100, () -> {
            Vector3d end = anchor(child);
            if (facing.getAxis().isVertical()) {
                h.assertTrue(end.y < initial.y - 4, "Open vertical rail did not release: " + end);
                h.assertTrue(!entity(child).hasJoint(), "End of rail still has a stop/joint");
            } else {
                h.assertTrue(end.distance(initial) < .16, "Horizontal rail drifted: " + end);
                h.assertTrue(entity(child).hasJoint(), "Missing native Sable joint");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void singleSegment(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 6);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, Direction.UP), 3);
        ServerSubLevel child = assemble(h, origin, Direction.UP);
        h.getLevel().setBlock(entity(child).getBlockPos(), entity(child).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Vector3d start = anchor(child);
        h.runAfterDelay(80, () -> {
            h.assertTrue(anchor(child).y < start.y - 4 && !entity(child).isBound(), "Single segment acts as an artificial stop");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nestedRail(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 7);
        List<BlockPos> rails = List.of(origin.north(), origin, origin.south());
        for (BlockPos pos : rails) h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, rails,
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ() - 1, origin.getX(), origin.getY(), origin.getZ() + 1));
        BlockPos parentRail = parent.getPlot().getCenterBlock();
        ServerSubLevel child = assemble(h, parentRail, Direction.SOUTH);
        h.runAfterDelay(20, () -> {
            Vector3d railWorld = new Vector3d(parentRail.getX() + .5, parentRail.getY() + .5, parentRail.getZ() + .5);
            parent.logicalPose().transformPosition(railWorld);
            h.assertTrue(anchor(child).distance(railWorld) < .18, "Nested carriage failed to follow falling parent");
            h.assertTrue(entity(child).hasJoint(), "Nested joint absent");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void breakingRailReleases(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 8);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        double startY = anchor(child).y;
        h.runAfterDelay(5, () -> h.getLevel().setBlock(origin, Blocks.AIR.defaultBlockState(), 3));
        h.runAfterDelay(25, () -> {
            h.assertTrue(!entity(child).hasJoint(), "Joint survives missing rail");
            h.assertTrue(anchor(child).y < startY - .5, "Released body did not fall");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void frameBasis(GameTestHelper h) {
        for (Direction facing : Direction.values()) {
            Vector3d axis = RailGeometry.frame(facing).transform(new Vector3d(0, 0, 1));
            Vector3d normal = RailGeometry.frame(facing).transform(new Vector3d(0, 1, 0));
            Direction n = RailGeometry.normal(facing);
            h.assertTrue(axis.distance(new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ())) < 1e-9, "Invalid frame " + facing);
            h.assertTrue(normal.distance(new Vector3d(n.getStepX(), n.getStepY(), n.getStepZ())) < 1e-9, "Invalid normal " + facing);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void mixedRailSlides(GameTestHelper h) { slide(h, Direction.SOUTH, 9); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void eastRailSlides(GameTestHelper h) { slide(h, Direction.EAST, 10); }

    private static void slide(GameTestHelper h, Direction facing, int index) {
        BlockPos origin = testOrigin(h, index);
        for (int i = -2; i <= 2; i++) h.getLevel().setBlock(origin.relative(facing, i),
                CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, i % 2 == 0 ? facing : facing.getOpposite()), 3);
        ServerSubLevel child = assemble(h, origin, facing);
        h.getLevel().setBlock(entity(child).getBlockPos(), entity(child).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Vector3d initial = anchor(child);
        double[] flight = {0, 0, 0};
        boolean[] observing = {true};
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!observing[0] || physics.getLevel() != h.getLevel() || child.isRemoved()) return;
            flight[0] = Math.max(flight[0], anchor(child).sub(initial).dot(facing.getStepX(), 0, facing.getStepZ()));
            flight[2] = physics.getPhysicsHandle(child).getLinearVelocity(new Vector3d()).dot(facing.getStepX(), 0, facing.getStepZ());
            flight[1] = Math.max(flight[1], flight[2]);
        });
        h.runAfterDelay(5, () -> SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .addLinearAndAngularVelocity(child, new Vector3d(facing.getStepX() * 4, 0, facing.getStepZ() * 4), new Vector3d()));
        h.runAfterDelay(70, () -> {
            observing[0] = false;
            double along = anchor(child).sub(initial).dot(facing.getStepX(), 0, facing.getStepZ());
            h.assertTrue(along > 3 && !entity(child).isBound(), "Open rail did not release the carriage: " + along
                    + "; peakTravel=" + flight[0] + ", peakSpeed=" + flight[1] + ", finalSpeed=" + flight[2]
                    + ", state=" + entity(child).getBlockState() + ", bound=" + entity(child).isBound());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void assembledRailKeepsCarriage(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 11);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, List.of(origin),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX(), origin.getY(), origin.getZ()));
        h.runAfterDelay(20, () -> {
            h.assertTrue(anchor(child).distance(anchor(parent)) < .16, "Moving rail lost its carriage");
            h.assertTrue(SubLevelHelper.getConnectedChain(parent).contains(child), "Parent lacks child dependency");
            h.assertTrue(SubLevelHelper.getConnectedChain(child).contains(parent), "Child lacks parent dependency");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void carriageRestoresFromNbt(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 12);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        h.runAfterDelay(10, () -> {
            CarriageEntity old = entity(child);
            CompoundTag nbt = old.saveWithFullMetadata(h.getLevel().registryAccess());
            BlockPos pos = old.getBlockPos();
            var state = old.getBlockState();
            h.getLevel().removeBlockEntity(pos);
            CarriageEntity restored = new CarriageEntity(pos, state);
            restored.loadWithComponents(nbt, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(restored);
            // Match Sable's chunk restoration: it refreshes actor registration
            // through onBlockChange after restoring each block entity.
            child.getPlot().onBlockChange(pos, state);
        });
        h.runAfterDelay(35, () -> {
            h.assertTrue(entity(child).isBound() && entity(child).hasJoint(), "Persisted carriage did not recreate joint");
            h.assertTrue(anchor(child).distance(new Vector3d(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5)) < .16, "Restored joint drifted");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void rotatedRailTransfer(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 13);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        BlockPos target = origin.south(4);
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(origin, target, 0, Rotation.CLOCKWISE_180, h.getLevel());
        SubLevelAssemblyHelper.moveBlocks(h.getLevel(), transform, List.of(origin));
        h.runAfterDelay(40, () -> {
            h.assertTrue(anchor(child).distance(new Vector3d(target.getX() + .5, target.getY() + .5, target.getZ() + .5)) < .16,
                    "Rotated transfer lost anchor: delta=" + anchor(child).sub(target.getX() + .5, target.getY() + .5, target.getZ() + .5)
                            + " bound=" + entity(child).isBound() + " joint=" + entity(child).hasJoint());
            Vector3d direction = child.logicalPose().orientation().transform(new Vector3d(0, 0, 1));
            h.assertTrue(direction.distance(new Vector3d(0, 0, -1)) < .08, "Transferred payload lost signed orientation");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void asymmetricParentAssembly(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 14);
        BlockPos stone = origin.east(20);
        h.getLevel().getChunk(stone);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, List.of(stone, origin),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), stone.getX(), stone.getY(), stone.getZ()));
        h.runAfterDelay(15, () -> {
            h.assertTrue(anchor(child).distance(anchor(parent)) < .16, "Temporary parent center of mass displaced carriage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void twoRailsOneBodyTransfer(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 15);
        BlockPos second = origin.east(2);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        h.getLevel().setBlock(second, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        BlockPos childOrigin = child.getPlot().getCenterBlock();
        h.getLevel().setBlock(childOrigin.east(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(childOrigin.east(2), CreateLinearEngineering.CARRIAGE.get().defaultBlockState(), 3);
        CarriageEntity carriage = (CarriageEntity) h.getLevel().getBlockEntity(childOrigin.east(2));
        carriage.bind(null, second);
        carriage.sable$tick(child);
        BlockPos target = origin.south(4);
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(origin, target, -1, Rotation.CLOCKWISE_90, h.getLevel());
        SubLevelAssemblyHelper.moveBlocks(h.getLevel(), transform, List.of(origin, second));
        h.runAfterDelay(40, () -> {
            h.assertTrue(anchor(child).distance(new Vector3d(target.getX() + .5, target.getY() + .5, target.getZ() + .5)) < .16,
                    "Two rails transformed their shared body twice");
            h.assertTrue(entity(child).hasJoint() && carriage.hasJoint(), "Shared body lost a joint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void descendantTransferAndPendingNbt(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 16);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel child = assemble(h, origin, Direction.SOUTH);
        BlockPos innerRail = child.getPlot().getCenterBlock().above();
        h.getLevel().setBlock(innerRail, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel descendant = assemble(h, innerRail, Direction.SOUTH);
        Vector3d before = anchor(descendant);
        BlockPos target = origin.south(4);
        SubLevelAssemblyHelper.moveBlocks(h.getLevel(), new SubLevelAssemblyHelper.AssemblyTransform(origin, target, 0, Rotation.NONE, h.getLevel()), List.of(origin));
        RailEntity rail = (RailEntity) h.getLevel().getBlockEntity(target);
        CompoundTag saved = rail.saveWithFullMetadata(h.getLevel().registryAccess());
        rail.loadWithComponents(saved, h.getLevel().registryAccess());
        h.runAfterDelay(40, () -> {
            h.assertTrue(anchor(descendant).distance(before.add(0, 0, 4)) < .16, "Descendant or pending NBT transfer lost its transform");
            h.assertTrue(entity(descendant).hasJoint(), "Descendant lost its joint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemAutomaticallyAssembles(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 17);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        autoPlace(h, origin);
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemAutomaticallyAssemblesOnSublevel(GameTestHelper h) {
        BlockPos origin = testOrigin(h, 18);
        h.getLevel().setBlock(origin, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), origin, List.of(origin),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX(), origin.getY(), origin.getZ()));
        autoPlace(h, parent.getPlot().getCenterBlock());
    }
    private static void autoPlace(GameTestHelper h, BlockPos rail) {
        var container = SubLevelContainer.getContainer(h.getLevel());
        HashSet<ServerSubLevel> before = new HashSet<>(container.getAllSubLevels());
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(0, 200, 0);
        ItemStack stack = new ItemStack(CreateLinearEngineering.CARRIAGE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(rail), Direction.UP, rail, false)));
        h.assertTrue(result.consumesAction(), "Carriage item refused rail placement");
        ServerSubLevel immediate = container.getAllSubLevels().stream().filter(s -> !before.contains(s)).findFirst().orElseThrow();
        Vector3d expected = new Vector3d(rail.getX() + .5, rail.getY() + .5, rail.getZ() + .5);
        var parent = dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(), rail);
        if (parent != null) parent.logicalPose().transformPosition(expected);
        h.assertTrue(anchor(immediate).distance(expected) < 1e-6, "Carriage did not spawn already aligned");
        h.assertTrue(h.getLevel().getBlockState(rail.above()).isAir(), "Temporary floating world block was placed");
        h.assertTrue(entity(immediate).hasJoint(), "Joint deferred to a later tick");
        h.runAfterDelay(12, () -> {
            ServerSubLevel child = container.getAllSubLevels().stream().filter(s -> !before.contains(s)).findFirst().orElseThrow();
            h.assertTrue(entity(child).isBound() && entity(child).hasJoint(), "Item placement did not automatically assemble a Sable carriage");
            h.succeed();
        });
    }

    private static ServerSubLevel assemble(GameTestHelper h, BlockPos rail, Direction facing) {
        var container = SubLevelContainer.getContainer(h.getLevel());
        HashSet<ServerSubLevel> before = new HashSet<>(container.getAllSubLevels());
        BlockPos pos = rail.relative(RailGeometry.normal(facing));
        h.getLevel().setBlock(pos, CreateLinearEngineering.CARRIAGE.get().defaultBlockState().setValue(LinearBlock.FACING, facing), 3);
        h.assertTrue(BearingAssembly.assemble(h.getLevel(), pos, null), "Assembly refused");
        return container.getAllSubLevels().stream().filter(s -> !before.contains(s)).findFirst().orElseThrow();
    }

    // Vanilla randomizes GameTest structures out to 15 million blocks. Sable 2.0.5
    // uses f32 physics, so exercise precise joints in explicitly loaded origin chunks.
    private static BlockPos testOrigin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }

    private static CarriageEntity entity(ServerSubLevel child) {
        return (CarriageEntity) child.getLevel().getBlockEntity(child.getPlot().getCenterBlock());
    }
    private static Vector3d anchor(ServerSubLevel child) {
        BlockPos p = child.getPlot().getCenterBlock();
        Vector3d result = new Vector3d(p.getX() + .5, p.getY() + .5, p.getZ() + .5);
        child.logicalPose().transformPosition(result);
        return result;
    }
}
