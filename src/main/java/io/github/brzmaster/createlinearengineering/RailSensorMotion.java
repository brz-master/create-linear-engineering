package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.HashSet;
import java.util.Set;

/** Measures actual Sable substeps, never a predicted velocity or a game-tick teleport. */
final class RailSensorMotion {
    private record Sample(SubLevel parent, BlockPos origin, BlockState rail, Vector3d point, Quaterniond frame) {}
    private Sample start;

    void clear() { start = null; }

    void capture(ServerSubLevel child, CarriageEntity carriage, SubLevel parent, BlockPos origin, BlockState rail) {
        start = new Sample(parent, origin, rail, point(child, carriage, parent), frame(child, carriage, parent));
    }

    private static Vector3d point(SubLevel child, CarriageEntity carriage, SubLevel parent) {
        Vector3d result = RailContact.anchor(child, carriage.getBlockPos());
        if (parent != null) parent.logicalPose().transformPositionInverse(result);
        return result;
    }

    private static Quaterniond frame(SubLevel child, CarriageEntity carriage, SubLevel parent) {
        Quaterniond result = child.logicalPose().orientation().mul(RailGeometry.frame(carriage.getBlockState()), new Quaterniond());
        if (parent != null) result.premul(new Quaterniond(parent.logicalPose().orientation()).invert());
        return result;
    }

    static void afterPhysics(SubLevelPhysicsSystem physics, double timeStep) {
        var level = physics.getLevel();
        Set<BlockPos> contacts = new HashSet<>();
        for (SubLevel body : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (body.isRemoved()) continue;
            for (var actor : body.getPlot().getBlockEntityActors()) {
                if (actor instanceof CarriageEntity carriage && !carriage.isRemoved())
                    carriage.sensorMotion.collect(body, carriage, contacts);
            }
        }
        // Neighbor updates may change bodies/actors: publish only after traversal.
        for (BlockPos pos : contacts) RailSensor.crossed(level, pos);
    }

    private void collect(SubLevel child, CarriageEntity carriage, Set<BlockPos> contacts) {
        Sample sample = start;
        clear();
        if (sample == null || !carriage.isBound() || (sample.parent != null && sample.parent.isRemoved())) return;
        Vector3d end = point(child, carriage, sample.parent);
        Quaterniond endFrame = frame(child, carriage, sample.parent);
        Direction axis = sample.rail.getValue(LinearBlock.FACING);
        Vector3d a = RailContact.alignedOffset(sample.point, sample.frame, null, sample.origin, sample.rail);
        Vector3d b = RailContact.alignedOffset(end, endFrame, null, sample.origin, sample.rail);
        if (a == null || b == null || !a.isFinite() || !b.isFinite()) return;
        double low = Math.min(a.z, b.z), high = Math.max(a.z, b.z);
        // Never wrap a block offset after an abnormal backend displacement.
        if (low <= Integer.MIN_VALUE + 1d || high >= Integer.MAX_VALUE - 1d) return;
        long first = (long) Math.floor(low - RailGeometry.CONTACT_REACH) + 1;
        long last = (long) Math.ceil(high + RailGeometry.CONTACT_REACH) - 1;
        int step = a.z <= b.z ? 1 : -1;
        var level = carriage.getLevel();
        for (long i = step > 0 ? first : last; i >= first && i <= last; i += step) {
            BlockPos pos = sample.origin.relative(axis, (int) i);
            // Follow the movement from its start; never scan or load an unloaded gap.
            if (!level.hasChunkAt(pos)) {
                if ((i - a.z) * step < 0) continue; // sleeve overhang behind the starting centre
                break;
            }
            BlockState rail = level.getBlockState(pos);
            if (!LinearBlock.isRail(rail) || !RailGeometry.compatible(rail, sample.rail)) continue;
            RailSensor.Mode mode = rail.getValue(LinearBlock.SENSOR);
            if (mode == RailSensor.Mode.NONE) continue;
            boolean crossed = mode == RailSensor.Mode.PRECISE
                    ? low - i <= RailSensor.PRECISION && high - i >= -RailSensor.PRECISION
                    : low - i < RailSensor.APPROXIMATE_REACH && high - i > -RailSensor.APPROXIMATE_REACH;
            if (crossed) contacts.add(pos);
        }
    }
}
