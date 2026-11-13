package io.github.brzmaster.createlinearengineering;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** Installs once on an existing rail; removal uses the rail's normal loot table. */
public final class RailSensorItem extends Item {
    public RailSensorItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!LinearBlock.isRail(state) || state.getValue(LinearBlock.SENSOR) != RailSensor.Mode.NONE || !AttachmentPlacement.canModify(context))
            return InteractionResult.FAIL;
        if (!level.isClientSide) {
            BlockState installed = state.setValue(LinearBlock.SENSOR, RailSensor.Mode.APPROXIMATE);
            if (!AttachmentPlacement.applyChange(context, installed.setValue(LinearBlock.POWERED, RailSensor.detects(level, pos, installed))))
                return InteractionResult.FAIL;
            RailSensor.notifyOutput(level, pos, installed);
            level.playSound(null, pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, .6f, 1);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer()));
            if (!context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
