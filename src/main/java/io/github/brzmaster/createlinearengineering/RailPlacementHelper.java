package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.foundation.placement.PoleHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

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
        PlacementOffset offset = super.getOffset(player, world, state, pos, ray);
        return offset.withTransform(offset.getTransform().andThen(s -> LinearBlock.bare(s).setValue(LinearBlock.NORMAL, RailGeometry.normal(state))));
    }
}
