package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;
import java.util.List;

/** Lubricates either carriage using the same protected attachment path as sensors. */
public final class GreaseItem extends Item {
    private final Grease grease;
    public GreaseItem(Grease grease, Properties properties) { super(properties); this.grease = grease; }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!LinearBlock.isCarriage(state) || !AttachmentPlacement.canModify(context)) return InteractionResult.FAIL;
        if (state.getValue(LinearBlock.GREASE) == grease) return InteractionResult.sidedSuccess(level.isClientSide);
        if (!level.isClientSide) {
            if (!AttachmentPlacement.applyChange(context, state.setValue(LinearBlock.GREASE, grease))) return InteractionResult.FAIL;
            if (level instanceof net.minecraft.server.level.ServerLevel server
                    && Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel body)
                SubLevelContainer.getContainer(server).physicsSystem().getPipeline().wakeUp(body);
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, .6f, .8f);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer()));
            if (!context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.create_linear_engineering.grease_" + grease.getSerializedName()));
    }
}
