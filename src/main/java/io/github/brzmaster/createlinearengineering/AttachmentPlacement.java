package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Shared protected block-state update for rail and carriage accessories. */
final class AttachmentPlacement {
    private AttachmentPlacement() {}
    static boolean canModify(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var point = RailContact.anchor(Sable.HELPER.getContaining(level, context.getClickedPos()), context.getClickedPos());
        BlockPos world = BlockPos.containing(point.x, point.y, point.z);
        return player != null && player.mayBuild() && level.mayInteract(player, world)
                && player.mayUseItemAt(world, context.getClickedFace(), context.getItemInHand());
    }

    /** One protection event at the real position, also for attachments in Sable plots. */
    static boolean applyChange(UseOnContext context, BlockState installed) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState previous = level.getBlockState(pos);
        var point = RailContact.anchor(Sable.HELPER.getContaining(level, pos), pos);
        BlockPos world = BlockPos.containing(point.x, point.y, point.z);
        var event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, world), previous, context.getPlayer()) {
            @Override public BlockState getState() { return installed; }
            @Override public BlockState getPlacedBlock() { return installed; }
        };
        if (NeoForge.EVENT_BUS.post(event).isCanceled() || level.getBlockState(pos) != previous) return false;
        // Match CarriagePlacement: the outer ItemStack hook must not capture a
        // second placement at storage coordinates, nor capture neighbouring lamps.
        boolean capturing = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        try {
            return level.setBlock(pos, installed, 3);
        } finally {
            level.captureBlockSnapshots = capturing;
        }
    }

}
