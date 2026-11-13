package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

/** Real 32 RPM hand-crank networks, Sable mass and dynamically moving load owners. */
@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class ScrewStressTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private record Track(BlockPos start, List<RailEntity> rails, HandCrankBlockEntity crank) {}

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void emptyTrackIsFreeAndPayloadRemovalRecoversOverstressedCrank(GameTestHelper h) {
        Track track = track(h, 230, 12);
        ServerSubLevel[] body = {null};
        List<BlockPos> payload = new ArrayList<>();
        h.runAfterDelay(20, () -> {
            poweredCrank(h, track);
            h.assertTrue(sum(track) == 0 && track.crank().getOrCreateNetwork().calculateStress() == 0,
                    "Empty screw rails consume stress");
            body[0] = CarriagePlacement.place(h.getLevel(), track.start().south(2));
            h.assertTrue(body[0] != null, "Cannot create stress carriage");
        });
        h.runAfterDelay(40, () -> {
            double expected = .25 + body[0].getMassTracker().getMass() / 128;
            near(h, sum(track), expected, .01, "Empty-carriage stress does not use native Sable mass");
            h.assertTrue(!load(body[0]).isOverStressed() && Math.abs(load(body[0]).getSpeed()) == 32,
                    "A hand crank cannot drive an unloaded carriage");
            BlockPos center = body[0].getPlot().getCenterBlock();
            for (int x = -3; x <= 4; x++) for (int y = 1; y <= 5; y++) for (int z = -3; z <= 4; z++) {
                BlockPos pos = center.offset(x, y, z);
                h.getLevel().setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                payload.add(pos);
            }
        });
        h.runAfterDelay(70, () -> {
            double mass = body[0].getMassTracker().getMass();
            h.assertTrue(mass > 992, "Heavy payload did not exceed the crank's native mass budget: " + mass);
            near(h, sum(track), .25 + mass / 128, .01, "Added payload did not refresh network stress");
            h.assertTrue(load(body[0]).isOverStressed() && load(body[0]).getSpeed() == 0,
                    "Native hand-crank network did not become overstressed");
            h.assertTrue(entity(body[0]).hasJoint(), "Overstress detached the screw carriage");
            LOGGER.info("Heavy native hand-crank fixture: mass={}, impact={}, capacity={}",
                    mass, sum(track), track.crank().getOrCreateNetwork().calculateCapacity());
            for (BlockPos pos : payload) h.getLevel().removeBlock(pos, false);
        });
        h.runAfterDelay(105, () -> {
            near(h, sum(track), .25 + body[0].getMassTracker().getMass() / 128, .01,
                    "Removing payload did not refresh a stopped kinetic network");
            h.assertTrue(!load(body[0]).isOverStressed() && Math.abs(load(body[0]).getSpeed()) == 32,
                    "Removing payload did not recover the overstressed crank");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void greaseChangesRealKineticLoadWithoutChangingScrewPitch(GameTestHelper h) {
        Track track = track(h, 231, 12);
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), track.start().south(2));
        h.assertTrue(body != null, "Cannot create grease stress carriage");
        BlockPos carriage = body.getPlot().getCenterBlock();
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++)
            h.getLevel().setBlock(carriage.offset(x, y, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        double[] dry = {0}, start = {0};
        h.runAfterDelay(25, () -> {
            poweredCrank(h, track);
            dry[0] = track.crank().getOrCreateNetwork().calculateStress();
            h.assertTrue(dry[0] > 0, "Dry fixture generated no native stress");
            start[0] = anchor(body).z;
            finish(h, body, Grease.HIGH);
        });
        h.runAfterDelay(45, () -> {
            near(h, track.crank().getOrCreateNetwork().calculateStress(), dry[0] / 4, .02,
                    "High grease did not reduce the real network load by four");
            h.assertTrue(anchor(body).z > start[0] + .08, "Grease stalled an otherwise powered screw");
            start[0] = anchor(body).z;
            finish(h, body, Grease.LOW);
        });
        h.runAfterDelay(65, () -> {
            near(h, track.crank().getOrCreateNetwork().calculateStress(), dry[0] / 16, .02,
                    "Low grease did not reduce the real network load by sixteen");
            double travel = anchor(body).z - start[0];
            near(h, travel, 32 * ScrewDrive.PITCH / 60, .035, "Lubrication changed the screw's RPM-to-travel ratio");
            finish(h, body, Grease.NONE);
        });
        h.runAfterDelay(85, () -> {
            near(h, track.crank().getOrCreateNetwork().calculateStress(), dry[0], .02,
                    "Dry state did not restore network load");
            h.assertTrue(entity(body).hasJoint(), "Lubrication changed the screw's guide ownership");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void coupledCarriagesShareNativeBodyMassExactlyOnce(GameTestHelper h) {
        Track track = track(h, 232, 12);
        ServerSubLevel a = CarriagePlacement.place(h.getLevel(), track.start().south(2));
        ServerSubLevel b = CarriagePlacement.place(h.getLevel(), track.start().south(3));
        h.assertTrue(a != null && b != null, "Cannot create adjacent full-length screw carriages");
        String result = CarriageCoupling.merge(h.getLevel(), a, b, null);
        h.assertTrue("couple_success".equals(result), "Touching full-length carriages did not merge: " + result);
        BlockPos center = a.getPlot().getCenterBlock();
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++)
            h.getLevel().setBlock(center.offset(x, y, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        h.runAfterDelay(30, () -> {
            h.assertTrue(b.isRemoved(), "Coupled body was not removed");
            List<CarriageEntity> carriages = new ArrayList<>();
            for (var actor : a.getPlot().getBlockEntityActors())
                if (actor instanceof CarriageEntity carriage) carriages.add(carriage);
            h.assertTrue(carriages.size() == 2 && carriages.stream().allMatch(CarriageEntity::hasJoint),
                    "Merged fixture lost one of its guiding joints");
            double mass = a.getMassTracker().getMass();
            double total = sum(track);
            near(h, total, .5 + mass / 128, .01, "Two carriages counted the same payload mass twice");
            for (CarriageEntity carriage : carriages)
                near(h, carriage.loadRail().calculateStressApplied(), .25 + mass / 256, .01,
                        "Mass was not shared equally between the two active screw supports");
            near(h, track.crank().getOrCreateNetwork().calculateStress(), total * 32, .03,
                    "Shared mass did not reach the actual Create network");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void movingAcrossGapTransfersLoadToAnotherKineticNetwork(GameTestHelper h) {
        BlockPos start = origin(h, 233);
        Track first = track(h, start, 4, false);
        Track second = track(h, start.south(5), 5, true);
        ServerSubLevel body = CarriagePlacement.place(h.getLevel(), start.south(3));
        h.assertTrue(body != null, "Cannot create gap handoff carriage");
        h.runAfterDelay(25, () -> {
            h.assertTrue(!first.crank().getOrCreateNetwork().id.equals(second.crank().getOrCreateNetwork().id),
                    "Gap fixture accidentally connected both kinetic networks");
            h.assertTrue(sum(first) > 0 && sum(second) == 0, "Initial load is not confined to the first network");
        });
        h.runAfterDelay(235, () -> {
            h.assertTrue(entity(body).hasJoint() && load(body) != null, "Carriage did not acquire the far rail after the gap");
            h.assertTrue(second.rails().contains(load(body)), "Load remained owned by the original anchor rail");
            near(h, sum(first), 0, .001, "Old support retained phantom load after handoff");
            near(h, sum(second), .25 + body.getMassTracker().getMass() / 128, .01,
                    "Destination network lost or duplicated the arriving body mass");
            near(h, first.crank().getOrCreateNetwork().calculateStress(), 0, .01,
                    "Old Create network retained phantom stress");
            h.assertTrue(second.crank().getOrCreateNetwork().calculateStress() > 0,
                    "Far Create network never received the carriage load");
            h.succeed();
        });
    }

    private static Track track(GameTestHelper h, int index, int length) {
        return track(h, origin(h, index), length, false);
    }
    private static Track track(GameTestHelper h, BlockPos start, int length, boolean crankAtEnd) {
        List<RailEntity> rails = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.south(i);
            h.getLevel().getChunk(pos);
            h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            h.getLevel().setBlock(pos, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
            rails.add((RailEntity) h.getLevel().getBlockEntity(pos));
        }
        BlockPos crankPos = crankAtEnd ? start.south(length) : start.north();
        h.getLevel().setBlock(crankPos, AllBlocks.HAND_CRANK.getDefaultState()
                .setValue(BlockStateProperties.FACING, crankAtEnd ? Direction.SOUTH : Direction.NORTH), 3);
        HandCrankBlockEntity crank = (HandCrankBlockEntity) h.getLevel().getBlockEntity(crankPos);
        h.assertTrue(crank != null, "Cannot create a native hand crank");
        crank.turn(!crankAtEnd);
        for (int tick = 5; tick <= 280; tick += 5) h.runAfterDelay(tick, () -> crank.turn(!crankAtEnd));
        return new Track(start, List.copyOf(rails), crank);
    }
    private static void poweredCrank(GameTestHelper h, Track track) {
        near(h, track.crank().calculateAddedStressCapacity(), 8, .001, "Fixture is not a standard finite-capacity hand crank");
        near(h, Math.abs(track.crank().getGeneratedSpeed()), 32, .001, "Hand crank does not generate native 32 RPM");
        near(h, Math.abs(track.rails().getFirst().getSpeed()), 32, .001, "Native rotation did not reach the track");
    }
    private static double sum(Track track) {
        return track.rails().stream().mapToDouble(RailEntity::calculateStressApplied).sum();
    }
    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String message) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, message + ": actual=" + actual + ", expected=" + expected);
    }
    private static void finish(GameTestHelper h, ServerSubLevel body, Grease grease) {
        BlockPos pos = body.getPlot().getCenterBlock();
        h.getLevel().setBlock(pos, h.getLevel().getBlockState(pos).setValue(LinearBlock.GREASE, grease), 3);
    }
    private static RailEntity load(ServerSubLevel body) { return entity(body).loadRail(); }
    private static CarriageEntity entity(ServerSubLevel body) {
        return (CarriageEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static Vector3d anchor(ServerSubLevel body) { return RailContact.anchor(body, body.getPlot().getCenterBlock()); }
    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
