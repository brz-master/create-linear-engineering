package io.github.brzmaster.createlinearengineering;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Repeatedly cross rail seams on ten independent full-length sleeves. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class FullLengthSlidingTests {
    @GameTest(template = "empty", timeoutTicks = 85)
    public static void tenLubricatedSleevesSlideAcrossSeamsOnBothHorizontalAxes(GameTestHelper h) {
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Direction axis = i % 2 == 0 ? Direction.EAST : Direction.SOUTH;
            BlockPos origin = new BlockPos((250 + i) * 48 + 8, 100, 8);
            for (int offset = -2; offset <= 25; offset++) {
                BlockPos pos = origin.relative(axis, offset);
                h.getLevel().getChunk(pos);
                h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState()
                        .setValue(LinearBlock.FACING, offset % 2 == 0 ? axis : axis.getOpposite()), 3);
            }
            ServerSubLevel body = CarriagePlacement.place(h.getLevel(), origin);
            h.assertTrue(body != null, "Cannot create full-length seam fixture " + i);
            BlockPos carriage = body.getPlot().getCenterBlock();
            h.getLevel().setBlock(carriage, h.getLevel().getBlockState(carriage).setValue(LinearBlock.GREASE, Grease.LOW), 3);
            fixtures.add(new Fixture(body, axis));
        }
        double[] starts = new double[fixtures.size()];
        h.runAfterDelay(5, () -> kick(h, fixtures, starts));
        h.runAfterDelay(20, () -> assertMoved(h, fixtures, starts, "first"));
        h.runAfterDelay(35, () -> kick(h, fixtures, starts));
        h.runAfterDelay(50, () -> {
            assertMoved(h, fixtures, starts, "second");
            var pipeline = SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline();
            for (Fixture fixture : fixtures) pipeline.resetVelocity(fixture.body());
            h.succeed();
        });
    }

    private record Fixture(ServerSubLevel body, Direction axis) {}

    private static void kick(GameTestHelper h, List<Fixture> fixtures, double[] starts) {
        var physics = SubLevelContainer.getContainer(h.getLevel()).physicsSystem();
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture fixture = fixtures.get(i);
            h.assertTrue(entity(fixture).hasJoint(), "Seam fixture lost its guiding joint before an impulse: " + i);
            starts[i] = position(fixture);
            Vector3d target = new Vector3d(fixture.axis().getStepX(), 0, fixture.axis().getStepZ()).mul(4);
            physics.getPipeline().addLinearAndAngularVelocity(fixture.body(),
                    target.sub(physics.getPhysicsHandle(fixture.body()).getLinearVelocity(new Vector3d())), new Vector3d());
        }
    }

    private static void assertMoved(GameTestHelper h, List<Fixture> fixtures, double[] starts, String impulse) {
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture fixture = fixtures.get(i);
            double travel = position(fixture) - starts[i];
            h.assertTrue(!fixture.body().isRemoved() && entity(fixture).hasJoint() && travel > 1,
                    "Full-length sleeve caught a native rail seam after " + impulse + " impulse: fixture=" + i
                            + ", axis=" + fixture.axis() + ", travel=" + travel + ", state=" + entity(fixture).getBlockState());
        }
    }

    private static CarriageEntity entity(Fixture fixture) {
        return (CarriageEntity) fixture.body().getLevel().getBlockEntity(fixture.body().getPlot().getCenterBlock());
    }
    private static double position(Fixture fixture) {
        Vector3d point = RailContact.anchor(fixture.body(), fixture.body().getPlot().getCenterBlock());
        return point.dot(fixture.axis().getStepX(), 0, fixture.axis().getStepZ());
    }
}
