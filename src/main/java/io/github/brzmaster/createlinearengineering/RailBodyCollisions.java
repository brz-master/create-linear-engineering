package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.function.Consumer;

/** Sweeps a guided body using vanilla voxel queries, including attached payload.
 * The joint locks rotation, so its two local frames differ only by quarter turns.
 */
final class RailBodyCollisions {
    private RailBodyCollisions() {}

    static double collide(ServerSubLevel child, BlockPos carriage, BlockState frame,
                          Vector3d reference, Direction.Axis axis, double distance) {
        var level = child.getLevel();
        Quaterniond rotation = RailGeometry.frame(frame).mul(
                RailGeometry.frame(level.getBlockState(carriage)).invert());
        Matrix4d toParent = new Matrix4d().translation(reference).rotate(rotation)
                .translate(-carriage.getX() - .5, -carriage.getY() - .5, -carriage.getZ() - .5);
        Matrix4d toChild = new Matrix4d(toParent).invert();
        AABB body = child.getPlot().getBoundingBox().toAABB();
        Vector3d delta = new Vector3d().setComponent(axis.ordinal(), distance);
        AABB sweep = transform(body, toParent).expandTowards(delta.x, delta.y, delta.z);
        Vector3d localDelta = toChild.transformDirection(delta, new Vector3d());
        Direction.Axis localAxis = level.getBlockState(carriage).getValue(LinearBlock.FACING).getAxis();
        double localDistance = localAxis.choose(localDelta.x, localDelta.y, localDelta.z);
        double[] allowed = {-localDistance};
        // Query obstacles first, then only the part of the payload each obstacle
        // could meet. No per-body geometry cache or scan of every payload block.
        forEachObstacle(level, sweep, obstacle -> {
            for (AABB part : obstacle.toAabbs()) {
                AABB box = transform(part, toChild);
                AABB query = box.expandTowards(-localDelta.x, -localDelta.y, -localDelta.z);
                if (!query.intersects(body)) continue;
                query = query.intersect(body);
                allowed[0] = Shapes.collide(localAxis, box, level.getBlockCollisions(null, query), allowed[0]);
            }
        });
        return distance * (-allowed[0] / localDistance);
    }

    /** Skip unloaded chunks and empty sections before vanilla's per-block iterator.
     * Otherwise a large hollow door would scan its entire empty bounding volume.
     */
    private static void forEachObstacle(ServerLevel level, AABB sweep, Consumer<VoxelShape> visitor) {
        for (int x = Mth.floor(sweep.minX - 1) >> 4; x <= Mth.floor(sweep.maxX + 1) >> 4; x++) {
            for (int z = Mth.floor(sweep.minZ - 1) >> 4; z <= Mth.floor(sweep.maxZ + 1) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                var sections = chunk.getSections();
                int first = Math.max(0, chunk.getSectionIndex(Mth.floor(sweep.minY - 1)));
                int last = Math.min(sections.length - 1, chunk.getSectionIndex(Mth.floor(sweep.maxY + 1)));
                for (int i = first; i <= last; i++) {
                    if (sections[i].hasOnlyAir()) continue;
                    int y = chunk.getSectionYFromSectionIndex(i) << 4;
                    // Include protruding vanilla shapes (e.g. fences) at a section edge.
                    AABB section = new AABB(x * 16, y, z * 16, x * 16 + 16, y + 16, z * 16 + 16).inflate(1);
                    if (!sweep.intersects(section)) continue;
                    for (var obstacle : level.getBlockCollisions(null, sweep.intersect(section))) visitor.accept(obstacle);
                }
            }
        }
    }

    private static AABB transform(AABB box, Matrix4d matrix) {
        Vector3d min = new Vector3d(), max = new Vector3d();
        matrix.transformAab(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, min, max);
        return new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
    }
}
