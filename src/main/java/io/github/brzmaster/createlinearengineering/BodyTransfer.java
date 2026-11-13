package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Verify Sable's move result: its helper logs block-level failures instead of throwing them. */
final class BodyTransfer {
    private record BlockMove(BlockSnapshot source, BlockSnapshot destination, BlockState expected) {}
    private BodyTransfer() {}

    static boolean move(ServerLevel level, ServerSubLevel source, ServerSubLevel target, SubLevelAssemblyHelper.AssemblyTransform transform,
                        List<BlockPos> blocks, BoundingBox3ic bounds) {
        List<BlockMove> changes = new ArrayList<>();
        for (BlockPos pos : blocks) changes.add(new BlockMove(BlockSnapshot.create(level.dimension(), level, pos),
                BlockSnapshot.create(level.dimension(), level, transform.apply(pos)), transform.apply(level.getBlockState(pos))));
        var glue = SuperGlueEntity.collectCropped(level, bounds.toAABB());
        var hanging = level.getEntitiesOfClass(HangingEntity.class, bounds.toAABB().inflate(2));
        var hangingPositions = new java.util.HashMap<HangingEntity, BlockPos>();
        for (HangingEntity entity : hanging) hangingPositions.put(entity, entity.getPos());
        BlockPos guard = new BlockPos(bounds.maxX() + 1, bounds.minY(), bounds.minZ());
        if (!source.getPlot().contains(guard.getX(), guard.getZ())) guard = new BlockPos(bounds.minX() - 1, bounds.minY(), bounds.minZ());
        if (!source.getPlot().contains(guard.getX(), guard.getZ()) || !level.getBlockState(guard).isAir()) return false;
        ChunkPos guardChunk = new ChunkPos(guard);
        if (source.getPlot().getChunkHolder(source.getPlot().toLocal(guardChunk)) == null) source.getPlot().newEmptyChunk(guardChunk);
        // Sable irreversibly marks an empty body removed. Keep it alive throughout
        // the native transaction so BlockSnapshot rollback can actually restore it.
        // The guard stays inside its reserved plot, with no client notification/tick.
        level.setBlock(guard, Blocks.STONE.defaultBlockState(), 0);
        try {
            SubLevelAssemblyHelper.moveBlocks(level, transform, blocks);
            if (changes.stream().anyMatch(change -> !moved(level, change))) {
                restore(level, changes); return false;
            }
        } catch (RuntimeException failure) {
            restore(level, changes);
            return false;
        } finally {
            level.setBlock(guard, Blocks.AIR.defaultBlockState(), 0);
        }
        // These helpers use the captured position set, so they can run after block validation.
        SubLevelAssemblyHelper.moveOtherStuff(level, transform, blocks, bounds);
        for (HangingEntity entity : hanging) {
            // Let Sable decide which hanging entities are supported by the moved blocks.
            if (entity.getPos().equals(hangingPositions.get(entity))) continue;
            entity.setYRot(entity.rotate(transform.getRotation()));
            entity.setPos(Vec3.atCenterOf(transform.apply(hangingPositions.get(entity))));
        }
        SubLevelAssemblyHelper.moveTrackingPoints(level, bounds, target, transform);
        for (SuperGlueEntity copy : glue) {
            AABB box = copy.getBoundingBox();
            Vec3 first = transform.apply(new Vec3(box.minX, box.minY, box.minZ));
            Vec3 last = transform.apply(new Vec3(box.maxX, box.maxY, box.maxZ));
            copy.setBoundingBox(new AABB(first, last));
            copy.resetPositionToBB();
            level.addFreshEntity(copy);
        }
        return true;
    }

    private static boolean moved(ServerLevel level, BlockMove change) {
        if (!level.getBlockState(change.source().getPos()).isAir()
                || level.getBlockState(change.destination().getPos()) != change.expected()) return false;
        var entity = level.getBlockEntity(change.destination().getPos());
        CompoundTag actual = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
        boolean kinetic = entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity;
        return Objects.equals(data(change.source().getTag(), kinetic), data(actual, kinetic));
    }

    private static CompoundTag data(CompoundTag tag, boolean kinetic) {
        if (tag == null) return null;
        tag = tag.copy();
        tag.remove("x"); tag.remove("y"); tag.remove("z");
        // Create rebuilds these network coordinates after the native Sable move.
        if (kinetic) for (String key : java.util.List.of("Speed", "Source", "Network", "NeedsSpeedUpdate")) tag.remove(key);
        // Rail movement deliberately adds a deferred descendant-pose snapshot.
        if (tag.getString("id").equals("create_linear_engineering:linear_rail")) tag.remove("PendingMove");
        return tag;
    }

    private static void restore(ServerLevel level, List<BlockMove> changes) {
        for (BlockMove change : changes) {
            if (level.getBlockEntity(change.destination().getPos()) instanceof CarriageEntity carriage) carriage.beforeTransfer();
        }
        boolean restoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            for (BlockMove change : changes) {
                var entity = level.getBlockEntity(change.destination().getPos());
                if (entity instanceof net.minecraft.world.RandomizableContainer container) container.setLootTable(null);
                net.minecraft.world.Clearable.tryClear(entity);
                change.destination().restore();
            }
            for (BlockMove change : changes) {
                if (!change.source().restore()) change.source().restoreBlockEntity(level, change.source().getPos());
            }
        } finally {
            level.restoringBlockSnapshots = restoring;
        }
        for (BlockMove change : changes) {
            if (level.getBlockEntity(change.source().getPos()) instanceof CarriageEntity carriage) carriage.afterTransfer();
            if (level.getBlockEntity(change.source().getPos()) instanceof RailEntity rail) rail.refreshBindings();
        }
    }
}
