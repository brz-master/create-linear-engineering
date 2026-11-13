package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class ScrewMechanicsTests {
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void screwHoldsWithoutPowerInEveryOrientation(GameTestHelper h) {
        var bodies = new java.util.ArrayList<ServerSubLevel>();
        var anchors = new java.util.ArrayList<Vector3d>();
        int index = 90;
        for (Direction axis : Direction.values()) for (Direction normal : Direction.values()) {
            if (axis.getAxis() == normal.getAxis()) continue;
            BlockPos pos = origin(h, index++);
            rail(h, pos, axis, normal);
            ServerSubLevel body = CarriagePlacement.place(h.getLevel(), pos);
            h.assertTrue(body != null, "Screw carriage placement refused: " + axis + "/" + normal);
            bodies.add(body); anchors.add(RailContact.anchor(null, pos));
        }
        h.runAfterDelay(15, () -> {
            for (var body : bodies) SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                    .addLinearAndAngularVelocity(body, new Vector3d(0, -1, 0), new Vector3d());
        });
        h.runAfterDelay(65, () -> {
            for (int i = 0; i < bodies.size(); i++) {
                var body = bodies.get(i);
                h.assertTrue(anchor(body).distance(anchors.get(i)) < .025, "Unpowered screw moved: " + i + " " + anchor(body).distance(anchors.get(i)));
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 280)
    public static void nativeShaftDrivesLineReversesAndStops(GameTestHelper h) {
        BlockPos pos = origin(h, 80);
        for (int i = 0; i <= 8; i++) {
            rail(h, pos.south(i), i % 2 == 0 ? Direction.SOUTH : Direction.NORTH, Direction.UP);
            // Detection changes must not reset the native kinetic network or drive joint.
            h.getLevel().setBlock(pos.south(i), h.getLevel().getBlockState(pos.south(i))
                    .setValue(LinearBlock.SENSOR, RailSensor.Mode.APPROXIMATE), 3);
        }
        var motor = motor(h, pos.north(2), Direction.SOUTH, 128);
        h.getLevel().setBlock(pos.north(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        ScrewMotionTests.awaitNetwork(h, java.util.stream.IntStream.rangeClosed(-2, 8).mapToObj(pos::south).toList(), 128, () -> {
            var body = CarriagePlacement.place(h.getLevel(), pos.south(2));
            Vector3d[] samples = new Vector3d[3];
            h.runAfterDelay(15, () -> {
                for (int i = 0; i <= 8; i++) h.assertTrue(Math.abs(((RailEntity)h.getLevel().getBlockEntity(pos.south(i))).getSpeed() - 128) < .01,
                        "Native rotation did not reach screw segment " + i);
                samples[0] = anchor(body);
            });
            h.runAfterDelay(60, () -> {
                samples[1] = anchor(body);
                h.assertTrue(Math.abs(samples[1].z - samples[0].z - 1.2) < .15, "Wrong pitch/speed: " + (samples[1].z - samples[0].z));
                motor.generatedSpeed.setValue(-128);
            });
            h.runAfterDelay(100, () -> {
                samples[2] = anchor(body);
                h.assertTrue(samples[2].z < samples[1].z - .75, "Reversing RPM did not reverse travel");
                motor.generatedSpeed.setValue(0);
            });
            h.runAfterDelay(130, () -> {
                h.assertTrue(anchor(body).distance(samples[2]) < .10, "Zero RPM did not hold position");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void poweredScrewLiftsAndHoldsPayload(GameTestHelper h) {
        BlockPos pos = origin(h, 81);
        for (int i = 0; i <= 7; i++) rail(h, pos.above(i), Direction.UP, Direction.NORTH);
        var motor = motor(h, pos.below(), Direction.UP, 128);
        var body = CarriagePlacement.place(h.getLevel(), pos.above(2));
        h.getLevel().setBlock(body.getPlot().getCenterBlock().north(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        Vector3d[] stopped = new Vector3d[1];
        h.runAfterDelay(65, () -> {
            h.assertTrue(anchor(body).y > pos.getY() + 3.5, "Screw did not lift against gravity: " + anchor(body));
            motor.generatedSpeed.setValue(0);
        });
        h.runAfterDelay(75, () -> stopped[0] = anchor(body));
        h.runAfterDelay(110, () -> {
            h.assertTrue(anchor(body).distance(stopped[0]) < .025, "Payload slid without RPM");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void passiveAndScrewProfilesDoNotCrossMount(GameTestHelper h) {
        BlockPos pos = origin(h, 82);
        rail(h, pos, Direction.SOUTH, Direction.UP);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var old = player.getMainHandItem().copy();
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(CreateLinearEngineering.CARRIAGE.get()));
            var context = new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
            h.assertTrue(player.getMainHandItem().useOn(context) == net.minecraft.world.InteractionResult.FAIL, "Plain carriage accepted screw rail");
        } finally { player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, old); }
        h.succeed();
    }

    private static CreativeMotorBlockEntity motor(GameTestHelper h, BlockPos pos, Direction direction, int rpm) {
        h.getLevel().setBlock(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, direction), 3);
        var motor = (CreativeMotorBlockEntity) h.getLevel().getBlockEntity(pos);
        motor.generatedSpeed.setValue(rpm);
        return motor;
    }
    private static void rail(GameTestHelper h, BlockPos pos, Direction axis, Direction normal) {
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        h.getLevel().setBlock(pos, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, normal), 3);
    }
    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
