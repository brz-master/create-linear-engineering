package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class PlacementAndExitTests {
    @GameTest(template = "empty")
    public static void allMountingFramesAndContacts(GameTestHelper h) {
        for (boolean screw : new boolean[]{false, true})
        for (Direction facing : Direction.values()) for (Direction normal : Direction.values()) {
            if (normal.getAxis() == facing.getAxis()) continue;
            BlockState state = (screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().defaultBlockState()
                    .setValue(LinearBlock.FACING, facing).setValue(LinearBlock.NORMAL, normal);
            var frame = RailGeometry.frame(state);
            h.assertTrue(frame.transform(new Vector3d(0, 0, 1)).distance(vector(facing)) < 1e-9, "Wrong rail axis");
            h.assertTrue(frame.transform(new Vector3d(0, 1, 0)).distance(vector(normal)) < 1e-9, "Wrong mounting normal");
            var guide = RailGeometry.shape(state, false);
            var sleeve = RailGeometry.shape(state, true);
            var bounds = sleeve.bounds();
            h.assertTrue(Math.abs(bounds.min(facing.getAxis())) < 1e-9
                    && Math.abs(bounds.max(facing.getAxis()) - 1) < 1e-9,
                    "Carriage does not span its full block along the rail: " + state);
            var physical = CarriagePlacement.state(state).getCollisionShape(h.getLevel(), BlockPos.ZERO);
            var physicalBounds = physical.bounds();
            h.assertTrue(Math.abs(physicalBounds.min(facing.getAxis())) < 1e-9
                    && Math.abs(physicalBounds.max(facing.getAxis()) - 1) < 1e-9,
                    "Running clearance shortened the physical carriage: " + state);
            // Native f32 settling previously pushed a flush face into a rail seam.
            for (int side : new int[]{-1, 1}) {
                Vector3d settling = frame.transform(new Vector3d(side * 7e-6, -7e-6, 0));
                h.assertTrue(!Shapes.joinIsNotEmpty(guide, physical.move(settling.x, settling.y, settling.z), BooleanOp.AND),
                        "Microscopic sideways/normal settling catches the rail: " + state + ", side=" + side);
            }
            h.assertTrue(!Shapes.joinIsNotEmpty(guide, sleeve, BooleanOp.AND), "Rail and carriage penetrate " + state);
            boolean contact = guide.toAabbs().stream().anyMatch(a -> sleeve.toAabbs().stream().anyMatch(b -> a.inflate(1e-5).intersects(b)));
            h.assertTrue(contact, "Floating carriage " + state);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullLengthSleeveFindsRailUntilItsTrailingFaceLeaves(GameTestHelper h) {
        BlockPos pos = origin(h, 220);
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.UP), 3);
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), pos);
        BlockPos carriage = body.getPlot().getCenterBlock();
        body.logicalPose().position().add(0, 0, .95);
        h.assertTrue(RailContact.find(body, carriage, entity(body).getBlockState()) != null,
                "Full-length sleeve lost rail contact while its trailing face still overlaps");
        body.logicalPose().position().add(0, 0, .10);
        h.assertTrue(RailContact.find(body, carriage, entity(body).getBlockState()) == null,
                "Full-length sleeve still attaches after its trailing face has left the rail");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void placementUsesClickedFace(GameTestHelper h) {
        BlockPos support = origin(h, 20);
        h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        var player = player(h);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CreateLinearEngineering.RAIL.get()));
        for (Direction face : Direction.values()) {
            var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(support, face)));
            var placed = CreateLinearEngineering.RAIL.get().getStateForPlacement(context);
            h.assertTrue(placed != null && RailGeometry.normal(placed) == face, "Mounting face ignored " + face);
            h.assertTrue(placed.getValue(LinearBlock.FACING).getAxis() != face.getAxis(), "Rail axis points into support");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void createHelperPreservesCeilingMount(GameTestHelper h) {
        BlockPos pos = origin(h, 21);
        BlockState rail = rail(Direction.EAST, Direction.DOWN);
        h.getLevel().setBlock(pos, rail, 3);
        h.getLevel().setBlock(pos.east(), rail, 3);
        var helper = PlacementHelpers.getHelpersView().stream().filter(p -> p instanceof RailPlacementHelper).findFirst().orElseThrow();
        var offset = helper.getOffset(player(h), h.getLevel(), rail, pos, hit(pos, Direction.EAST));
        h.assertTrue(offset.isSuccessful() && offset.getBlockPos().equals(pos.east(2)), "Create extension target wrong");
        var ghost = offset.getTransform().apply(CreateLinearEngineering.RAIL.get().defaultBlockState());
        h.assertTrue(ghost == rail, "Create ghost lost orientation");
        h.getLevel().setBlock(pos.east(), rail(Direction.EAST, Direction.UP), 3);
        var mixed = helper.getOffset(player(h), h.getLevel(), rail, pos, hit(pos, Direction.EAST));
        h.assertTrue(mixed.isSuccessful() && mixed.getBlockPos().equals(pos.west()), "Helper crossed incompatible mounting");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rotatingParentGivesCorrectVelocity(GameTestHelper h) {
        BlockPos pos = origin(h, 26);
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.UP), 3);
        ServerSubLevel parent = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, java.util.List.of(pos),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
        pipeline.addLinearAndAngularVelocity(parent, new Vector3d(1, 2, 3), new Vector3d(4, 0, 0));
        BlockPos parentRail = parent.getPlot().getCenterBlock();
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), parentRail);
        Vector3d parentPoint = parent.logicalPose().transformPositionInverse(new Vector3d(child.logicalPose().position()));
        Vector3d expected = dev.ryanhcode.sable.Sable.HELPER.getVelocity(h.getLevel(), parentPoint, new Vector3d());
        Vector3d actual = pipeline.getLinearVelocity(child, new Vector3d());
        h.assertTrue(expected.distance(actual) < 1e-5, "Child COM velocity incorrect: expected=" + expected + " actual=" + actual);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void adjacentCarriagesOnRotatedParent(GameTestHelper h) {
        BlockPos pos = origin(h, 28);
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.UP), 3);
        h.getLevel().setBlock(pos.south(), rail(Direction.SOUTH, Direction.UP), 3);
        ServerSubLevel parent = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos,
                java.util.List.of(pos, pos.south()), new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                        pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ() + 1));
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        BlockPos first = parent.getPlot().getCenterBlock();
        ServerSubLevel firstChild = CarriagePlacement.place(h.getLevel(), first);
        h.runAfterDelay(5, () -> {
            h.assertTrue(!CarriagePlacement.canPlace(h.getLevel(), first), "Actual overlap was allowed");
            h.assertTrue(CarriagePlacement.canPlace(h.getLevel(), first.south()),
                    "Rotated AABB falsely blocked adjacent carriage");
            // Contact tolerance admits numerical settling, never a visible overlap.
            var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
            Vector3d previous = new Vector3d(firstChild.logicalPose().position());
            firstChild.logicalPose().position().add(parent.logicalPose().orientation().transform(new Vector3d(0, 0, .01)));
            pipeline.teleport(firstChild, firstChild.logicalPose().position(), firstChild.logicalPose().orientation());
            firstChild.updateBoundingBox();
            h.assertTrue(!CarriagePlacement.canPlace(h.getLevel(), first.south()), "Contact tolerance admitted a .01-block overlap");
            firstChild.logicalPose().position().set(previous);
            pipeline.teleport(firstChild, firstChild.logicalPose().position(), firstChild.logicalPose().orientation());
            firstChild.updateBoundingBox();
            CarriagePlacement.place(h.getLevel(), first.south());
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void placementCancellationLeavesNoBody(GameTestHelper h) {
        BlockPos pos = origin(h, 27);
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.UP), 3);
        var player = player(h);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(CreateLinearEngineering.CARRIAGE.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var container = SubLevelContainer.getContainer(h.getLevel());
        int before = container.getAllSubLevels().size();
        int[] events = {0};
        java.util.function.Consumer<net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() == player && event.getPlacedBlock().is(CreateLinearEngineering.CARRIAGE.get())) {
                events[0]++;
                h.assertTrue(event.getPos().equals(pos), "Placement event uses plot storage coordinates");
                event.setCanceled(true);
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try {
            var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, Direction.UP)));
            h.assertTrue(!result.consumesAction() && events[0] == 1, "Installation ignored canceled placement event");
            h.assertTrue(container.getAllSubLevels().size() == before && stack.getCount() == 3, "Canceled placement leaked body/item");
            h.assertTrue(!((RailEntity) h.getLevel().getBlockEntity(pos)).hasCarriageHere(), "Canceled placement left binding");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void ceilingCarriageStaysAttached(GameTestHelper h) {
        BlockPos pos = origin(h, 22);
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.DOWN), 3);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos);
        Vector3d start = anchor(child);
        h.runAfterDelay(70, () -> {
            h.assertTrue(anchor(child).distance(start) < .12 && entity(child).hasJoint(), "Ceiling carriage fell/drifted");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void solidBlockStopsVerticalCarriage(GameTestHelper h) {
        BlockPos pos = origin(h, 23);
        for (int i = -1; i <= 1; i++) h.getLevel().setBlock(pos.above(i), rail(Direction.UP, Direction.NORTH), 3);
        h.getLevel().setBlock(pos.below(2), Blocks.STONE.defaultBlockState(), 3);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos);
        h.getLevel().setBlock(entity(child).getBlockPos(), entity(child).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Vector3d start = anchor(child);
        h.runAfterDelay(90, () -> {
            double travel = start.y - anchor(child).y;
            h.assertTrue(travel > .9 && travel < 1.3, "Real vertical stopper failed: " + travel);
            h.assertTrue(entity(child).hasJoint(), "Carriage detached before blocker contact");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void solidBlockStopsHorizontalCarriage(GameTestHelper h) {
        BlockPos pos = origin(h, 24);
        for (int i = 0; i <= 2; i++) h.getLevel().setBlock(pos.south(i), rail(Direction.SOUTH, Direction.UP), 3);
        h.getLevel().setBlock(pos.south(3), Blocks.STONE.defaultBlockState(), 3);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos);
        h.getLevel().setBlock(entity(child).getBlockPos(), entity(child).getBlockState()
                .setValue(LinearBlock.GREASE, Grease.LOW), 3);
        Vector3d start = anchor(child);
        double[] flight = {0, 0, 0};
        boolean[] observing = {true};
        dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!observing[0] || physics.getLevel() != h.getLevel() || child.isRemoved()) return;
            flight[0] = Math.max(flight[0], anchor(child).z - start.z);
            flight[2] = physics.getPhysicsHandle(child).getLinearVelocity(new Vector3d()).z;
            flight[1] = Math.max(flight[1], flight[2]);
        });
        h.runAfterDelay(5, () -> SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .addLinearAndAngularVelocity(child, new Vector3d(0, 0, 4), new Vector3d()));
        h.runAfterDelay(85, () -> {
            observing[0] = false;
            double travel = anchor(child).z - start.z;
            h.assertTrue(travel > 1.8 && travel < 2.3 && entity(child).hasJoint(), "Horizontal blocker failed: " + travel
                    + "; peakTravel=" + flight[0] + ", peakSpeed=" + flight[1] + ", finalSpeed=" + flight[2]
                    + ", state=" + entity(child).getBlockState() + ", bound=" + entity(child).isBound());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemRejectsFreeAndDuplicatePlacement(GameTestHelper h) {
        BlockPos pos = origin(h, 25);
        var player = player(h);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(CreateLinearEngineering.CARRIAGE.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
        h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(!stack.useOn(context).consumesAction() && stack.getCount() == 3, "Free carriage was allowed");
        h.getLevel().setBlock(pos, rail(Direction.SOUTH, Direction.UP), 3);
        h.assertTrue(stack.useOn(context).consumesAction() && stack.getCount() == 2, "Rail install failed");
        h.assertTrue(!stack.useOn(context).consumesAction() && stack.getCount() == 2, "Duplicate same-tick carriage allowed");
        h.runAfterDelay(10, () -> {
            h.assertTrue(!stack.useOn(context).consumesAction() && stack.getCount() == 2, "Duplicate delayed carriage allowed");
            h.succeed();
        });
    }

    private static net.neoforged.neoforge.common.util.FakePlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.CREATIVE);
        player.setPos(0, 200, 0);
        return player;
    }
    private static BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).relative(face, .5), face, pos, false);
    }
    private static BlockState rail(Direction facing, Direction normal) {
        return CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, facing).setValue(LinearBlock.NORMAL, normal);
    }
    private static Vector3d vector(Direction d) { return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ()); }
    private static CarriageEntity entity(ServerSubLevel child) {
        return (CarriageEntity) child.getLevel().getBlockEntity(child.getPlot().getCenterBlock());
    }
    private static Vector3d anchor(ServerSubLevel child) {
        BlockPos pos = child.getPlot().getCenterBlock();
        return child.logicalPose().transformPosition(new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5));
    }
    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
