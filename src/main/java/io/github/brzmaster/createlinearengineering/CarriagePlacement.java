package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.function.Predicate;

/** Uses Sable's native allocation; no temporary world block and no client prediction offset. */
public final class CarriagePlacement implements IPlacementHelper {
    private final boolean screw;
    public CarriagePlacement() { this(false); }
    public CarriagePlacement(boolean screw) { this.screw = screw; }
    static BlockState state(BlockState rail) {
        return (LinearBlock.isScrew(rail) ? CreateLinearEngineering.SCREW_CARRIAGE : CreateLinearEngineering.CARRIAGE).get().defaultBlockState()
                .setValue(LinearBlock.FACING, rail.getValue(LinearBlock.FACING))
                .setValue(LinearBlock.NORMAL, RailGeometry.normal(rail));
    }

    static boolean canPlace(Level level, BlockPos pos) {
        BlockState rail = level.getBlockState(pos);
        if (!LinearBlock.isRail(rail) || SubLevelContainer.getContainer(level) == null) return false;
        if (level.getBlockEntity(pos) instanceof RailEntity entity && entity.hasCarriageHere()) return false;
        var shape = RailGeometry.shape(state(rail), true).move(pos.getX(), pos.getY(), pos.getZ());
        if (!level.isUnobstructed(null, shape)) return false;
        SubLevel parent = Sable.HELPER.getContaining(level, pos);
        var sink = ((LevelExtension) level).sable$getJOMLSink();
        for (var box : shape.toAabbs()) {
            AABB inset = box.deflate(RailGeometry.CONTACT_TOLERANCE);
            var placed = oriented(inset, parent, sink);
            BoundingBox3d worldBox = new BoundingBox3d(inset);
            if (parent != null) worldBox.transform(parent.logicalPose(), worldBox);
            if (overlapsBlocks(level, placed, worldBox, null, sink)) return false;
            for (SubLevel other : Sable.HELPER.getAllIntersecting(level, worldBox)) {
                if (other == parent) continue;
                if (overlapsBlocks(level, placed, worldBox, other, sink)) return false;
            }
        }
        return true;
    }

    private static OrientedBoundingBox3d oriented(AABB box, SubLevel body, LevelReusedVectors sink) {
        var result = new OrientedBoundingBox3d(sink);
        result.getPosition().set((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        result.getDimensions().set(box.getXsize(), box.getYsize(), box.getZsize());
        if (body != null) {
            body.logicalPose().transformPosition(result.getPosition());
            result.getOrientation().set(body.logicalPose().orientation());
        }
        return result;
    }

    private static boolean overlapsBlocks(Level level, OrientedBoundingBox3d placed, BoundingBox3d worldBox,
                                          SubLevel body, LevelReusedVectors sink) {
        BoundingBox3d query = new BoundingBox3d(worldBox);
        if (body != null) query.transformInverse(body.logicalPose(), query);
        // AABBs only select candidates. Native Sable SAT tests the actual oriented boxes.
        for (var collision : level.getBlockCollisions(null, query.toMojang())) {
            for (AABB box : collision.toAabbs()) {
                if (OrientedBoundingBox3d.sat(placed, oriented(box, body, sink)).lengthSquared() > 1e-12) return true;
            }
        }
        return false;
    }

    static ServerSubLevel place(ServerLevel level, BlockPos railPos) {
        var container = SubLevelContainer.getContainer(level);
        SubLevel parent = Sable.HELPER.getContaining(level, railPos);
        Vector3d localAnchor = new Vector3d(railPos.getX() + .5, railPos.getY() + .5, railPos.getZ() + .5);
        Pose3d pose = new Pose3d();
        pose.position().set(localAnchor);
        if (parent != null) {
            parent.logicalPose().transformPosition(pose.position());
            pose.orientation().set(parent.logicalPose().orientation());
        }
        ServerSubLevel child = (ServerSubLevel) container.allocateNewSubLevel(pose);
        var plot = child.getPlot();
        plot.newEmptyChunk(plot.getCenterChunk());
        BlockPos carriagePos = plot.getCenterBlock();
        // The item already fired the placement event at the real rail. Do not let
        // ItemStack's outer hook emit a second event at Sable's storage coordinates.
        boolean capture = level.captureBlockSnapshots;
        try {
            level.captureBlockSnapshots = false;
            level.setBlock(carriagePos, state(level.getBlockState(railPos)), 3);
        } finally {
            level.captureBlockSnapshots = capture;
        }
        // Mass is computed by Sable. Align its COM without moving the requested block anchor.
        var center = child.getSelfMassTracker().getCenterOfMass();
        child.logicalPose().rotationPoint().set(center);
        Vector3d offset = new Vector3d(center).sub(carriagePos.getX() + .5, carriagePos.getY() + .5, carriagePos.getZ() + .5);
        child.logicalPose().position().set(pose.position()).add(pose.orientation().transform(offset, new Vector3d()));
        var pipeline = container.physicsSystem().getPipeline();
        pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
        if (parent instanceof ServerSubLevel serverParent) {
            pipeline.addLinearAndAngularVelocity(child, Sable.HELPER.getVelocity(level, localAnchor.add(offset), new Vector3d()),
                    pipeline.getAngularVelocity(serverParent, new Vector3d()));
        }
        child.updateLastPose();
        child.updateBoundingBox();
        CarriageEntity entity = (CarriageEntity) level.getBlockEntity(carriagePos);
        entity.bind(parent, railPos);
        ((RailEntity) level.getBlockEntity(railPos)).attach(child, carriagePos);
        entity.sable$tick(child);
        return child;
    }

    @Override public Predicate<ItemStack> getItemPredicate() { return s -> s.is((screw ? CreateLinearEngineering.SCREW_CARRIAGE : CreateLinearEngineering.CARRIAGE).get().asItem()); }
    @Override public Predicate<BlockState> getStatePredicate() { return s -> LinearBlock.isRail(s) && LinearBlock.isScrew(s) == screw; }
    @Override public PlacementOffset getOffset(Player player, Level world, BlockState rail, BlockPos pos, BlockHitResult ray) {
        return canPlace(world, pos) ? PlacementOffset.success(pos, ignored -> state(rail)) : PlacementOffset.fail();
    }
}
