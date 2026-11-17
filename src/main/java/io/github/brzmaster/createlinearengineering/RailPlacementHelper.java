package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.foundation.placement.PoleHelper;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Create's pole extension, ghost preview, arrow, range and Extendo Grip support. */
public final class RailPlacementHelper extends PoleHelper<Direction> {
    private final boolean screw;
    public RailPlacementHelper() { this(false); }
    public RailPlacementHelper(boolean screw) {
        super(s -> LinearBlock.isRail(s) && LinearBlock.isScrew(s) == screw, s -> s.getValue(LinearBlock.FACING).getAxis(), LinearBlock.FACING);
        this.screw = screw;
    }

    @Override public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.is((screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().asItem());
    }

    @Override public int attachedPoles(Level world, BlockPos pos, Direction direction) {
        BlockState origin = world.getBlockState(pos);
        int count = 0;
        BlockPos next = pos.relative(direction);
        while (world.hasChunkAt(next)) {
            BlockState state = world.getBlockState(next);
            if (!LinearBlock.isRail(state) || !RailGeometry.compatible(origin, state)) break;
            count++;
            next = next.relative(direction);
        }
        return count;
    }

    @Override public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
        int range = AllConfigs.server().equipment.placementAssistRange.get();
        var reach = player == null ? null : player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) range += 4;
        var hand = player != null && matchesItem(player.getOffhandItem()) && !matchesItem(player.getMainHandItem())
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var stack = player == null ? state.getBlock().asItem().getDefaultInstance() : player.getItemInHand(hand);

        for (Direction direction : IPlacementHelper.orderedByDistanceOnlyAxis(pos, ray.getLocation(), axisFunction.apply(state))) {
            int count = attachedPoles(world, pos, direction);
            if (count >= range) continue;
            BlockPos end = pos.relative(direction, count);
            BlockPos target = end.relative(direction);
            if (!world.hasChunkAt(target) || !world.getBlockState(target).canBeReplaced()) continue;
            // A click on the last rail face runs Sable's normal cross-sublevel placement check.
            var face = new BlockHitResult(Vec3.atCenterOf(end).relative(direction, .5), direction, end, false);
            if (new BlockPlaceContext(world, player, hand, stack, face).canPlace())
                return PlacementOffset.success(target, ignored -> LinearBlock.bare(state).setValue(LinearBlock.NORMAL, RailGeometry.normal(state)));
        }
        return PlacementOffset.fail();
    }
}
