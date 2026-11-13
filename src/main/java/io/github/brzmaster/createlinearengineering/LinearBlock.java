package io.github.brzmaster.createlinearengineering;

import com.mojang.serialization.MapCodec;
import net.createmod.catnip.placement.PlacementHelpers;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LinearBlock extends com.simibubi.create.content.kinetics.base.KineticBlock implements EntityBlock, BlockSubLevelAssemblyListener {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final DirectionProperty NORMAL = DirectionProperty.create("normal");
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<RailSensor.Mode> SENSOR =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("sensor", RailSensor.Mode.class);
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Grease> GREASE =
            net.minecraft.world.level.block.state.properties.EnumProperty.create("grease", Grease.class);
    private static final int RAIL_PLACEMENT = PlacementHelpers.register(new RailPlacementHelper());
    private static final int SCREW_PLACEMENT = PlacementHelpers.register(new RailPlacementHelper(true));
    private final boolean carriage;
    private final boolean screw;
    public LinearBlock(boolean carriage, Properties properties) { this(carriage, false, properties); }
    public LinearBlock(boolean carriage, boolean screw, Properties properties) {
        super(properties);
        this.carriage = carriage;
        this.screw = screw;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(NORMAL, Direction.UP).setValue(SENSOR, RailSensor.Mode.NONE).setValue(POWERED, false).setValue(GREASE, Grease.NONE));
    }

    @Override protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new LinearBlock(carriage, screw, properties));
    }
    public static boolean isRail(BlockState state) { return state.getBlock() instanceof LinearBlock block && !block.carriage; }
    public static boolean isCarriage(BlockState state) { return state.getBlock() instanceof LinearBlock block && block.carriage; }
    public static boolean isScrew(BlockState state) { return state.getBlock() instanceof LinearBlock block && block.screw; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return screw && !carriage && face.getAxis() == getRotationAxis(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, NORMAL, SENSOR, POWERED, GREASE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (carriage) return null; // CarriageItem creates the Sable body directly on a rail.
        Direction normal = context.getClickedFace();
        BlockState support = context.getLevel().getBlockState(context.getClickedPos().relative(normal.getOpposite()));
        if (support.is(this)) return bare(support);
        for (Direction direction : context.getNearestLookingDirections()) {
            BlockState adjacent = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (adjacent.is(this) && adjacent.getValue(FACING).getAxis() == direction.getAxis()) return bare(adjacent);
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != normal.getAxis())
                return defaultBlockState().setValue(FACING, direction.getOpposite()).setValue(NORMAL, normal);
        }
        return null;
    }
    static BlockState bare(BlockState state) { return state.setValue(SENSOR, RailSensor.Mode.NONE).setValue(POWERED, false).setValue(GREASE, Grease.NONE); }
    @Override protected boolean isSignalSource(BlockState state) { return !carriage && state.getValue(SENSOR) != RailSensor.Mode.NONE; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // Minecraft asks from the receiving block towards this source.
        return !carriage && state.getValue(SENSOR) != RailSensor.Mode.NONE && state.getValue(POWERED)
                && direction == RailGeometry.normal(state) ? 15 : 0;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        super.onRemove(state, level, pos, next, moved);
        if (!state.is(next.getBlock()) && state.getValue(POWERED)) RailSensor.notifyOutput(level, pos, state);
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING))).setValue(NORMAL, rotation.rotate(RailGeometry.normal(state)));
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(NORMAL, mirror.mirror(RailGeometry.normal(state)));
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RailGeometry.shape(state, carriage);
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RailGeometry.collisionShape(state, carriage);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof GreaseItem) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if ((carriage || state.getValue(SENSOR) != RailSensor.Mode.NONE) && com.simibubi.create.AllItems.WRENCH.isIn(stack)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        var helper = PlacementHelpers.get(screw ? SCREW_PLACEMENT : RAIL_PLACEMENT);
        if (!carriage && !player.isShiftKeyDown() && helper.matchesItem(stack))
            return helper.getOffset(player, level, state, pos, hit).placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hit);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public InteractionResult onWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        if (!carriage || context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel() instanceof ServerLevel server)
            CarriageCoupling.click(server, context.getClickedPos(), context.getPlayer(), context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResult onSneakWrenched(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (!carriage && state.getValue(SENSOR) != RailSensor.Mode.NONE) return RailSensor.toggle(context);
        if (!context.getLevel().isClientSide) CarriageCoupling.clear(context.getPlayer(), context.getItemInHand());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return carriage ? new CarriageEntity(pos, state) : new RailEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (carriage) return null;
        return (world, pos, block, entity) -> { if (entity instanceof RailEntity rail) rail.tick(); };
    }
    @Override public void afterMove(ServerLevel origin, ServerLevel result, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (!carriage && result.getBlockEntity(newPos) instanceof RailEntity rail) {
            rail.prepareMove(origin, oldPos, state);
        }
        if (carriage && result.getBlockEntity(newPos) instanceof CarriageEntity entity) entity.afterTransfer();
    }
    @Override public void beforeMove(ServerLevel origin, ServerLevel result, BlockState state, BlockPos oldPos, BlockPos newPos) {
        if (carriage && origin.getBlockEntity(oldPos) instanceof CarriageEntity entity) entity.beforeTransfer();
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (carriage && !old.is(this) && !level.isClientSide) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (carriage && level.getBlockEntity(pos) instanceof CarriageEntity entity && !entity.isBound()) {
            BearingAssembly.assemble(level, pos, null);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!carriage || !(level.getBlockEntity(pos) instanceof CarriageEntity entity)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && !entity.isBound()) BearingAssembly.assemble(server, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
