package io.github.brzmaster.createlinearengineering;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class RailSensorProtectionTests {
    private record Fixture(BlockPos rail, BlockPos world, FakePlayer player) {}

    @GameTest(template = "empty")
    public static void sensorChangesEmitOneProjectedEventOnRotatedParent(GameTestHelper h) {
        Fixture fixture = fixture(h, 150);
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getEntity() != fixture.player()) return;
            events[0]++;
            h.assertTrue(event.getPos().equals(fixture.world()), "Sensor event exposed plot coordinates: " + event.getPos());
            h.assertTrue(event.getPlacedBlock().is(CreateLinearEngineering.SCREW_RAIL.get()), "Unexpected neighbor placement event");
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            ItemStack stack = new ItemStack(CreateLinearEngineering.SENSOR.get(), 2);
            fixture.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
            h.assertTrue(click(h, fixture).consumesAction(), "Projected sensor installation was rejected");
            h.assertTrue(events[0] == 1 && stack.getCount() == 1, "Installation duplicated event or consumption: " + events[0]);
            fixture.player().setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
            fixture.player().setShiftKeyDown(true);
            h.assertTrue(click(h, fixture).consumesAction(), "Projected mode change was rejected");
            h.assertTrue(events[0] == 2, "Mode change must emit exactly one projected event: " + events[0]);
            h.assertTrue(h.getLevel().getBlockState(fixture.rail()).getValue(LinearBlock.SENSOR) == RailSensor.Mode.PRECISE,
                    "Authorized mode change did not persist");
            h.assertTrue(!h.getLevel().captureBlockSnapshots && h.getLevel().capturedBlockSnapshots.isEmpty(),
                    "Sensor left snapshot capture or entries behind");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            fixture.player().setShiftKeyDown(false);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledSensorInstallationPreservesRailAndStack(GameTestHelper h) {
        Fixture fixture = fixture(h, 151);
        var before = h.getLevel().getBlockState(fixture.rail());
        ItemStack stack = new ItemStack(CreateLinearEngineering.SENSOR.get(), 3);
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = cancelAtWorld(h, fixture, events);
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(!click(h, fixture).consumesAction(), "Canceled installation reported success");
            h.assertTrue(events[0] == 1, "Canceled installation emitted wrong event count");
            h.assertTrue(stack.getCount() == 3 && h.getLevel().getBlockState(fixture.rail()) == before,
                    "Canceled installation changed stack or rail state");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledSensorModeChangePreservesModeAndPower(GameTestHelper h) {
        Fixture fixture = fixture(h, 152);
        var before = h.getLevel().getBlockState(fixture.rail())
                .setValue(LinearBlock.SENSOR, RailSensor.Mode.APPROXIMATE).setValue(LinearBlock.POWERED, true);
        h.getLevel().setBlock(fixture.rail(), before, 3);
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
        fixture.player().setShiftKeyDown(true);
        int[] events = {0};
        Consumer<BlockEvent.EntityPlaceEvent> listener = cancelAtWorld(h, fixture, events);
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(!click(h, fixture).consumesAction(), "Canceled mode change reported success");
            h.assertTrue(events[0] == 1, "Canceled mode change emitted wrong event count");
            h.assertTrue(h.getLevel().getBlockState(fixture.rail()) == before,
                    "Canceled mode change modified mode or powered state");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            fixture.player().setShiftKeyDown(false);
        }
        h.succeed();
    }

    private static Consumer<BlockEvent.EntityPlaceEvent> cancelAtWorld(GameTestHelper h, Fixture fixture, int[] events) {
        return event -> {
            if (event.getEntity() != fixture.player()) return;
            events[0]++;
            h.assertTrue(event.getPos().equals(fixture.world()), "Protection event uses storage coordinates");
            event.setCanceled(true);
        };
    }

    private static Fixture fixture(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        h.getLevel().setBlock(pos, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), 3);
        var parent = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        BlockPos rail = parent.getPlot().getCenterBlock();
        var anchor = RailContact.anchor(parent, rail);
        parent.logicalPose().orientation().rotationY(Math.PI / 4);
        parent.logicalPose().position().add(anchor.sub(RailContact.anchor(parent, rail)));
        SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .teleport(parent, parent.logicalPose().position(), parent.logicalPose().orientation());
        parent.updateLastPose();
        var point = RailContact.anchor(parent, rail);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SensorTest"));
        player.setGameMode(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        return new Fixture(rail, BlockPos.containing(point.x, point.y, point.z), player);
    }

    private static net.minecraft.world.InteractionResult click(GameTestHelper h, Fixture fixture) {
        return fixture.player().gameMode.useItemOn(fixture.player(), h.getLevel(), fixture.player().getMainHandItem(),
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(fixture.rail()), Direction.UP, fixture.rail(), false));
    }
}
