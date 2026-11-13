package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class CouplingAndReentryTests {
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void fallingCarriageReentersLowerRail(GameTestHelper h) { falling(h, false, 29); }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void reversedCarriageReentersLowerRail(GameTestHelper h) { falling(h, true, 30); }

    private static void falling(GameTestHelper h, boolean reversed, int index) {
        BlockPos upper = origin(h, index);
        BlockPos lower = upper.below(3);
        rail(h, upper, Direction.UP);
        rail(h, lower, Direction.UP);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), upper);
        h.getLevel().setBlock(upper, Blocks.AIR.defaultBlockState(), 3);
        h.runAfterDelay(3, () -> {
            if (reversed) {
                var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
                child.logicalPose().orientation().rotationY(Math.PI);
                pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
                child.updateLastPose();
            }
        });
        h.runAfterDelay(90, () -> {
            var entity = carriage(child, child.getPlot().getCenterBlock());
            h.assertTrue(entity.isBound() && entity.hasJoint(), "Fallen carriage did not reattach");
            h.assertTrue(RailContact.anchor(child, entity.getBlockPos()).distance(RailContact.anchor(null, lower)) < .12, "Reentry not aligned");
            h.assertTrue(((RailEntity) h.getLevel().getBlockEntity(lower)).sable$getConnectionDependencies().iterator().next() == child, "Missing new rail link");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void wrongAngleDoesNotMagnetize(GameTestHelper h) {
        BlockPos pos = origin(h, 31);
        rail(h, pos, Direction.UP);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos);
        child.logicalPose().orientation().rotationY(Math.PI / 2);
        h.assertTrue(RailContact.find(child, child.getPlot().getCenterBlock(), carriage(child, child.getPlot().getCenterBlock()).getBlockState()) == null,
                "Perpendicular carriage magnetized to rail");
        child.logicalPose().orientation().identity();
        child.logicalPose().position().add(1, 0, 0);
        h.assertTrue(RailContact.find(child, child.getPlot().getCenterBlock(), carriage(child, child.getPlot().getCenterBlock()).getBlockState()) == null,
                "Carriage magnetized across a parallel lane");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void twoGuidesOneRigidDoor(GameTestHelper h) {
        BlockPos bottom = origin(h, 32);
        BlockPos top = bottom.above(3);
        for (int i = -3; i <= 3; i++) { rail(h, bottom.south(i), Direction.UP); rail(h, top.south(i), Direction.DOWN); }
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), bottom);
        ServerSubLevel b = CarriagePlacement.place(h.getLevel(), top);
        BlockPos center = a.getPlot().getCenterBlock();
        h.getLevel().setBlock(center.above(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(b.getPlot().getCenterBlock().below(), Blocks.STONE.defaultBlockState(), 3);
        String result = CarriageCoupling.merge(h.getLevel(), a, b, null);
        h.assertTrue(result.equals("couple_success"), "Door merge refused: " + result);
        h.assertTrue(b.isRemoved(), "Second body was not removed");
        h.assertTrue(h.getLevel().getBlockState(center.above(2)).is(Blocks.STONE), "Payload lost");
        h.assertTrue(h.getLevel().getBlockState(center.above(3)).is(CreateLinearEngineering.CARRIAGE.get()), "Upper carriage lost");
        for (BlockPos pos : java.util.List.of(center, center.above(3)))
            h.getLevel().setBlock(pos, h.getLevel().getBlockState(pos).setValue(LinearBlock.GREASE, Grease.LOW), 3);
        h.runAfterDelay(8, () -> SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .addLinearAndAngularVelocity(a, new Vector3d(0, 0, 2), new Vector3d()));
        h.runAfterDelay(25, () -> {
            h.assertTrue(carriage(a, center).hasJoint() && carriage(a, center.above(3)).hasJoint(), "Merged door lost a rail joint");
            double distance = RailContact.anchor(a, center).distance(RailContact.anchor(a, center.above(3)));
            h.assertTrue(Math.abs(distance - 3) < 1e-8, "Door is not rigid");
            h.assertTrue(RailContact.anchor(a, center).z > bottom.getZ() + .8, "Joined door cannot slide");
            h.assertTrue(((RailEntity) h.getLevel().getBlockEntity(top)).sable$getConnectionDependencies().iterator().next() == a, "Upper rail still links deleted body");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void mergeOverlapLeavesBothBodies(GameTestHelper h) {
        BlockPos bottom = origin(h, 33), top = bottom.above(2);
        rail(h, bottom, Direction.UP); rail(h, top, Direction.DOWN);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), bottom), b = CarriagePlacement.place(h.getLevel(), top);
        BlockPos one = a.getPlot().getCenterBlock().above(), two = b.getPlot().getCenterBlock().below();
        h.getLevel().setBlock(one, Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(two, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        h.assertTrue(CarriageCoupling.merge(h.getLevel(), a, b, null).equals("couple_overlap"), "Overlapping blocks accepted");
        h.assertTrue(!a.isRemoved() && !b.isRemoved() && h.getLevel().getBlockState(one).is(Blocks.STONE)
                && h.getLevel().getBlockState(two).is(Blocks.GOLD_BLOCK), "Rejected merge changed blocks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mergePreservesInventoryAndGlue(GameTestHelper h) {
        BlockPos bottom = origin(h, 34), top = bottom.above(3);
        rail(h, bottom, Direction.UP); rail(h, top, Direction.DOWN);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), bottom), b = CarriagePlacement.place(h.getLevel(), top);
        BlockPos chest = b.getPlot().getCenterBlock().below();
        h.getLevel().setBlock(a.getPlot().getCenterBlock().above(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(chest))
                .setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 37));
        h.getLevel().addFreshEntity(new com.simibubi.create.content.contraptions.glue.SuperGlueEntity(h.getLevel(),
                new net.minecraft.world.phys.AABB(chest).expandTowards(0, 1, 0)));
        h.assertTrue(CarriageCoupling.merge(h.getLevel(), a, b, null).equals("couple_success"), "Inventory merge failed");
        BlockPos movedChest = a.getPlot().getCenterBlock().above(2);
        var moved = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(movedChest);
        h.assertTrue(moved.getItem(0).getCount() == 37 && moved.getItem(0).is(net.minecraft.world.item.Items.DIAMOND), "Inventory changed");
        h.assertTrue(com.simibubi.create.content.contraptions.glue.SuperGlueEntity.isGlued(h.getLevel(), movedChest, Direction.UP, null), "Source glue lost");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mergeOutsideBuildHeightRefused(GameTestHelper h) {
        BlockPos low = origin(h, 35).atY(-60), high = low.atY(300);
        rail(h, low, Direction.UP); rail(h, high, Direction.UP);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        h.assertTrue(CarriageCoupling.merge(h.getLevel(), a, b, null).equals("couple_overlap"), "Out-of-height merge allowed");
        h.assertTrue(!a.isRemoved() && !b.isRemoved(), "Out-of-height merge removed a body");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeMoveFailureRollsBack(GameTestHelper h) {
        BlockPos low = origin(h, 36), high = low.above(4);
        rail(h, low, Direction.UP); rail(h, high, Direction.UP);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        BlockPos start = b.getPlot().getCenterBlock(), end = a.getPlot().getCenterBlock().above(4);
        h.getLevel().setBlock(start.above(), Blocks.STONE.defaultBlockState(), 3);
        BlockPos chest = start.above(2);
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(chest))
                .setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 37));
        var transform = new dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform(start, end, 0,
                net.minecraft.world.level.block.Rotation.NONE, h.getLevel()) {
            int stoneCalls;
            @Override public net.minecraft.world.level.block.state.BlockState apply(net.minecraft.world.level.block.state.BlockState state) {
                if (state.is(Blocks.STONE) && ++stoneCalls == 2) throw new IllegalStateException("Intentional GameTest transfer failure");
                return super.apply(state);
            }
        };
        h.assertTrue(!BodyTransfer.move(h.getLevel(), b, a, transform, java.util.List.of(start, chest, start.above()), b.getPlot().getBoundingBox()),
                "Native swallowed failure was reported as success");
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(end).expandTowards(0, 3, 0).inflate(2)).isEmpty(),
                "Rollback duplicated inventory as dropped items");
        h.runAfterDelay(5, () -> {
            h.assertTrue(!b.isRemoved() && h.getLevel().getBlockState(start).is(CreateLinearEngineering.CARRIAGE.get())
                    && h.getLevel().getBlockState(start.above()).is(Blocks.STONE), "Rollback did not keep source alive");
            h.assertTrue(h.getLevel().getBlockState(end).isAir() && h.getLevel().getBlockState(end.above()).isAir(), "Rollback left duplicates");
            h.assertTrue(h.getLevel().getBlockState(end.above(2)).isAir(), "Rollback left destination chest");
            var restoredChest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(chest);
            h.assertTrue(restoredChest != null && restoredChest.getItem(0).is(net.minecraft.world.item.Items.DIAMOND)
                    && restoredChest.getItem(0).getCount() == 37, "Rollback lost source inventory");
            h.assertTrue(carriage(b, start).hasJoint(), "Rollback lost original rail constraint");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void wrenchMergeUsesWorldPermissions(GameTestHelper h) {
        BlockPos low = origin(h, 37), high = low.above(3);
        rail(h, low, Direction.UP); rail(h, high, Direction.DOWN);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        h.getLevel().setBlock(a.getPlot().getCenterBlock().above(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(a.getPlot().getCenterBlock().above(2), Blocks.STONE.defaultBlockState(), 3);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var wrench = com.simibubi.create.AllItems.WRENCH.asStack();
        double size = h.getLevel().getWorldBorder().getSize();
        int[] events = {0};
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() == player && event.getPlacedBlock().is(CreateLinearEngineering.CARRIAGE.get())) {
                h.assertTrue(event.getPos().equals(high), "Merge event used plot coordinates"); events[0]++;
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.getLevel().getWorldBorder().setSize(8192);
            CarriageCoupling.click(h.getLevel(), a.getPlot().getCenterBlock(), player, wrench);
            CarriageCoupling.click(h.getLevel(), b.getPlot().getCenterBlock(), player, wrench);
            h.assertTrue(b.isRemoved() && events[0] == 1, "Wrench refused bodies inside a reduced world border");
        } finally {
            h.getLevel().getWorldBorder().setSize(size);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledMergePreservesBodies(GameTestHelper h) {
        BlockPos low = origin(h, 38), high = low.above(3);
        rail(h, low, Direction.UP); rail(h, high, Direction.DOWN);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getPos().equals(high)) event.setCanceled(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(CarriageCoupling.merge(h.getLevel(), a, b, player).equals("couple_denied"), "Merge ignored claim cancellation");
            h.assertTrue(!a.isRemoved() && !b.isRemoved() && carriage(b, b.getPlot().getCenterBlock()).isBound(), "Canceled merge changed source");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reentryOnMovingSublevel(GameTestHelper h) {
        BlockPos high = origin(h, 39), low = high.below(3);
        rail(h, high, Direction.UP); rail(h, low, Direction.UP);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), high);
        ServerSubLevel parent = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), low, java.util.List.of(low),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(low.getX(), low.getY(), low.getZ(), low.getX(), low.getY(), low.getZ()));
        h.getLevel().setBlock(high, Blocks.AIR.defaultBlockState(), 3);
        h.runAfterDelay(10, () -> {
            Vector3d target = RailContact.anchor(parent, parent.getPlot().getCenterBlock());
            child.logicalPose().orientation().set(parent.logicalPose().orientation());
            child.logicalPose().position().add(target.sub(RailContact.anchor(child, child.getPlot().getCenterBlock())));
            var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
            pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
            child.updateLastPose();
            carriage(child, child.getPlot().getCenterBlock()).sable$tick(child);
            h.assertTrue(carriage(child, child.getPlot().getCenterBlock()).hasJoint(), "Reentry on moving parent failed");
            h.assertTrue(carriage(child, child.getPlot().getCenterBlock()).sable$getConnectionDependencies().iterator().next() == parent,
                    "Reentry bound to wrong parent");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rotatedMergePreservesItemFrame(GameTestHelper h) {
        BlockPos low = origin(h, 40), high = low.above(3);
        rail(h, low, Direction.UP); rail(h, high, Direction.UP);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        BlockPos source = b.getPlot().getCenterBlock(), target = a.getPlot().getCenterBlock().above(3);
        h.getLevel().setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(target.below(2), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(source.east(), Blocks.STONE.defaultBlockState(), 3);
        var frame = new net.minecraft.world.entity.decoration.ItemFrame(h.getLevel(), source.east().south(), Direction.SOUTH);
        frame.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CLOCK));
        h.getLevel().addFreshEntity(frame);
        h.getLevel().setBlock(high, Blocks.AIR.defaultBlockState(), 3);
        carriage(b, source).sable$tick(b);
        Vector3d anchorBefore = RailContact.anchor(b, source);
        b.logicalPose().orientation().rotationY(Math.PI / 2);
        b.logicalPose().position().add(anchorBefore.sub(RailContact.anchor(b, source)));
        SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .teleport(b, b.logicalPose().position(), b.logicalPose().orientation());
        b.updateLastPose();
        String result = CarriageCoupling.merge(h.getLevel(), a, b, null);
        h.assertTrue(result.equals("couple_success"), "Rotated merge failed: " + result);
        h.assertTrue(frame.getPos().equals(target.north().east()), "Frame immediate position=" + frame.getPos() + ", target=" + target + ", source=" + source);
        h.runAfterDelay(5, () -> {
            h.assertTrue(!frame.isRemoved() && frame.getDirection() == Direction.EAST && frame.getPos().equals(target.north().east()),
                    "Hanging entity lost orientation or attachment: removed=" + frame.isRemoved() + ", direction=" + frame.getDirection()
                            + ", position=" + frame.getPos() + ", expected=" + target.north().east());
            h.assertTrue(frame.survives() && frame.getItem().is(net.minecraft.world.item.Items.CLOCK), "Frame lost support/item");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void removedParentAllowsReentry(GameTestHelper h) {
        BlockPos high = origin(h, 41), low = high.below(3);
        rail(h, high, Direction.UP); rail(h, low, Direction.UP);
        ServerSubLevel parent = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), high, java.util.List.of(high),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(high.getX(), high.getY(), high.getZ(), high.getX(), high.getY(), high.getZ()));
        BlockPos railPos = parent.getPlot().getCenterBlock();
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), railPos);
        h.getLevel().setBlock(railPos, Blocks.AIR.defaultBlockState(), 3);
        SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(parent, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        h.runAfterDelay(90, () -> {
            var carriage = carriage(child, child.getPlot().getCenterBlock());
            h.assertTrue(carriage.hasJoint() && RailContact.anchor(child, carriage.getBlockPos()).distance(RailContact.anchor(null, low)) < .12,
                    "Missing parent left carriage bound forever");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void separatedBodiesRequireDoorBlocks(GameTestHelper h) {
        BlockPos low = origin(h, 42), high = low.above(3);
        rail(h, low, Direction.UP); rail(h, high, Direction.DOWN);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), low), b = CarriagePlacement.place(h.getLevel(), high);
        h.assertTrue(CarriageCoupling.merge(h.getLevel(), a, b, null).equals("couple_disconnected"), "Disconnected islands accepted");
        h.assertTrue(!a.isRemoved() && !b.isRemoved(), "Rejected disconnected merge changed bodies");
        h.succeed();
    }

    private static void rail(GameTestHelper h, BlockPos pos, Direction normal) {
        h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.NORMAL, normal), 3);
    }
    private static CarriageEntity carriage(ServerSubLevel child, BlockPos pos) { return (CarriageEntity) child.getLevel().getBlockEntity(pos); }
    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
