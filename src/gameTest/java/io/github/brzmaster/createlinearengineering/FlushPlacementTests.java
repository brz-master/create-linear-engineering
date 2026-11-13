package io.github.brzmaster.createlinearengineering;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** A real end wall may stop a sleeve without pushing an already flush sleeve away. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class FlushPlacementTests {
    @GameTest(template = "empty", timeoutTicks = 75)
    public static void passiveSingleRailPlacementRemainsFlushWithItsEndWall(GameTestHelper h) {
        flush(h, false, 270);
    }

    @GameTest(template = "empty", timeoutTicks = 75)
    public static void screwSingleRailPlacementRemainsFlushWithItsEndWall(GameTestHelper h) {
        flush(h, true, 294);
    }

    private static void flush(GameTestHelper h, boolean screw, int firstIndex) {
        List<Sample> samples = new ArrayList<>();
        int mount = 0;
        for (Direction axis : Direction.values()) for (Direction normal : Direction.values()) {
            if (axis.getAxis() == normal.getAxis()) continue;
            BlockPos origin = new BlockPos((firstIndex + mount) * 48 + 8, 100, 8);
            h.getLevel().getChunk(origin);
            h.getLevel().setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
            // A free vertical carriage is supposed to fall. Put its wall below,
            // so gravity presses it against the wall rather than pulling it away.
            int wallSign = !screw && axis.getAxis() == Direction.Axis.Y
                    ? -axis.getAxisDirection().getStep() : mount % 2 == 0 ? 1 : -1;
            var state = (screw ? CreateLinearEngineering.SCREW_RAIL : CreateLinearEngineering.RAIL).get().defaultBlockState()
                    .setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, normal);
            h.getLevel().setBlock(origin, state, 3);
            h.getLevel().setBlock(origin.relative(axis, wallSign), Blocks.STONE.defaultBlockState(), 3);
            ServerSubLevel body = CarriagePlacement.place(h.getLevel(), origin);
            h.assertTrue(body != null, "Cannot place flush sleeve at " + axis + "/" + normal);
            BlockPos carriage = body.getPlot().getCenterBlock();
            Grease grease = Grease.values()[mount % Grease.values().length];
            h.getLevel().setBlock(carriage, h.getLevel().getBlockState(carriage).setValue(LinearBlock.GREASE, grease), 3);
            samples.add(new Sample(body, origin, axis, normal, grease, wallSign));
            mount++;
        }
        boolean[] active = {true};
        SableEventPlatform.INSTANCE.onPostPhysicsTick((physics, dt) -> {
            if (!active[0] || physics.getLevel() != h.getLevel()) return;
            for (Sample sample : samples) {
                if (sample.body.isRemoved()) continue;
                var point = RailContact.anchor(sample.body, sample.body.getPlot().getCenterBlock());
                double travel = point.sub(sample.origin.getX() + .5, sample.origin.getY() + .5, sample.origin.getZ() + .5)
                        .dot(sample.axis.getStepX(), sample.axis.getStepY(), sample.axis.getStepZ());
                sample.steps++;
                sample.maxDrift = Math.max(sample.maxDrift, Math.abs(travel));
                sample.maxGap = Math.max(sample.maxGap, -sample.wallSign * travel);
                sample.maxPenetration = Math.max(sample.maxPenetration, sample.wallSign * travel);
            }
        });
        h.runAfterDelay(40, () -> {
            active[0] = false;
            LogUtils.getLogger().info("Single-rail flush placement screw={} mounts={} peakDrift={}", screw, samples.size(),
                    samples.stream().mapToDouble(sample -> sample.maxDrift).max().orElseThrow());
            for (Sample sample : samples) {
                h.assertTrue(!sample.body.isRemoved() && sample.steps >= 30 && entity(sample.body).hasJoint(),
                        "Flush fixture lost its body/joint or native sampling: " + sample);
                h.assertTrue(sample.maxDrift <= 2 * RailGeometry.CONTACT_TOLERANCE,
                        "A wall expelled an already flush carriage from its aligned rail cell: " + sample);
            }
            h.succeed();
        });
    }

    private static final class Sample {
        private final ServerSubLevel body;
        private final BlockPos origin;
        private final Direction axis, normal;
        private final Grease grease;
        private final int wallSign;
        private int steps;
        private double maxDrift, maxGap, maxPenetration;
        private Sample(ServerSubLevel body, BlockPos origin, Direction axis, Direction normal, Grease grease, int wallSign) {
            this.body = body; this.origin = origin; this.axis = axis; this.normal = normal; this.grease = grease; this.wallSign = wallSign;
        }
        @Override public String toString() {
            return axis + "/" + normal + ", grease=" + grease + ", wallSign=" + wallSign + ", steps=" + steps
                    + ", peakDrift=" + maxDrift + ", peakGap=" + maxGap + ", peakPenetration=" + maxPenetration;
        }
    }

    private static CarriageEntity entity(ServerSubLevel body) {
        return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
    }
}
