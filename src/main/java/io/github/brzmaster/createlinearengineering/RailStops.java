package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Real voxel obstacles become native prismatic limits, not artificial rail end stops.
 * Sable 2.0.5 has no voxel shape casts, so its discrete contacts alone can tunnel.
 */
final class RailStops {
    private double lower = Double.NEGATIVE_INFINITY, upper = Double.POSITIVE_INFINITY;

    void reset() { lower = Double.NEGATIVE_INFINITY; upper = Double.POSITIVE_INFINITY; }

    double minimum(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? lower : -upper;
    }

    double maximum(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? upper : -lower;
    }

    void update(GenericConstraintHandle joint, ServerSubLevel child, BlockPos carriage,
                       SubLevel parent, BlockPos origin, BlockState rail, BlockPos support,
                       boolean reversed, double timeStep) {
        if (support == null) {
            reset();
            joint.setLimit(ConstraintJointAxis.LINEAR_Z, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            return;
        }
        ServerLevel level = child.getLevel();
        Direction direction = rail.getValue(LinearBlock.FACING);
        if (reversed) direction = direction.getOpposite();
        Direction.Axis axis = direction.getAxis();
        Vector3d localPoint = RailContact.anchor(child, carriage);
        if (parent != null) parent.logicalPose().transformPositionInverse(localPoint);
        Vector3d velocity = Sable.HELPER.getVelocity(level, child,
                new Vector3d(carriage.getX() + .5, carriage.getY() + .5, carriage.getZ() + .5), new Vector3d());
        if (parent != null) {
            velocity.sub(Sable.HELPER.getVelocity(level, parent, localPoint, new Vector3d()));
            parent.logicalPose().orientation().transformInverse(velocity);
        }
        double speed = axis.choose(velocity.x, velocity.y, velocity.z);
        double position = axis.choose(localPoint.x - origin.getX() - .5,
                localPoint.y - origin.getY() - .5, localPoint.z - origin.getZ() - .5);
        // Reuse the last permitted position only to recover small solver overlap.
        // Backing up a whole rail cell would create false stops inside hollow payloads.
        double referencePosition = Mth.clamp(position, lower, upper);
        Vector3d reference = new Vector3d(origin.getX() + .5, origin.getY() + .5, origin.getZ() + .5);
        reference.setComponent(axis.ordinal(), reference.get(axis.ordinal()) + referencePosition);
        double reach = 2 + Math.abs(speed) * timeStep + Math.abs(position - referencePosition);
        if (!Double.isFinite(reach)) return;
        reset();
        BlockState frame = rail.setValue(LinearBlock.FACING, direction);
        for (int sign : new int[]{-1, 1}) {
            double distance = sign * loadedReach(level, reference, axis, sign, reach);
            if (distance == 0) continue;
            double allowed = RailBodyCollisions.collide(child, carriage, frame, reference, axis, distance);
            // Shapes.collide already stops at the exact contact plane. An extra
            // margin would expel a sleeve that was placed flush against a wall.
            if (sign > 0 && allowed < distance) upper = referencePosition + allowed;
            if (sign < 0 && allowed > distance) lower = referencePosition + allowed;
        }
        // Degenerate/overlapping obstacles must never submit inverted native limits.
        if (lower > upper) lower = upper = (lower + upper) * .5;
        joint.setLimit(ConstraintJointAxis.LINEAR_Z, minimum(direction), maximum(direction));
    }

    /** Bound work to the continuous loaded region; never request missing chunks. */
    private static double loadedReach(ServerLevel level, Vector3d start, Direction.Axis axis, int sign, double reach) {
        if (axis == Direction.Axis.Y)
            return Math.max(0, Math.min(reach, sign > 0 ? level.getMaxBuildHeight() - start.y : start.y - level.getMinBuildHeight()));
        double coordinate = axis == Direction.Axis.X ? start.x : start.z;
        int chunk = Mth.floor(coordinate) >> 4;
        int other = Mth.floor(axis == Direction.Axis.X ? start.z : start.x) >> 4;
        double edge = sign > 0 ? (chunk + 1L) * 16 - coordinate : coordinate - chunk * 16L;
        for (; edge < reach; edge += 16) {
            chunk += sign;
            int x = axis == Direction.Axis.X ? chunk : other;
            int z = axis == Direction.Axis.Z ? chunk : other;
            if (level.getChunkSource().getChunkNow(x, z) == null) return edge;
        }
        return reach;
    }
}
