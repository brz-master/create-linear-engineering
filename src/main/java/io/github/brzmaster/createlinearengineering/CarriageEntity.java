package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

/** The persisted rail reference lives on the moving body. Only Sable owns its physics. */
public final class CarriageEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private boolean bound;
    private boolean reversed;
    private UUID parentId;
    private BlockPos railOffset = BlockPos.ZERO;
    private GenericConstraintHandle joint;
    private SubLevel cachedParent;
    private BlockState jointRailState;
    private BlockState jointCarriageState;
    private RailEntity registeredLoadRail;
    final RailSensorMotion sensorMotion = new RailSensorMotion();
    private final ScrewDrive screwDrive = new ScrewDrive();
    private final RailStops railStops = new RailStops();
    public static final int MAX_RAIL_LENGTH = 256;

    public CarriageEntity(BlockPos pos, BlockState state) { super(CreateLinearEngineering.CARRIAGE_ENTITY.get(), pos, state); }
    public boolean isBound() { return bound; }
    public boolean hasJoint() { return joint != null && joint.isValid(); }
    RailEntity loadRail() { return registeredLoadRail; }
    private void registerLoad(RailEntity rail) {
        if (registeredLoadRail == rail) return;
        if (registeredLoadRail != null) registeredLoadRail.removeLoad(this);
        registeredLoadRail = rail;
        if (rail != null) rail.addLoad(this);
    }
    void beforeTransfer() { unlink(); removeJoint(); }
    void afterTransfer() {
        if (!bound || level == null) return;
        var container = SubLevelContainer.getContainer(level);
        SubLevel parent = parentId == null ? null : container.getSubLevel(parentId);
        if (parentId != null && parent == null) return;
        BlockPos pos = parent == null ? railOffset : parent.getPlot().getCenterBlock().offset(railOffset);
        if (Sable.HELPER.getContaining(level, worldPosition) instanceof ServerSubLevel child
                && level.getBlockEntity(pos) instanceof RailEntity rail) rail.attach(child, worldPosition);
        // Sable finalizes the body pose after the move; its actor tick recreates the joint.
    }

    public void bind(SubLevel parent, BlockPos rail) {
        UUID nextId = parent == null ? null : parent.getUniqueId();
        BlockPos nextOffset = parent == null ? rail.immutable() : rail.subtract(parent.getPlot().getCenterBlock());
        if (bound && Objects.equals(parentId, nextId) && railOffset.equals(nextOffset)) return;
        boolean acquiring = !bound;
        unlink();
        removeJoint();
        bound = true;
        parentId = nextId;
        railOffset = nextOffset;
        cachedParent = parent;
        SubLevel child = Sable.HELPER.getContaining(level, worldPosition);
        Vector3d childAxis = RailGeometry.frame(getBlockState()).transform(new Vector3d(0, 0, 1));
        if (child != null) child.logicalPose().orientation().transform(childAxis);
        Vector3d railAxis = RailGeometry.frame(level.getBlockState(rail)).transform(new Vector3d(0, 0, 1));
        if (parent != null) parent.logicalPose().orientation().transform(railAxis);
        // Transfers rebind before RailEntity finishes rotating the payload. Preserve
        // the existing sign; only a genuinely new contact determines it from the pose.
        if (acquiring) reversed = childAxis.dot(railAxis) < 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override public void sable$physicsTick(ServerSubLevel child, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double timeStep) {
        sensorMotion.clear();
        if (!bound || isRemoved() || level == null) return;
        var container = SubLevelContainer.getContainer(level);
        SubLevel parent = parentId == null ? null : container.getSubLevel(parentId);
        if (parentId != null && (parent == null || parent.isRemoved())) return;
        BlockPos pos = parent == null ? railOffset : parent.getPlot().getCenterBlock().offset(railOffset);
        if (level.hasChunkAt(pos) && LinearBlock.isRail(level.getBlockState(pos))) {
            BlockState rail = level.getBlockState(pos);
            sensorMotion.capture(child, this, parent, pos, rail);
            if (hasJoint()) {
                BlockPos support = supportingRail(child, parent, pos, rail);
                railStops.update(joint, child, worldPosition, parent, pos, rail, support, reversed, timeStep);
                if (!LinearBlock.isScrew(getBlockState())) {
                    // Native velocity damping acts along the joint relative to its parent.
                    // Zero stiffness never attracts the carriage to a position. A zero
                    // force cap disables the brake for low grease or a lost support.
                    double friction = support == null ? 0 : getBlockState().getValue(LinearBlock.GREASE).friction;
                    joint.setMotor(ConstraintJointAxis.LINEAR_Z, 0, 0, Math.max(1, 64 * friction),
                            true, child.getMassTracker().getMass() * 64 * friction);
                }
                if (support != null && LinearBlock.isScrew(getBlockState())) {
                    Direction axis = rail.getValue(LinearBlock.FACING);
                    if (reversed) axis = axis.getOpposite();
                    Vector3d point = RailContact.anchor(child, worldPosition);
                    if (parent != null) parent.logicalPose().transformPositionInverse(point);
                    double position = point.sub(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                            .dot(axis.getStepX(), axis.getStepY(), axis.getStepZ());
                    float rpm = level.getBlockEntity(support) instanceof RailEntity supporting ? supporting.getSpeed() : 0;
                    screwDrive.update(joint, position, rpm, axis, timeStep, child.getMassTracker().getMass(),
                            railStops.minimum(axis), railStops.maximum(axis));
                }
            }
        }
    }

    @Override public void sable$tick(ServerSubLevel child) {
        if (level == null) return;
        if (!bound) {
            var contact = RailContact.find(child, worldPosition, getBlockState());
            if (contact == null) return;
            bind(contact.parent(), contact.pos());
        }
        var container = SubLevelContainer.getContainer(child.getLevel());
        SubLevel parent = parentId == null ? null : container.getSubLevel(parentId);
        if (parentId != null && parent == null) { detach(); return; }
        cachedParent = parent;
        BlockPos railPos = parent == null ? railOffset : parent.getPlot().getCenterBlock().offset(railOffset);
        if (!level.hasChunkAt(railPos)) { removeJoint(); return; }
        BlockState railState = level.getBlockState(railPos);
        if (!LinearBlock.isRail(railState) || LinearBlock.isScrew(railState) != LinearBlock.isScrew(getBlockState())) { detach(); return; }
        if (level.getBlockEntity(railPos) instanceof RailEntity rail) {
            rail.finishMove();
            rail.attach(child, worldPosition);
        }
        BlockPos support = supportingRail(child, parent, railPos, railState);
        if (support == null) { detach(); return; }
        if (parent == child) { removeJoint(); return; }
        if (!RailGeometry.sameMount(jointRailState, railState) || !RailGeometry.sameMount(jointCarriageState, getBlockState())) removeJoint();
        if (!hasJoint()) {
            railStops.reset();
            Vector3d a = new Vector3d(railPos.getX() + .5, railPos.getY() + .5, railPos.getZ() + .5);
            Vector3d b = new Vector3d(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5);
            BlockState frameState = reversed ? railState.setValue(LinearBlock.FACING, railState.getValue(LinearBlock.FACING).getOpposite()) : railState;
            joint = container.physicsSystem().getPipeline().addConstraint((ServerSubLevel) parent, child,
                    new GenericConstraintConfiguration(a, b, RailGeometry.frame(frameState), RailGeometry.frame(getBlockState()), EnumSet.of(
                            ConstraintJointAxis.LINEAR_X, ConstraintJointAxis.LINEAR_Y,
                            ConstraintJointAxis.ANGULAR_X, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z)));
            if (joint == null) throw new IllegalStateException("Sable physics backend does not support generic constraints");
            // Keep contacts: the carriage must collide with scenery and the rail's parent structure.
            joint.setContactsEnabled(true);
            jointRailState = railState;
            jointCarriageState = getBlockState();
        }
        if (LinearBlock.isScrew(getBlockState())) {
            registerLoad(level.getBlockEntity(support) instanceof RailEntity rail ? rail : null);
            float rpm = level.getBlockEntity(support) instanceof RailEntity rail ? rail.getSpeed() : 0;
            // Rapier's motor-parameter update does not wake sleeping bodies.
            if (rpm != 0) {
                container.physicsSystem().getPipeline().wakeUp(child);
                if (parent instanceof ServerSubLevel movingRail) container.physicsSystem().getPipeline().wakeUp(movingRail);
            }
        }
    }

    /** A free prismatic joint while any part of the sleeve overlaps a matching rail.
     * At the open end it becomes an ordinary Sable rigid body; only real blocks stop it.
     * Query only the one or two segments under the sleeve, independent of track length.
     */
    private BlockPos supportingRail(ServerSubLevel child, SubLevel parent, BlockPos railPos, BlockState rail) {
        Vector3d anchor = new Vector3d(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5);
        child.logicalPose().transformPosition(anchor);
        if (parent != null) parent.logicalPose().transformPositionInverse(anchor);
        Direction facing = rail.getValue(LinearBlock.FACING);
        double travel = anchor.sub(railPos.getX() + .5, railPos.getY() + .5, railPos.getZ() + .5)
                .dot(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        // Both rail and carriage are one block long.
        int first = (int) Math.floor(travel - RailGeometry.CONTACT_REACH) + 1;
        int last = (int) Math.ceil(travel + RailGeometry.CONTACT_REACH) - 1;
        BlockPos nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = first; i <= last; i++) {
            BlockPos pos = railPos.relative(facing, i);
            if (!level.hasChunkAt(pos)) return railPos; // defer release until support is known
            BlockState state = level.getBlockState(pos);
            if (LinearBlock.isRail(state) && RailGeometry.compatible(state, rail) && Math.abs(travel - i) < distance) {
                nearest = pos; distance = Math.abs(travel - i);
            }
        }
        return nearest;
    }

    @Override public Iterable<SubLevel> sable$getConnectionDependencies() {
        var container = level == null ? null : SubLevelContainer.getContainer(level);
        cachedParent = parentId == null || container == null ? null : container.getSubLevel(parentId);
        return cachedParent == null || cachedParent.isRemoved() ? List.of() : List.of(cachedParent);
    }

    private void removeJoint() {
        registerLoad(null);
        sensorMotion.clear();
        railStops.reset();
        if (hasJoint()) joint.remove();
        joint = null;
        screwDrive.reset();
    }
    private void detach() {
        unlink();
        removeJoint();
        bound = false;
        parentId = null;
        cachedParent = null;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    private void unlink() {
        if (!bound || level == null) return;
        var container = SubLevelContainer.getContainer(level);
        SubLevel parent = parentId == null ? null : container.getSubLevel(parentId);
        if (parentId != null && parent == null) return;
        BlockPos pos = parent == null ? railOffset : parent.getPlot().getCenterBlock().offset(railOffset);
        SubLevel child = Sable.HELPER.getContaining(level, worldPosition);
        if (child != null && level.getBlockEntity(pos) instanceof RailEntity rail) rail.remove(child, worldPosition);
    }
    @Override public void setRemoved() { removeJoint(); super.setRemoved(); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Bound", bound);
        tag.putBoolean("Reversed", reversed);
        if (parentId != null) tag.putUUID("Parent", parentId);
        tag.putLong("Rail", railOffset.asLong());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bound = tag.getBoolean("Bound");
        reversed = tag.getBoolean("Reversed");
        parentId = tag.hasUUID("Parent") ? tag.getUUID("Parent") : null;
        railOffset = BlockPos.of(tag.getLong("Rail"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
