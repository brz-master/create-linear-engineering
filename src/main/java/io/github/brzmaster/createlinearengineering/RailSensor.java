package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** An attachment stored in vanilla block state; Sable supplies the spatial query and poses. */
public final class RailSensor {
    public static final double PRECISION = 1d / 32;
    static final double APPROXIMATE_REACH = RailGeometry.CONTACT_REACH - RailGeometry.CONTACT_TOLERANCE;
    public enum Mode implements StringRepresentable {
        NONE, APPROXIMATE, PRECISE;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    private RailSensor() {}

    static void tick(RailEntity rail) {
        BlockState state = rail.getBlockState();
        if (state.getValue(LinearBlock.SENSOR) == Mode.NONE) return;
        Level level = rail.getLevel();
        boolean detected = level.getGameTime() < rail.sensorPulseUntil || detects(level, rail.getBlockPos(), state);
        if (detected == state.getValue(LinearBlock.POWERED)) return;
        if (detected) rail.holdSensorUntil(level.getGameTime() + 2);
        level.setBlock(rail.getBlockPos(), state.setValue(LinearBlock.POWERED, detected), 3);
        notifyOutput(level, rail.getBlockPos(), state);
    }

    static void crossed(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RailEntity rail)) return;
        BlockState state = rail.getBlockState();
        if (state.getValue(LinearBlock.SENSOR) == Mode.NONE) return;
        // Latch a real contact for one redstone tick. Continuous occupancy is
        // handled by tick(); it does not keep extending the trailing edge.
        if (state.getValue(LinearBlock.POWERED)) return;
        // Sable runs at ServerLevel.tick HEAD, before vanilla advances gameTime.
        rail.holdSensorUntil(level.getGameTime() + 3);
        level.setBlock(pos, state.setValue(LinearBlock.POWERED, true), 3);
        notifyOutput(level, pos, state);
    }

    static boolean detects(Level level, BlockPos pos, BlockState state) {
        SubLevel parent = Sable.HELPER.getContaining(level, pos);
        Vector3d min = new Vector3d(Double.POSITIVE_INFINITY), max = new Vector3d(Double.NEGATIVE_INFINITY);
        // One local block plus the sleeve overhang. Transform corners before querying
        // the world index, so rotated and moving parents use exactly the same logic.
        for (int i = 0; i < 8; i++) {
            Vector3d corner = new Vector3d(pos.getX() + .5 + ((i & 1) == 0 ? -1 : 1),
                    pos.getY() + .5 + ((i & 2) == 0 ? -1 : 1), pos.getZ() + .5 + ((i & 4) == 0 ? -1 : 1));
            if (parent != null) parent.logicalPose().transformPosition(corner);
            min.min(corner); max.max(corner);
        }
        var bounds = new BoundingBox3d(min.x, min.y, min.z, max.x, max.y, max.z);
        for (SubLevel body : Sable.HELPER.getAllIntersecting(level, bounds)) {
            if (body == parent) continue;
            for (var actor : body.getPlot().getBlockEntityActors()) {
                if (!(actor instanceof CarriageEntity carriage) || carriage.isRemoved()
                        || LinearBlock.isScrew(carriage.getBlockState()) != LinearBlock.isScrew(state)) continue;
                Vector3d point = RailContact.anchor(body, carriage.getBlockPos());
                if (parent != null) parent.logicalPose().transformPositionInverse(point);
                Quaterniond frame = body.logicalPose().orientation().mul(RailGeometry.frame(carriage.getBlockState()), new Quaterniond());
                Vector3d offset = RailContact.alignedOffset(point, frame, parent, pos, state);
                if (offset != null && (state.getValue(LinearBlock.SENSOR) == Mode.PRECISE
                        ? Math.abs(offset.z) <= PRECISION : Math.abs(offset.z) < APPROXIMATE_REACH)) return true;
            }
        }
        return false;
    }

    static void notifyOutput(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(RailGeometry.normal(state).getOpposite()), state.getBlock());
    }

    static InteractionResult toggle(UseOnContext context) {
        if (!AttachmentPlacement.canModify(context)) return InteractionResult.FAIL;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Mode mode = state.getValue(LinearBlock.SENSOR) == Mode.APPROXIMATE ? Mode.PRECISE : Mode.APPROXIMATE;
        if (!level.isClientSide) {
            BlockState next = state.setValue(LinearBlock.SENSOR, mode);
            if (!AttachmentPlacement.applyChange(context, next.setValue(LinearBlock.POWERED, detects(level, pos, next))))
                return InteractionResult.FAIL;
            notifyOutput(level, pos, next);
            if (level.getBlockEntity(pos) instanceof RailEntity rail) rail.holdSensorUntil(0);
            // Shift-wrench still cancels any pending carriage coupling selection.
            CarriageCoupling.clear(context.getPlayer(), context.getItemInHand());
            context.getPlayer().displayClientMessage(Component.translatable("message.create_linear_engineering.sensor_" + mode.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
