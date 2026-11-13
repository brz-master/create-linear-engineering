package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Two clicks with Create's wrench; Sable moves blocks, block entities, glue and tracking points. */
final class CarriageCoupling {
    private static final String SELECTION = "create_linear_engineering_selection";
    private CarriageCoupling() {}

    static void clear(Player player, ItemStack wrench) {
        CustomData.update(DataComponents.CUSTOM_DATA, wrench, tag -> tag.remove(SELECTION));
        message(player, "couple_cleared");
    }

    static void click(ServerLevel level, BlockPos pos, Player player, ItemStack wrench) {
        if (!player.mayBuild()) return;
        if (!(Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel clicked)) {
            message(player, "couple_physical_only"); return;
        }
        if (!level.mayInteract(player, global(clicked, pos))) { message(player, "couple_denied"); return; }
        var data = wrench.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (player.isShiftKeyDown()) {
            clear(player, wrench); return;
        }
        var selected = data.getCompound(SELECTION);
        var container = SubLevelContainer.getContainer(level);
        if (selected.hasUUID("Body") && selected.getString("Dimension").equals(level.dimension().location().toString())
                && level.getGameTime() - selected.getLong("Time") < 6000
                && container.getSubLevel(selected.getUUID("Body")) instanceof ServerSubLevel target
                && !target.isRemoved() && selected.contains("Carriage")
                && level.getBlockEntity(target.getPlot().getCenterBlock().offset(BlockPos.of(selected.getLong("Carriage")))) instanceof CarriageEntity) {
            if (target == clicked) {
                CustomData.update(DataComponents.CUSTOM_DATA, wrench, tag -> tag.remove(SELECTION));
                message(player, "couple_cleared"); return;
            }
            String result = merge(level, target, clicked, player);
            message(player, result);
            if (result.equals("couple_success")) CustomData.update(DataComponents.CUSTOM_DATA, wrench, tag -> tag.remove(SELECTION));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, wrench, tag -> {
            var selection = new net.minecraft.nbt.CompoundTag();
            selection.putUUID("Body", clicked.getUniqueId());
            selection.putLong("Carriage", pos.subtract(clicked.getPlot().getCenterBlock()).asLong());
            selection.putString("Dimension", level.dimension().location().toString());
            selection.putLong("Time", level.getGameTime());
            tag.put(SELECTION, selection);
        });
        message(player, "couple_selected");
    }

    static String merge(ServerLevel level, ServerSubLevel target, ServerSubLevel source, Player player) {
        if (source == target || source.isRemoved() || target.isRemoved()) return "couple_physical_only";
        // Joining a rail carrier to one of its own descendants would make a self-constraint.
        if (supports(source, target) || supports(target, source)) return "couple_parent_child";
        Quaterniond relative = new Quaterniond(target.logicalPose().orientation()).invert().mul(source.logicalPose().orientation());
        Rotation[] rotations = {Rotation.NONE, Rotation.COUNTERCLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.CLOCKWISE_90};
        int quarterTurn = -1;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(relative.dot(new Quaterniond().rotationY(i * Math.PI / 2))) > .99999) { quarterTurn = i; break; }
        }
        if (quarterTurn < 0) return "couple_align";
        BlockPos sourceAnchor = source.getPlot().getCenterBlock();
        Vector3d mapped = target.logicalPose().transformPositionInverse(RailContact.anchor(source, sourceAnchor));
        BlockPos targetAnchor = BlockPos.containing(mapped.x, mapped.y, mapped.z);
        if (mapped.distance(new Vector3d(targetAnchor.getX() + .5, targetAnchor.getY() + .5, targetAnchor.getZ() + .5)) > .125)
            return "couple_align";
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(sourceAnchor, targetAnchor, quarterTurn, rotations[quarterTurn], level);
        var bounds = source.getPlot().getBoundingBox();
        long volume = (long) (bounds.maxX() - bounds.minX() + 1) * (bounds.maxY() - bounds.minY() + 1) * (bounds.maxZ() - bounds.minZ() + 1);
        if (volume > 129L * 129 * 129) return "couple_too_large";
        List<BlockPos> blocks = new ArrayList<>();
        boolean touching = false;
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (!level.hasChunkAt(pos)) return "couple_unloaded";
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            BlockPos to = transform.apply(pos);
            if (level.isOutsideBuildHeight(to)) return "couple_overlap";
            if (!target.getPlot().contains(to.getX(), to.getZ()) || !level.getBlockState(to).isAir()) return "couple_overlap";
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos neighbor = to.relative(direction);
                if (target.getPlot().contains(neighbor.getX(), neighbor.getZ()) && !level.getBlockState(neighbor).isAir()) touching = true;
            }
            if (!BlockMovementChecks.isMovementAllowed(state, level, pos)) return "assembly_refused";
            if (player != null && !permitted(level, player, global(source, pos), global(target, to), state, transform.apply(state)))
                return "couple_denied";
            if (blocks.size() >= BearingAssembly.MAX_BLOCKS) return "couple_too_large";
            blocks.add(pos.immutable());
        }
        if (blocks.isEmpty()) return "couple_physical_only";
        // Sable splits disconnected islands. Require the door/payload to bridge the bodies.
        if (!touching) return "couple_disconnected";
        if (!BodyTransfer.move(level, source, target, transform, blocks, bounds)) return "couple_move_failed";
        SubLevelContainer.getContainer(level).removeSubLevel(source, SubLevelRemovalReason.REMOVED);
        return "couple_success";
    }

    private static BlockPos global(SubLevel body, BlockPos pos) {
        Vector3d point = RailContact.anchor(body, pos);
        return BlockPos.containing(point.x, point.y, point.z);
    }

    private static boolean permitted(ServerLevel level, Player player, BlockPos from, BlockPos to, BlockState before, BlockState after) {
        if (!level.mayInteract(player, from) || !level.mayInteract(player, to)) return false;
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, from, before, player)).isCanceled()) return false;
        var event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, to), level.getBlockState(to), player) {
            @Override public BlockState getState() { return after; }
            @Override public BlockState getPlacedBlock() { return after; }
        };
        return !NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    private static boolean supports(ServerSubLevel from, ServerSubLevel sought) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<SubLevel> pending = new ArrayDeque<>();
        pending.add(from);
        while (!pending.isEmpty()) {
            SubLevel current = pending.removeFirst();
            if (!visited.add(current.getUniqueId())) continue;
            for (var actor : current.getPlot().getBlockEntityActors()) {
                if (!(actor instanceof RailEntity rail)) continue;
                for (SubLevel child : rail.sable$getConnectionDependencies()) {
                    if (child == sought) return true;
                    pending.add(child);
                }
            }
        }
        return false;
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.create_linear_engineering." + key), true);
    }
}
