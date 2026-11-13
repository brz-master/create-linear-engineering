package io.github.brzmaster.createlinearengineering;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.createmod.catnip.placement.PlacementHelpers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import dev.ryanhcode.sable.Sable;

/** The item installs a physical carriage only on a rail, never a free world block. */
public final class CarriageItem extends BlockItem {
    private static final int PLACEMENT = PlacementHelpers.register(new CarriagePlacement());
    private static final int SCREW_PLACEMENT = PlacementHelpers.register(new CarriagePlacement(true));
    public CarriageItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var anchor = RailContact.anchor(Sable.HELPER.getContaining(level, pos), pos);
        BlockPos worldPos = BlockPos.containing(anchor.x, anchor.y, anchor.z);
        var player = context.getPlayer();
        if (player == null || !player.mayBuild() || !level.mayInteract(player, worldPos)
                || !player.mayUseItemAt(worldPos, context.getClickedFace(), context.getItemInHand())
                || LinearBlock.isScrew(getBlock().defaultBlockState()) != LinearBlock.isScrew(level.getBlockState(pos))
                || !CarriagePlacement.canPlace(level, pos)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel server) {
            BlockState rail = level.getBlockState(pos);
            BlockState carriage = CarriagePlacement.state(rail);
            // The installation shares the rail's world cell; the rail itself is never replaced.
            var event = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, worldPos), rail, player) {
                @Override public BlockState getState() { return carriage; }
                @Override public BlockState getPlacedBlock() { return carriage; }
            };
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) return InteractionResult.FAIL;
            CarriagePlacement.place(server, pos);
            var sound = getBlock().defaultBlockState().getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 1, .8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player));
            if (!player.isCreative()) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
