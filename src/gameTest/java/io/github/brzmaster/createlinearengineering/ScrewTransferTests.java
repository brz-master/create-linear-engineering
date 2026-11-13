package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class ScrewTransferTests {
    @GameTest(template = "empty", timeoutTicks = 110)
    public static void poweredScrewFollowsRotatedMovingParent(GameTestHelper h) {
        BlockPos pos = origin(h, 120);
        List<BlockPos> blocks = line(h, pos, 7);
        motor(h, pos.north(), Direction.SOUTH);
        blocks.add(pos.north());
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, blocks,
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ() - 1, pos.getX(), pos.getY(), pos.getZ() + 6));
        BlockPos localRail = parent.getPlot().getCenterBlock().south(2);
        Vector3d oldAnchor = RailContact.anchor(parent, localRail);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(oldAnchor.sub(RailContact.anchor(parent, localRail)));
        var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
        pipeline.teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), localRail);
        h.assertTrue(child != null, "Cannot place screw carriage on rotated parent");
        Vector3d[] sample = new Vector3d[1];
        Vector3d before = new Vector3d(parent.logicalPose().position());
        h.runAfterDelay(10, () -> pipeline.addLinearAndAngularVelocity(parent, new Vector3d(1, 0, 0), new Vector3d()));
        h.runAfterDelay(15, () -> {
            h.assertTrue(Math.abs(((RailEntity) h.getLevel().getBlockEntity(localRail)).getSpeed() - 128) < .01,
                    "Native motor failed to power assembled screw");
            sample[0] = localAnchor(parent, child);
        });
        h.runAfterDelay(65, () -> {
            Vector3d displacement = localAnchor(parent, child).sub(sample[0]);
            h.assertTrue(Math.abs(displacement.z - 4d / 3) < .18 && Math.abs(displacement.x) < .04 && Math.abs(displacement.y) < .04,
                    "Screw failed in moving local frame: " + displacement);
            h.assertTrue(parent.logicalPose().position().distance(before) > .5, "Parent fixture did not move");
            h.assertTrue(carriage(child).hasJoint(), "Rotated moving parent lost carriage constraint");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void poweredRailMergeRetargetsDescendantsAndKinetics(GameTestHelper h) {
        BlockPos pos = origin(h, 121);
        List<BlockPos> blocks = line(h, pos, 7);
        motor(h, pos.north(), Direction.SOUTH);
        blocks.add(pos.north());
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos.south(2));
        BlockPos innerRail = child.getPlot().getCenterBlock().above();
        h.getLevel().setBlock(innerRail, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
        ServerSubLevel descendant = CarriagePlacement.place(h.getLevel(), innerRail);
        h.assertTrue(descendant != null, "Nested carriage fixture failed");
        h.runAfterDelay(15, () -> {
            for (int i = 0; i < 7; i++) {
                BlockPos railPos = pos.south(i);
                h.assertTrue(h.getLevel().getBlockEntity(railPos) instanceof RailEntity,
                        "Source fixture rail " + i + " missing before assembly at " + railPos
                                + ": " + h.getLevel().getBlockState(railPos));
            }
            h.assertTrue(Math.abs(((RailEntity) h.getLevel().getBlockEntity(pos)).getSpeed() - 128) < .01,
                    "Source screw was not powered before transfer");
            ServerSubLevel source = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, blocks,
                    new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ() - 1, pos.getX(), pos.getY(), pos.getZ() + 6));
            for (int i = 0; i < 7; i++) {
                BlockPos railPos = source.getPlot().getCenterBlock().south(i);
                BlockEntity rail = h.getLevel().getBlockEntity(railPos);
                h.assertTrue(rail instanceof RailEntity,
                        "Native assembly lost rail " + i + " at " + railPos
                                + ": " + h.getLevel().getBlockState(railPos)
                                + ", source=" + source.getUniqueId() + ", removed=" + source.isRemoved());
                ((RailEntity) rail).refreshBindings();
            }
            BlockPos support = pos.east();
            h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            ServerSubLevel target = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), support, List.of(support),
                    new BoundingBox3i(support.getX(), support.getY(), support.getZ(), support.getX(), support.getY(), support.getZ()));
            String result = CarriageCoupling.merge(h.getLevel(), target, source, null);
            h.assertTrue(result.equals("couple_success"), "Powered rail merge failed: " + result);
            h.assertTrue(source.isRemoved(), "Merged rail source survived");
            BlockPos movedRail = target.getPlot().getCenterBlock().west();
            Vector3d[] sample = new Vector3d[1];
            h.runAfterDelay(15, () -> {
                h.assertTrue(Math.abs(((RailEntity) h.getLevel().getBlockEntity(movedRail.south(2))).getSpeed() - 128) < .01,
                        "Moved screw did not reconnect its native kinetic network");
                h.assertTrue(carriage(child).sable$getConnectionDependencies().iterator().next() == target,
                        "Carriage still references removed rail body");
                h.assertTrue(carriage(descendant).sable$getConnectionDependencies().iterator().next() == child,
                        "Nested carriage lost its parent during transfer");
                sample[0] = localAnchor(target, child);
            });
            h.runAfterDelay(55, () -> {
                h.assertTrue(localAnchor(target, child).z > sample[0].z + .85, "Transferred screw no longer drives payload");
                h.assertTrue(carriage(child).hasJoint() && carriage(descendant).hasJoint(), "Transfer lost descendant constraints");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reloadedScrewCarriageHoldsItsCurrentPosition(GameTestHelper h) {
        BlockPos pos = origin(h, 122);
        for (int i = 0; i < 7; i++) h.getLevel().setBlock(pos.above(i), CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState()
                .setValue(LinearBlock.FACING, Direction.UP).setValue(LinearBlock.NORMAL, Direction.NORTH), 3);
        CreativeMotorBlockEntity motor = motor(h, pos.below(), Direction.UP);
        ServerSubLevel child = CarriagePlacement.place(h.getLevel(), pos.above(2));
        h.runAfterDelay(40, () -> motor.generatedSpeed.setValue(0));
        Vector3d[] sample = new Vector3d[1];
        h.runAfterDelay(55, () -> {
            BlockPos carriagePos = child.getPlot().getCenterBlock();
            CarriageEntity old = carriage(child);
            var state = old.getBlockState();
            var saved = old.saveWithFullMetadata(h.getLevel().registryAccess());
            sample[0] = RailContact.anchor(child, carriagePos);
            old.beforeTransfer();
            h.getLevel().removeBlockEntity(carriagePos);
            BlockEntity restored = BlockEntity.loadStatic(carriagePos, state, saved, h.getLevel().registryAccess());
            h.assertTrue(restored instanceof CarriageEntity, "Could not reload screw block entity");
            h.getLevel().setBlockEntity(restored);
            // Sable's plot load rebuilds actors via onBlockChange, not setBlockEntity.
            child.getPlot().onBlockChange(carriagePos, state);
        });
        h.runAfterDelay(125, () -> {
            h.assertTrue(carriage(child).isBound() && carriage(child).hasJoint(), "Reload lost screw binding");
            h.assertTrue(RailContact.anchor(child, child.getPlot().getCenterBlock()).distance(sample[0]) < .03,
                    "Reload reset servo target or allowed vertical payload to fall");
            sample[0] = RailContact.anchor(child, child.getPlot().getCenterBlock());
            motor.generatedSpeed.setValue(128);
        });
        h.runAfterDelay(170, () -> {
            double travel = RailContact.anchor(child, child.getPlot().getCenterBlock()).y - sample[0].y;
            h.assertTrue(carriage(child).hasJoint() && Math.abs(travel - 1.2) < .15,
                    "Reloaded sleeping screw did not resume at native RPM: " + travel);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void twoPoweredCarriagesMoveOneRigidBody(GameTestHelper h) {
        BlockPos pos = origin(h, 123);
        line(h, pos, 7); line(h, pos.east(2), 7);
        motor(h, pos.north(), Direction.SOUTH); motor(h, pos.east(2).north(), Direction.SOUTH);
        ServerSubLevel first = CarriagePlacement.place(h.getLevel(), pos.south(2));
        ServerSubLevel second = CarriagePlacement.place(h.getLevel(), pos.east(2).south(2));
        BlockPos center = first.getPlot().getCenterBlock();
        h.getLevel().setBlock(center.east(), Blocks.STONE.defaultBlockState(), 3);
        String result = CarriageCoupling.merge(h.getLevel(), first, second, null);
        h.assertTrue(result.equals("couple_success"), "Could not couple screw carriages: " + result);
        Vector3d[] sample = new Vector3d[1];
        h.runAfterDelay(15, () -> sample[0] = RailContact.anchor(first, center));
        h.runAfterDelay(65, () -> {
            h.assertTrue(carriage(first).hasJoint() && ((CarriageEntity) h.getLevel().getBlockEntity(center.east(2))).hasJoint(),
                    "Coupled screw body lost a motor joint");
            double travel = RailContact.anchor(first, center).z - sample[0].z;
            h.assertTrue(Math.abs(travel - 4d / 3) < .18, "Coupled motors disagreed or stalled: " + travel);
            h.succeed();
        });
    }

    private static List<BlockPos> line(GameTestHelper h, BlockPos pos, int length) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            BlockPos rail = pos.south(i);
            h.getLevel().setBlock(rail, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
            blocks.add(rail);
        }
        return blocks;
    }

    private static CreativeMotorBlockEntity motor(GameTestHelper h, BlockPos pos, Direction direction) {
        h.getLevel().setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, direction), 3);
        var motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(pos);
        motor.generatedSpeed.setValue(128);
        return motor;
    }

    private static Vector3d localAnchor(ServerSubLevel parent, ServerSubLevel child) {
        return parent.logicalPose().transformPositionInverse(RailContact.anchor(child, child.getPlot().getCenterBlock()));
    }

    private static CarriageEntity carriage(ServerSubLevel body) {
        return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
    }

    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
