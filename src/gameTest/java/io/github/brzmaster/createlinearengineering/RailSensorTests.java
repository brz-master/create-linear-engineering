package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class RailSensorTests {
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void sensorPowersOnlyRearLampInAllSixMountings(GameTestHelper h) {
        List<BlockPos> sensors = new ArrayList<>();
        for (Direction normal : Direction.values()) {
            BlockPos pos = origin(h, 130 + normal.ordinal());
            Direction axis = normal.getAxis() == Direction.Axis.Z ? Direction.EAST : Direction.SOUTH;
            BlockState state = sensor(true, RailSensor.Mode.APPROXIMATE)
                    .setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, normal);
            h.getLevel().setBlock(pos, state, 3);
            for (Direction side : Direction.values()) h.getLevel().setBlock(pos.relative(side), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
            h.assertTrue(CarriagePlacement.place(h.getLevel(), pos) != null, "Sensor blocked carriage mounting " + normal);
            sensors.add(pos);
        }
        // All six lamps share their sensor's chunk. Begin timing only after
        // vanilla can execute both block-entity updates and scheduled lamp ticks.
        awaitWorldTicks(h, sensors, 0, () -> {
            h.runAfterDelay(12, () -> {
                for (BlockPos pos : sensors) {
                    BlockState state = h.getLevel().getBlockState(pos);
                    Direction normal = RailGeometry.normal(state);
                    h.assertTrue(state.getValue(LinearBlock.POWERED), "Screw carriage was not detected " + normal);
                    for (Direction side : Direction.values()) {
                        int expected = side == normal ? 15 : 0;
                        h.assertTrue(state.getSignal(h.getLevel(), pos, side) == expected, "Wrong weak signal face " + normal + "/" + side);
                        // Weak-only output lights the rear lamp without powering it as a
                        // conductor and leaking into lamps beside it along the track.
                        h.assertTrue(state.getDirectSignal(h.getLevel(), pos, side) == 0, "Sensor leaked strong power " + normal + "/" + side);
                        h.assertTrue(lit(h, pos.relative(side)) == (side == normal.getOpposite()), "Wrong lamp powered " + normal + "/" + side);
                    }
                    h.getLevel().removeBlock(pos, false);
                }
            });
            h.runAfterDelay(22, () -> {
                for (BlockPos pos : sensors) for (Direction side : Direction.values()) {
                    BlockPos lamp = pos.relative(side);
                    h.assertTrue(!lit(h, lamp), "Removing a sensor left a powered lamp; sensor=" + pos
                            + ", side=" + side + ", sensorState=" + h.getLevel().getBlockState(pos)
                            + ", neighborSignal=" + h.getLevel().hasNeighborSignal(lamp)
                            + ", pendingOff=" + h.getLevel().getBlockTicks().hasScheduledTick(lamp, Blocks.REDSTONE_LAMP)
                            + "; " + tickReadiness(h, lamp));
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void approximateOverlapAndPreciseCentreUseMovingPose(GameTestHelper h) {
        BlockPos start = origin(h, 136), sensorPos = start.south(2);
        for (int i = 0; i < 5; i++) h.getLevel().setBlock(start.south(i), CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        h.getLevel().setBlock(sensorPos, sensor(false, RailSensor.Mode.APPROXIMATE), 3);
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), start);
        h.assertTrue(body != null, "Cannot create passive carriage");
        awaitWorldTicks(h, List.of(sensorPos), 0, () -> {
            h.runAfterDelay(5, () -> moveAnchor(h, body, centre(sensorPos).add(0, 0, -1.05)));
            h.runAfterDelay(10, () -> {
                movingPosePowered(h, sensorPos, body, false, "Approximate sensor fired before overlap");
                moveAnchor(h, body, centre(sensorPos).add(0, 0, -.95));
            });
            h.runAfterDelay(15, () -> {
                movingPosePowered(h, sensorPos, body, true, "Carriage overlapping later rail segment was not detected");
                setMode(h, sensorPos, RailSensor.Mode.PRECISE);
            });
            h.runAfterDelay(20, () -> {
                movingPosePowered(h, sensorPos, body, false, "Precise sensor accepted a carriage far from centre");
                moveAnchor(h, body, centre(sensorPos).add(0, 0, -.02));
            });
            h.runAfterDelay(25, () -> {
                movingPosePowered(h, sensorPos, body, true, "Precise sensor missed centre tolerance");
                moveAnchor(h, body, centre(sensorPos).add(0, 0, .065));
            });
            h.runAfterDelay(30, () -> {
                movingPosePowered(h, sensorPos, body, false, "Precise sensor stayed active outside centre tolerance");
                moveAnchor(h, body, centre(sensorPos));
            });
            h.runAfterDelay(35, () -> {
                movingPosePowered(h, sensorPos, body, true, "Exactly centred carriage was not detected");
                h.succeed();
            });
        });
    }

    private static void awaitWorldTicks(GameTestHelper h, List<BlockPos> positions, int elapsed, Runnable ready) {
        if (positions.stream().allMatch(pos -> worldTicksReady(h, pos))) ready.run();
        else if (elapsed < 120) h.runAfterDelay(1, () -> awaitWorldTicks(h, positions, elapsed + 1, ready));
        else h.assertTrue(false, "Sensor fixture never became eligible for vanilla ticks: "
                + positions.stream().map(pos -> tickReadiness(h, pos)).toList());
    }

    private static boolean worldTicksReady(GameTestHelper h, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        // Match ServerLevel's scheduled-block-tick predicate, plus sensor BE eligibility.
        return h.getLevel().areEntitiesLoaded(chunk) && h.getLevel().getChunkSource().isPositionTicking(chunk)
                && h.getLevel().isPositionEntityTicking(pos);
    }

    private static String tickReadiness(GameTestHelper h, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        return "pos=" + pos + ", time=" + h.getLevel().getGameTime()
                + ", entitiesLoaded=" + h.getLevel().areEntitiesLoaded(chunk)
                + ", chunkTicking=" + h.getLevel().getChunkSource().isPositionTicking(chunk)
                + ", entityTicking=" + h.getLevel().isPositionEntityTicking(pos);
    }

    private static void movingPosePowered(GameTestHelper h, BlockPos pos, ServerSubLevel body, boolean expected, String message) {
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(LinearBlock.POWERED) == expected,
                message + "; " + movingPoseDiagnostic(h, pos, body));
    }

    private static String movingPoseDiagnostic(GameTestHelper h, BlockPos pos, ServerSubLevel body) {
        var level = h.getLevel();
        var entity = level.getBlockEntity(pos);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return "time=" + level.getGameTime() + " occupancy=" + RailSensor.detects(level, pos, level.getBlockState(pos))
                + " until=" + (entity instanceof RailEntity rail ? rail.sensorPulseUntil : "missingBE")
                + " entitiesLoaded=" + level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos))
                + " entityTicking=" + level.isPositionEntityTicking(pos)
                + " chunkStatus=" + (chunk == null ? "missing" : chunk.getFullStatus())
                + " anchor=" + RailContact.anchor(body, body.getPlot().getCenterBlock());
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void nearbyParallelCarriageDoesNotTriggerSensor(GameTestHelper h) {
        BlockPos pos = origin(h, 137);
        h.getLevel().setBlock(pos, sensor(false, RailSensor.Mode.APPROXIMATE), 3);
        h.getLevel().setBlock(pos.east(), CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        ServerSubLevel neighbour = CarriagePlacement.place(h.getLevel(), pos.east());
        h.assertTrue(neighbour != null, "Cannot create neighbouring carriage");
        awaitWorldTicks(h, List.of(pos, pos.east()), 0, () -> {
            h.runAfterDelay(12, () -> {
                powered(h, pos, false, "Sensor detected carriage on parallel lane");
                h.assertTrue(CarriagePlacement.place(h.getLevel(), pos) != null, "Neighbouring lane blocked actual carriage placement");
            });
            h.runAfterDelay(22, () -> {
                powered(h, pos, true, "Sensor did not detect its own carriage");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void sensorItemWrenchLootAndRailExtensionStayConsistent(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        ItemStack previous = player.getMainHandItem();
        GameType previousMode = player.gameMode.getGameModeForPlayer();
        boolean previousShift = player.isShiftKeyDown();
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        try {
            for (boolean screw : new boolean[]{false, true}) {
                BlockPos pos = origin(h, screw ? 140 : 139);
                var block = screw ? CreateLinearEngineering.SCREW_RAIL.get() : CreateLinearEngineering.RAIL.get();
                h.getLevel().setBlock(pos, block.defaultBlockState().setValue(LinearBlock.FACING, Direction.EAST).setValue(LinearBlock.NORMAL, Direction.DOWN), 3);
                ItemStack sensors = new ItemStack(CreateLinearEngineering.SENSOR.get(), 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, sensors);
                h.assertTrue(click(h, player, pos, Direction.DOWN).consumesAction(), "Sensor item was not installed");
                h.assertTrue(sensors.getCount() == 1, "Survival installation did not consume exactly one sensor");
                h.assertTrue(h.getLevel().getBlockState(pos).getValue(LinearBlock.SENSOR) == RailSensor.Mode.APPROXIMATE, "Unexpected installed mode");
                click(h, player, pos, Direction.DOWN);
                h.assertTrue(sensors.getCount() == 1, "Duplicate sensor installation consumed an item");
                player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                player.setShiftKeyDown(true);
                h.assertTrue(click(h, player, pos, Direction.DOWN).consumesAction(), "Shift wrench was not handled");
                player.setShiftKeyDown(false);
                BlockState installed = h.getLevel().getBlockState(pos);
                h.assertTrue(installed.getValue(LinearBlock.SENSOR) == RailSensor.Mode.PRECISE, "Shift wrench did not switch mode");
                List<ItemStack> drops = Block.getDrops(installed, h.getLevel(), pos, h.getLevel().getBlockEntity(pos));
                h.assertTrue(count(drops, block.asItem()) == 1 && count(drops, CreateLinearEngineering.SENSOR.get()) == 1,
                        "Installed rail must drop exactly one rail and one sensor");
                h.assertTrue(drops.stream().mapToInt(ItemStack::getCount).sum() == 2, "Unexpected additional sensor loot");
                h.getLevel().setBlock(pos, installed.setValue(LinearBlock.POWERED, true), 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(pos, Direction.DOWN)));
                BlockState placed = block.getStateForPlacement(context);
                h.assertTrue(placed != null && placed.getValue(LinearBlock.SENSOR) == RailSensor.Mode.NONE && !placed.getValue(LinearBlock.POWERED),
                        "Ordinary placement copied installed sensor or live output");
                var helper = PlacementHelpers.getHelpersView().stream().filter(p -> p instanceof RailPlacementHelper && p.matchesItem(player.getMainHandItem())).findFirst().orElseThrow();
                var offset = helper.getOffset(player, h.getLevel(), h.getLevel().getBlockState(pos), pos, hit(pos, Direction.EAST));
                h.assertTrue(offset.isSuccessful(), "Create rail extension failed");
                BlockState preview = offset.getTransform().apply(block.defaultBlockState());
                h.assertTrue(preview.getValue(LinearBlock.SENSOR) == RailSensor.Mode.NONE && !preview.getValue(LinearBlock.POWERED),
                        "Create extension copied installed sensor or live output");
            }
            BlockPos stone = origin(h, 141);
            h.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            ItemStack sensors = new ItemStack(CreateLinearEngineering.SENSOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, sensors);
            click(h, player, stone, Direction.UP);
            h.assertTrue(sensors.getCount() == 1 && h.getLevel().getBlockState(stone).is(Blocks.STONE), "Sensor installed on a non-rail block");
        } finally {
            player.setShiftKeyDown(previousShift);
            player.setGameMode(previousMode);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void sensorFollowsRotatedMovingRailParent(GameTestHelper h) {
        BlockPos pos = origin(h, 142);
        h.getLevel().setBlock(pos, sensor(true, RailSensor.Mode.PRECISE), 3);
        h.getLevel().setBlock(pos.below(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        ServerSubLevel parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, List.of(pos, pos.below()),
                new BoundingBox3i(pos.getX(), pos.getY() - 1, pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        BlockPos rail = parent.getPlot().getCenterBlock();
        Vector3d before = RailContact.anchor(parent, rail);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(before.sub(RailContact.anchor(parent, rail)));
        var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
        pipeline.teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        h.assertTrue(CarriagePlacement.place(h.getLevel(), rail) != null, "Cannot mount carriage on rotated sensor rail");
        Vector3d start = new Vector3d(parent.logicalPose().position());
        h.runAfterDelay(8, () -> pipeline.addLinearAndAngularVelocity(parent, new Vector3d(1, 0, 0), new Vector3d()));
        h.runAfterDelay(24, () -> {
            powered(h, rail, true, "Rotated moving sensor lost centred carriage");
            h.assertTrue(lit(h, rail.below()), "Sensor did not power rear lamp in its own sublevel");
            h.assertTrue(parent.logicalPose().position().distance(start) > .2, "Parent fixture never moved");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void tenRailSensorsLightTheirLampsInSequence(GameTestHelper h) {
        BlockPos start = origin(h, 143).north(6);
        List<BlockPos> sensors = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sensors.add(start.south(i));
            h.getLevel().setBlock(start.south(i), sensor(false, RailSensor.Mode.APPROXIMATE), 3);
            h.getLevel().setBlock(start.south(i).below(), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
        }
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), start);
        h.assertTrue(body != null, "Cannot create carriage for sensor sequence");
        awaitWorldTicks(h, sensors, 0, () -> {
            for (int step = 0; step < 10; step++) {
                final int active = step;
                h.runAfterDelay((step + 1) * 10, () -> {
                    for (int i = 0; i < 10; i++) {
                        BlockPos sensor = start.south(i);
                        Vector3d observed = RailContact.anchor(body, body.getPlot().getCenterBlock());
                        String diagnostic = "; anchorZ=" + observed.z + ", offsetFromIntendedCentre="
                                + (observed.z - centre(start.south(active)).z) + ", offsetFromSensor="
                                + (observed.z - centre(sensor).z) + "; " + movingPoseDiagnostic(h, sensor, body);
                        powered(h, sensor, i == active, "Wrong sensor in sequence at step " + active + ", rail " + i + diagnostic);
                        h.assertTrue(lit(h, sensor.below()) == (i == active),
                                "Wrong lamp in sequence at step " + active + ", rail " + i + diagnostic);
                    }
                    if (active == 9) h.succeed();
                    else moveAnchor(h, body, centre(start.south(active + 1)));
                });
            }
        });
    }

    private static BlockState sensor(boolean screw, RailSensor.Mode mode) {
        return (screw ? CreateLinearEngineering.SCREW_RAIL.get() : CreateLinearEngineering.RAIL.get()).defaultBlockState()
                .setValue(LinearBlock.SENSOR, mode).setValue(LinearBlock.POWERED, false);
    }
    private static void setMode(GameTestHelper h, BlockPos pos, RailSensor.Mode mode) {
        h.getLevel().setBlock(pos, h.getLevel().getBlockState(pos).setValue(LinearBlock.SENSOR, mode), 3);
    }
    private static void powered(GameTestHelper h, BlockPos pos, boolean expected, String message) {
        h.assertTrue(h.getLevel().getBlockState(pos).getValue(LinearBlock.POWERED) == expected, message);
    }
    private static boolean lit(GameTestHelper h, BlockPos pos) {
        BlockState state = h.getLevel().getBlockState(pos);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(BlockStateProperties.LIT);
    }
    private static long count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static BlockHitResult hit(BlockPos pos, Direction direction) {
        return new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false);
    }
    private static net.minecraft.world.InteractionResult click(GameTestHelper h, FakePlayer player, BlockPos pos, Direction direction) {
        return player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos, direction));
    }
    private static Vector3d centre(BlockPos pos) { return RailContact.anchor(null, pos); }
    private static void moveAnchor(GameTestHelper h, ServerSubLevel body, Vector3d target) {
        var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
        var handle = physics.getPhysicsHandle(body);
        physics.getPipeline().addLinearAndAngularVelocity(body, handle.getLinearVelocity(new Vector3d()).negate(), handle.getAngularVelocity(new Vector3d()).negate());
        body.logicalPose().position().add(target.sub(RailContact.anchor(body, body.getPlot().getCenterBlock())));
        physics.getPipeline().teleport(body, body.logicalPose().position(), body.logicalPose().orientation());
        body.updateLastPose();
    }
    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
