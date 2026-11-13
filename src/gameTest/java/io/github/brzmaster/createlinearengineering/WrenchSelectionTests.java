package io.github.brzmaster.createlinearengineering;

import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder(CreateLinearEngineering.ID)
@PrefixGameTestTemplate(false)
public final class WrenchSelectionTests {
    @GameTest(template = "empty")
    public static void shiftWrenchCancelsThroughRealInteraction(GameTestHelper h) {
        ServerSubLevel body = body(h, origin(h, 43));
        FakePlayer player = player(h);
        ItemStack previous = player.getMainHandItem();
        ItemStack wrench = AllItems.WRENCH.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        try {
            BlockPos carriage = body.getPlot().getCenterBlock();
            click(h, player, carriage, false);
            h.assertTrue(selection(wrench).hasUUID("Body"), "Normal wrench click did not select through item dispatch");
            click(h, player, carriage, true);
            h.assertTrue(selection(wrench).isEmpty(), "Shift wrench did not clear through Create IWrenchable");
            h.assertTrue(!body.isRemoved() && h.getLevel().getBlockEntity(carriage) instanceof CarriageEntity,
                    "Cancel dismantled carriage or body");
            click(h, player, carriage, false);
            h.assertTrue(selection(wrench).hasUUID("Body"), "Could not start again after cancel");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void brokenSelectedCarriageStartsFreshSelection(GameTestHelper h) {
        BlockPos rail = origin(h, 44);
        ServerSubLevel old = body(h, rail), fresh = body(h, rail.above(4));
        BlockPos oldCarriage = old.getPlot().getCenterBlock(), freshCarriage = fresh.getPlot().getCenterBlock();
        h.getLevel().setBlock(oldCarriage.above(), Blocks.STONE.defaultBlockState(), 3);
        FakePlayer player = player(h);
        ItemStack previous = player.getMainHandItem();
        ItemStack wrench = AllItems.WRENCH.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        try {
            click(h, player, oldCarriage, false);
            h.assertTrue(selection(wrench).getUUID("Body").equals(old.getUniqueId()), "Initial selection missing");
            h.getLevel().setBlock(oldCarriage, Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(!old.isRemoved(), "Fixture must retain old payload body");
            click(h, player, freshCarriage, false);
            h.assertTrue(selection(wrench).getUUID("Body").equals(fresh.getUniqueId()),
                    "Wrench kept stale body after selected carriage was broken");
            h.assertTrue(!fresh.isRemoved() && h.getLevel().getBlockState(oldCarriage.above()).is(Blocks.STONE),
                    "Fresh selection unexpectedly merged or changed payload");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void threeCarriagesJoinOneSlidingBody(GameTestHelper h) {
        BlockPos rail = origin(h, 45);
        for (int height : new int[]{0, 3, 6}) {
            for (int along = -2; along <= 2; along++) {
                h.getLevel().setBlock(rail.above(height).south(along), CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
            }
        }
        ServerSubLevel first = CarriagePlacement.place(h.getLevel(), rail);
        ServerSubLevel second = CarriagePlacement.place(h.getLevel(), rail.above(3));
        ServerSubLevel third = CarriagePlacement.place(h.getLevel(), rail.above(6));
        BlockPos base = first.getPlot().getCenterBlock();
        h.getLevel().setBlock(base.above(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 3);
        FakePlayer player = player(h);
        ItemStack previous = player.getMainHandItem();
        ItemStack wrench = AllItems.WRENCH.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        try {
            click(h, player, base, false);
            click(h, player, second.getPlot().getCenterBlock(), false);
            h.assertTrue(second.isRemoved() && selection(wrench).isEmpty(), "First merge did not finish and clear selection");
            h.getLevel().setBlock(base.above(4), Blocks.STONE.defaultBlockState(), 3);
            h.getLevel().setBlock(base.above(5), Blocks.STONE.defaultBlockState(), 3);
            click(h, player, base.above(3), false);
            click(h, player, third.getPlot().getCenterBlock(), false);
            h.assertTrue(third.isRemoved() && selection(wrench).isEmpty(), "Could not merge a third carriage");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
        for (int height : new int[]{0, 3, 6}) {
            BlockPos pos = base.above(height);
            h.getLevel().setBlock(pos, h.getLevel().getBlockState(pos).setValue(LinearBlock.GREASE, Grease.LOW), 3);
        }
        h.runAfterDelay(8, () -> SubLevelContainer.getContainer(h.getLevel()).physicsSystem().getPipeline()
                .addLinearAndAngularVelocity(first, new Vector3d(0, 0, 2), new Vector3d()));
        h.runAfterDelay(25, () -> {
            h.assertTrue(!first.isRemoved(), "Merged body was removed");
            for (int height : new int[]{0, 3, 6}) {
                var entity = h.getLevel().getBlockEntity(base.above(height));
                h.assertTrue(entity instanceof CarriageEntity carriage && carriage.hasJoint(),
                        "Merged carriage lost its guide at height " + height);
            }
            h.assertTrue(RailContact.anchor(first, base).z > rail.getZ() + .8, "Three-guide body cannot slide");
            h.succeed();
        });
    }

    private static void click(GameTestHelper h, FakePlayer player, BlockPos pos, boolean shift) {
        player.setShiftKeyDown(shift);
        var result = player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        h.assertTrue(result.consumesAction(), "Wrench interaction was not handled");
    }

    private static CompoundTag selection(ItemStack wrench) {
        return wrench.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("create_linear_engineering_selection");
    }

    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.CREATIVE);
        player.setShiftKeyDown(false);
        return player;
    }

    private static ServerSubLevel body(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlock(pos, CreateLinearEngineering.RAIL.get().defaultBlockState(), 3);
        return CarriagePlacement.place(h.getLevel(), pos);
    }

    private static BlockPos origin(GameTestHelper h, int index) {
        BlockPos pos = new BlockPos(index * 48 + 8, 100, 8);
        h.getLevel().getChunk(pos);
        h.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        return pos;
    }
}
