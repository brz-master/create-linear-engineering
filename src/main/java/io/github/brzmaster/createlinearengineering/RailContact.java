package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Local contact search through Sable's spatial index, independent of rail length. */
final class RailContact {
    record Contact(SubLevel parent, BlockPos pos, double distance) {}
    private RailContact() {}

    static Contact find(ServerSubLevel child, BlockPos carriagePos, BlockState state) {
        Vector3d point = anchor(child, carriagePos);
        Quaterniond frame = child.logicalPose().orientation().mul(RailGeometry.frame(state), new Quaterniond());
        Contact best = findIn(child, null, point, frame, state, null);
        BoundingBox3d bounds = new BoundingBox3d(point.x - 1, point.y - 1, point.z - 1, point.x + 1, point.y + 1, point.z + 1);
        for (SubLevel parent : Sable.HELPER.getAllIntersecting(child.getLevel(), bounds)) {
            if (parent != child) best = findIn(child, parent, point, frame, state, best);
        }
        return best;
    }

    private static Contact findIn(ServerSubLevel child, SubLevel parent, Vector3d worldPoint, Quaterniond frame, BlockState state, Contact best) {
        Vector3d point = new Vector3d(worldPoint);
        if (parent != null) parent.logicalPose().transformPositionInverse(point);
        BlockPos center = BlockPos.containing(point.x, point.y, point.z);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (!child.getLevel().hasChunkAt(pos)) continue;
            BlockState rail = child.getLevel().getBlockState(pos);
            if (!LinearBlock.isRail(rail) || LinearBlock.isScrew(rail) != LinearBlock.isScrew(state)) continue;
            Vector3d offset = alignedOffset(point, frame, parent, pos, rail);
            if (offset == null || Math.abs(offset.z) >= RailGeometry.CONTACT_REACH) continue;
            double distance = offset.lengthSquared();
            if (best == null || distance < best.distance()) best = new Contact(parent, pos.immutable(), distance);
        }
        return best;
    }

    /** Shared contact frame for reattachment and installed presence sensors. */
    static Vector3d alignedOffset(Vector3d localPoint, Quaterniond worldFrame, SubLevel parent, BlockPos pos, BlockState rail) {
        Quaterniond railFrame = RailGeometry.frame(rail);
        Vector3d offset = new Vector3d(localPoint).sub(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        railFrame.transformInverse(offset);
        if (Math.abs(offset.x) > .05 || offset.y < -.03 || offset.y > .06) return null;
        if (parent != null) railFrame.premul(parent.logicalPose().orientation());
        double axis = Math.abs(worldFrame.transform(new Vector3d(0, 0, 1)).dot(railFrame.transform(new Vector3d(0, 0, 1))));
        double normal = worldFrame.transform(new Vector3d(0, 1, 0)).dot(railFrame.transform(new Vector3d(0, 1, 0)));
        return axis < .996 || normal < .996 ? null : offset;
    }

    static Vector3d anchor(SubLevel body, BlockPos pos) {
        Vector3d anchor = new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (body != null) body.logicalPose().transformPosition(anchor);
        return anchor;
    }
}
