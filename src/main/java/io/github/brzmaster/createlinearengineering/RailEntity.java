package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.util.SableNBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The back-reference makes Sable connection and loading graphs bidirectional. */
public final class RailEntity extends com.simibubi.create.content.kinetics.base.KineticBlockEntity implements BlockEntitySubLevelActor {
    private record Link(UUID child, BlockPos offset) {}
    private final Set<Link> children = new HashSet<>();
    private final Set<CarriageEntity> loads = new HashSet<>();
    private float carriageImpact;
    private record BodySnapshot(UUID id, Pose3d pose, Vector3d velocity, Vector3d angular) {}
    private record PendingMove(Pose3d railFrame, List<BodySnapshot> bodies) {}
    private PendingMove pendingMove;
    long sensorPulseUntil;
    void holdSensorUntil(long until) {
        sensorPulseUntil = until;
        setChanged();
    }

    public RailEntity(BlockPos pos, BlockState state) { super(CreateLinearEngineering.RAIL_ENTITY.get(), pos, state); }
    void addLoad(CarriageEntity carriage) { loads.add(carriage); }
    void removeLoad(CarriageEntity carriage) { loads.remove(carriage); }
    @Override public float calculateStressApplied() {
        return lastStressApplied = LinearBlock.isScrew(getBlockState()) ? carriageImpact : 0;
    }
    private void updateLoad() {
        loads.removeIf(carriage -> carriage.isRemoved() || !carriage.isBound()
                || !carriage.hasJoint() || carriage.loadRail() != this);
        float next = RailLoad.calculate(loads);
        if (Float.compare(next, carriageImpact) == 0) return;
        carriageImpact = next;
        float impact = calculateStressApplied();
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, impact);
        setChanged();
        sendData();
    }
    @Override public void tick() {
        if (LinearBlock.isScrew(getBlockState())) super.tick();
        if (level != null && !level.isClientSide) { refreshBindings(); updateLoad(); RailSensor.tick(this); }
    }
    /** Also covers a second click before Sable updates its spatial query index. */
    public boolean hasCarriageHere() {
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        SubLevel parent = Sable.HELPER.getContaining(level, worldPosition);
        Vector3d anchor = globalAnchor(parent, worldPosition);
        for (Link link : children) {
            SubLevel child = container.getSubLevel(link.child());
            if (child == null) continue;
            BlockPos pos = child.getPlot().getCenterBlock().offset(link.offset());
            if (globalAnchor(child, pos).distanceSquared(anchor) < 1 - 1e-5) return true;
        }
        return false;
    }
    public void attach(ServerSubLevel child, BlockPos carriagePos) {
        BlockPos offset = carriagePos.subtract(child.getPlot().getCenterBlock());
        if (children.add(new Link(child.getUniqueId(), offset))) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    public void remove(SubLevel child, BlockPos carriagePos) {
        if (children.remove(new Link(child.getUniqueId(), carriagePos.subtract(child.getPlot().getCenterBlock())))) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    public void refreshBindings() {
        finishMove();
        refreshReferences();
    }
    private void refreshReferences() {
        if (!(level instanceof ServerLevel server) || children.isEmpty()) return;
        var container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        SubLevel parent = Sable.HELPER.getContaining(server, worldPosition);
        boolean changed = children.removeIf(entry -> {
            if (!(container.getSubLevel(entry.child()) instanceof ServerSubLevel child) || child == parent) return false;
            if (!(server.getBlockEntity(child.getPlot().getCenterBlock().offset(entry.offset())) instanceof CarriageEntity carriage) || !carriage.isBound()) return true;
            carriage.bind(parent, worldPosition);
            return false;
        });
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }
    /** Snapshot first; Sable finalizes the destination pose only after afterMove returns. */
    public void prepareMove(ServerLevel origin, BlockPos oldPos, BlockState oldState) {
        if (level != origin || children.isEmpty()) return;
        var container = SubLevelContainer.getContainer(origin);
        SubLevel oldParent = Sable.HELPER.getContaining(origin, oldPos);
        SubLevel newParent = Sable.HELPER.getContaining(origin, worldPosition);
        Pose3d frame = new Pose3d();
        frame.position().set(globalAnchor(oldParent, oldPos));
        frame.orientation().set(RailGeometry.frame(oldState));
        if (oldParent != null) frame.orientation().premul(oldParent.logicalPose().orientation());
        List<BodySnapshot> snapshots = new ArrayList<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        for (Link link : children) pending.add(link.child());
        Set<UUID> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            if (!visited.add(id) || !(container.getSubLevel(id) instanceof ServerSubLevel child)
                    || child == oldParent || child == newParent) continue;
            var handle = container.physicsSystem().getPhysicsHandle(child);
            snapshots.add(new BodySnapshot(id, new Pose3d(child.logicalPose()),
                    handle.getLinearVelocity(new Vector3d()), handle.getAngularVelocity(new Vector3d())));
            for (var actor : child.getPlot().getBlockEntityActors()) {
                if (actor instanceof RailEntity rail) for (Link link : rail.children) pending.add(link.child());
            }
        }
        pendingMove = new PendingMove(frame, snapshots);
        refreshReferences();
        setChanged();
    }
    /** Use absolute snapshots: overlapping rails set the same target instead of
     * applying a relative transform twice. Descendants receive the same isometry. */
    public void finishMove() {
        if (pendingMove == null || !(level instanceof ServerLevel server)) return;
        PendingMove move = pendingMove;
        pendingMove = null;
        var container = SubLevelContainer.getContainer(server);
        SubLevel parent = Sable.HELPER.getContaining(server, worldPosition);
        Vector3d newAnchor = globalAnchor(parent, worldPosition);
        Quaterniond frame = RailGeometry.frame(getBlockState());
        if (parent != null) frame.premul(parent.logicalPose().orientation());
        Quaterniond delta = frame.mul(new Quaterniond(move.railFrame().orientation()).invert());
        var pipeline = container.physicsSystem().getPipeline();
        for (BodySnapshot snapshot : move.bodies()) {
            if (!(container.getSubLevel(snapshot.id()) instanceof ServerSubLevel child) || child == parent) continue;
            var handle = container.physicsSystem().getPhysicsHandle(child);
            Vector3d currentVelocity = handle.getLinearVelocity(new Vector3d());
            Vector3d currentAngular = handle.getAngularVelocity(new Vector3d());
            child.logicalPose().position().set(snapshot.pose().position()).sub(move.railFrame().position()).rotate(delta).add(newAnchor);
            child.logicalPose().orientation().set(snapshot.pose().orientation()).premul(delta);
            pipeline.teleport(child, child.logicalPose().position(), child.logicalPose().orientation());
            pipeline.addLinearAndAngularVelocity(child,
                    delta.transform(snapshot.velocity(), new Vector3d()).sub(currentVelocity),
                    delta.transform(snapshot.angular(), new Vector3d()).sub(currentAngular));
            child.updateLastPose();
        }
        setChanged();
    }
    private static Vector3d globalAnchor(SubLevel parent, BlockPos pos) {
        Vector3d anchor = new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        if (parent != null) parent.logicalPose().transformPosition(anchor);
        return anchor;
    }
    @Override public void sable$tick(ServerSubLevel subLevel) { refreshBindings(); }
    @Override public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (level == null) return List.of();
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return List.of();
        List<SubLevel> result = new ArrayList<>();
        for (Link link : children) {
            SubLevel child = container.getSubLevel(link.child());
            if (child != null && !child.isRemoved()) result.add(child);
        }
        return result;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("CarriageImpact", carriageImpact);
        tag.putLong("SensorPulseUntil", sensorPulseUntil);
        ListTag list = new ListTag();
        for (Link child : children) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", child.child());
            entry.putLong("Offset", child.offset().asLong());
            list.add(entry);
        }
        tag.put("Children", list);
        if (pendingMove != null) {
            CompoundTag motion = new CompoundTag();
            motion.put("Frame", SableNBTUtils.writePose3d(pendingMove.railFrame()));
            ListTag bodies = new ListTag();
            for (BodySnapshot body : pendingMove.bodies()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Id", body.id());
                entry.put("Pose", SableNBTUtils.writePose3d(body.pose()));
                entry.put("Velocity", SableNBTUtils.writeVector3d(body.velocity()));
                entry.put("Angular", SableNBTUtils.writeVector3d(body.angular()));
                bodies.add(entry);
            }
            motion.put("Bodies", bodies);
            tag.put("PendingMove", motion);
        }
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        carriageImpact = tag.getFloat("CarriageImpact");
        sensorPulseUntil = tag.getLong("SensorPulseUntil");
        children.clear();
        ListTag list = tag.getList("Children", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) children.add(new Link(entry.getUUID("Id"), BlockPos.of(entry.getLong("Offset"))));
        }
        pendingMove = null;
        if (tag.contains("PendingMove", Tag.TAG_COMPOUND)) {
            CompoundTag motion = tag.getCompound("PendingMove");
            List<BodySnapshot> snapshots = new ArrayList<>();
            ListTag bodies = motion.getList("Bodies", Tag.TAG_COMPOUND);
            for (int i = 0; i < bodies.size(); i++) {
                CompoundTag body = bodies.getCompound(i);
                if (body.hasUUID("Id")) snapshots.add(new BodySnapshot(body.getUUID("Id"),
                        SableNBTUtils.readPose3d(body.getCompound("Pose")), SableNBTUtils.readVector3d(body.getCompound("Velocity")),
                        SableNBTUtils.readVector3d(body.getCompound("Angular"))));
            }
            pendingMove = new PendingMove(SableNBTUtils.readPose3d(motion.getCompound("Frame")), snapshots);
        }
    }
}
