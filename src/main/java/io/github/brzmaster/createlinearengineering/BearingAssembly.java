package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Set;

public final class BearingAssembly {
    public static final int MAX_BLOCKS = 2048;
    private BearingAssembly() {}

    public static boolean assemble(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!LinearBlock.isCarriage(state)) return false;
        Direction facing = state.getValue(LinearBlock.FACING);
        Direction normal = RailGeometry.normal(state);
        BlockPos railPos = pos.relative(normal.getOpposite());
        BlockState rail = level.getBlockState(railPos);
        if (!LinearBlock.isRail(rail) || !RailGeometry.compatible(rail, state)) return fail(player, "rail_required");
        if (SubLevelContainer.getContainer(level) == null) return fail(player, "physics_unavailable");
        Set<BlockPos> blocks = gather(level, pos, railPos, rail);
        if (blocks == null) return fail(player, "assembly_refused");
        // Opposite horizontal rail directions have identical running geometry.
        // Start both local frames with the support's signed direction; later Sable
        // transfers can then preserve real 180-degree rotations of the payload.
        level.setBlock(pos, state.setValue(LinearBlock.FACING, rail.getValue(LinearBlock.FACING)).setValue(LinearBlock.NORMAL, normal), 3);
        int minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        for (BlockPos block : blocks) {
            minX = Math.min(minX, block.getX()); minY = Math.min(minY, block.getY()); minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX()); maxY = Math.max(maxY, block.getY()); maxZ = Math.max(maxZ, block.getZ());
        }
        SubLevel parent = Sable.HELPER.getContaining(level, railPos);
        ServerSubLevel child = SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ));
        Vector3d shift = new Vector3d(-normal.getStepX(), -normal.getStepY(), -normal.getStepZ());
        if (parent != null) parent.logicalPose().orientation().transform(shift);
        child.logicalPose().position().add(shift);
        var container = SubLevelContainer.getContainer(level);
        container.physicsSystem().getPipeline().teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
        child.updateLastPose();
        BlockPos carriagePos = child.getPlot().getCenterBlock();
        if (!(level.getBlockEntity(carriagePos) instanceof CarriageEntity entity))
            throw new IllegalStateException("Sable did not move the carriage block entity");
        entity.bind(parent, railPos);
        if (level.getBlockEntity(railPos) instanceof RailEntity railEntity) railEntity.attach(child, carriagePos);
        entity.sable$tick(child);
        return true;
    }

    // Sable's gather helper reads chunks before invoking its predicate. This bounded BFS
    // checks loaded chunks first, then delegates glue and movement rules to Create.
    private static Set<BlockPos> gather(ServerLevel level, BlockPos origin, BlockPos rail, BlockState railState) {
        Set<BlockPos> support = supportRails(level, rail, railState);
        if (support == null) return null;
        Set<BlockPos> blocks = new HashSet<>();
        Set<SuperGlueEntity> glue = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        blocks.add(origin);
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos from = pending.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos to = from.relative(direction);
                if (blocks.contains(to) || support.contains(to)
                        || !SuperGlueEntity.isGlued(level, from, direction, glue)) continue;
                var chunk = level.getChunkSource().getChunkNow(to.getX() >> 4, to.getZ() >> 4);
                if (chunk == null || !level.isInWorldBounds(to)) return null;
                BlockState state = chunk.getBlockState(to);
                if (state.isAir()) continue;
                if (!BlockMovementChecks.isMovementAllowed(state, level, to)) return null;
                if (Math.abs(to.getX() - origin.getX()) > 64 || Math.abs(to.getY() - origin.getY()) > 64
                        || Math.abs(to.getZ() - origin.getZ()) > 64 || blocks.size() >= MAX_BLOCKS) return null;
                blocks.add(to);
                pending.add(to);
            }
        }
        return blocks;
    }

    private static Set<BlockPos> supportRails(ServerLevel level, BlockPos rail, BlockState railState) {
        Direction facing = railState.getValue(LinearBlock.FACING);
        Set<BlockPos> support = new HashSet<>();
        support.add(rail);
        for (Direction along : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos pos = rail.relative(along);
            while (true) {
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !level.isInWorldBounds(pos)) break;
                BlockState state = chunk.getBlockState(pos);
                if (!LinearBlock.isRail(state) || !RailGeometry.compatible(state, railState)) break;
                if (support.size() >= CarriageEntity.MAX_RAIL_LENGTH) return null;
                support.add(pos);
                pos = pos.relative(along);
            }
        }
        return support;
    }

    private static boolean fail(Player player, String reason) {
        if (player != null) player.displayClientMessage(Component.translatable("message.create_linear_engineering." + reason), true);
        return false;
    }
}
