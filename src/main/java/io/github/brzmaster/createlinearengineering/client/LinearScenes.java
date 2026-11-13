package io.github.brzmaster.createlinearengineering.client;

import com.simibubi.create.AllItems;
import io.github.brzmaster.createlinearengineering.CreateLinearEngineering;
import io.github.brzmaster.createlinearengineering.LinearBlock;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Scripted demonstrations using Ponder's native independent world sections. */
public final class LinearScenes {
    private LinearScenes() {}
    private static void start(SceneBuilder scene, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.85f);
        scene.showBasePlate();
        scene.idle(10);
    }
    private static BlockState rail(Direction axis, Direction normal) {
        return CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, normal);
    }
    private static BlockState carriage(Direction axis, Direction normal) {
        return CreateLinearEngineering.CARRIAGE.get().defaultBlockState().setValue(LinearBlock.FACING, axis).setValue(LinearBlock.NORMAL, normal);
    }
    private static void text(SceneBuilder scene, String message, Vec3 point) {
        scene.overlay().showText(85).text(message).pointAt(point).placeNearTarget().attachKeyFrame();
        scene.idle(90);
    }
    private static void track(SceneBuilder scene, SceneBuildingUtil util) {
        scene.world().setBlocks(util.select().fromTo(3, 1, 1, 3, 1, 5), rail(Direction.SOUTH, Direction.UP), false);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 3, 1, 5), Direction.DOWN);
    }
    private static ElementLink<WorldSectionElement> mount(SceneBuilder scene, SceneBuildingUtil util) {
        // The two complementary blocks share a cell in game. A native independent
        // section gives the preview that same overlap without replacing the rail.
        scene.world().setBlock(new BlockPos(3, 2, 2), carriage(Direction.SOUTH, Direction.UP), false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(3, 2, 2));
        scene.world().moveSection(body, new Vec3(0, -1, 0), 0);
        return body;
    }
    public static void grease(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "grease", "Lubrication and load"); track(scene, util);
        var body = mount(scene, util);
        var pos = new BlockPos(3, 2, 2);
        var target = new Vec3(3.5, 1.8, 2.5);
        text(scene, "A dry free carriage brakes strongly. Lubrication changes its inner sliding surfaces and remains until replaced.", target);
        scene.overlay().showControls(target, Pointing.DOWN, 45).rightClick().withItem(new ItemStack(CreateLinearEngineering.HIGH_GREASE.get()));
        scene.world().modifyBlock(pos, state -> state.setValue(LinearBlock.GREASE, io.github.brzmaster.createlinearengineering.Grease.HIGH), false);
        scene.world().moveSection(body, new Vec3(0, 0, .5), 45);
        text(scene, "High Viscosity Grease halves the added brake. Amber surfaces identify the controlled sliding mode.", target);
        scene.overlay().showControls(target, Pointing.DOWN, 45).rightClick().withItem(new ItemStack(CreateLinearEngineering.LOW_GREASE.get()));
        scene.world().modifyBlock(pos, state -> state.setValue(LinearBlock.GREASE, io.github.brzmaster.createlinearengineering.Grease.LOW), false);
        scene.world().moveSection(body, new Vec3(0, 0, 1.5), 90);
        text(scene, "Low Viscosity Grease removes the added brake. Pale surfaces identify free sliding; Sable still handles contacts and drag.", new Vec3(3.5, 1.8, 4.5));
        text(scene, "On screws, High reduces stress to one quarter and Low to one sixteenth. More carried mass needs more stress; an empty rail costs none.", target);
        text(scene, "Even lubricated, a screw carriage holds at zero RPM. Linked carriages share their body's mass instead of counting it twice.", target);
    }
    public static void horizontal(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "horizontal", "Placing a horizontal guide");
        scene.world().setBlocks(util.select().fromTo(3, 1, 1, 3, 1, 5), rail(Direction.SOUTH, Direction.UP), false);
        scene.overlay().showControls(new Vec3(3.5, 1, 1.5), Pointing.DOWN, 45).rightClick().withItem(new ItemStack(CreateLinearEngineering.RAIL.get()));
        scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
        text(scene, "Click a surface with the Linear Rail. Its base follows the face you click.", new Vec3(3.5, 1.2, 1.5));
        for (int z = 2; z <= 5; z++) {
            scene.world().showSection(util.select().position(3, 1, z), Direction.NORTH);
            scene.idle(8);
        }
        text(scene, "Aim at the existing guide: Create's arrow and preview extend it with matching orientation. Hold Shift to bypass assistance.", new Vec3(3.5, 1.5, 4.5));
    }
    public static void carriage(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "carriage", "Installing the carriage"); track(scene, util);
        scene.overlay().showControls(new Vec3(3.5, 1.9, 2.5), Pointing.DOWN, 50).rightClick().withItem(new ItemStack(CreateLinearEngineering.CARRIAGE.get()));
        mount(scene, util);
        text(scene, "Click the rail with a Linear Carriage. It appears already fitted around the T profile, as a physical Sable body.", new Vec3(3.5, 1.8, 2.5));
        text(scene, "Carriages can only be installed on rails. Add your payload directly to the moving body.", new Vec3(3.5, 1.8, 2.5));
    }
    public static void physics(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "physics", "A real physical body"); track(scene, util);
        var body = mount(scene, util);
        text(scene, "A level guide does not drive itself. Sable handles mass, gravity and collisions; no rotational power is required.", new Vec3(3.5, 1.8, 2.5));
        scene.world().moveSection(body, new Vec3(0, 0, 2), 45);
        text(scene, "An external force can slide the body along the guide. The joint prevents sideways motion and rotation.", new Vec3(3.5, 1.8, 4.5));
        scene.world().moveSection(body, new Vec3(0, 0, 2.5), 35);
        scene.idle(35);
        scene.world().moveSection(body, new Vec3(0, -2, 0), 20);
        text(scene, "There is no invisible end stop: once fully off the rail, the body is free. Use a solid block as a physical stop.", new Vec3(3.5, 1.2, 5.5));
    }
    public static void vertical(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "vertical", "Gravity on a vertical guide");
        scene.world().setBlocks(util.select().fromTo(3, 1, 3, 3, 5, 3), rail(Direction.UP, Direction.NORTH), false);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 5, 3), Direction.SOUTH);
        scene.world().setBlock(new BlockPos(3, 4, 2), carriage(Direction.UP, Direction.NORTH), false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(3, 4, 2));
        scene.world().moveSection(body, new Vec3(0, 0, 1), 0);
        text(scene, "On a vertical guide, an unsupported carriage slides down under its own weight.", new Vec3(3.5, 4.5, 3));
        scene.world().moveSection(body, new Vec3(0, -3, 0), 35);
        text(scene, "A solid block at the bottom stops it by collision. Without support it can leave the guide.", new Vec3(3.5, 1.5, 3));
    }
    public static void orientations(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "orientations", "Floor, ceiling, walls and moving structures");
        scene.world().setBlocks(util.select().fromTo(1, 1, 1, 1, 1, 5), rail(Direction.SOUTH, Direction.UP), false);
        scene.world().showSection(util.select().fromTo(1, 1, 1, 1, 1, 5), Direction.DOWN);
        text(scene, "The clicked face determines the base. Adjacent rails preserve the orientation of the line.", new Vec3(1.5, 1.3, 3.5));
        scene.world().setBlocks(util.select().fromTo(3, 5, 1, 3, 5, 5), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlocks(util.select().fromTo(3, 4, 1, 3, 4, 5), rail(Direction.SOUTH, Direction.DOWN), false);
        scene.world().showSection(util.select().fromTo(3, 4, 1, 3, 5, 5), Direction.UP);
        text(scene, "Click the underside of a ceiling to hang the rail with its profile pointing down: ideal for sliding doors.", new Vec3(3.5, 4.5, 3.5));
        scene.world().setBlocks(util.select().fromTo(5, 1, 3, 5, 4, 3), rail(Direction.UP, Direction.WEST), false);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 4, 3), Direction.EAST);
        text(scene, "Wall mounting also works. The same mechanism works on moving or rotated Sable bodies, following their local axes.", new Vec3(5.2, 3, 3.5));
    }
    public static void coupling(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "coupling", "One rigid door on several guides"); track(scene, util);
        scene.world().setBlocks(util.select().fromTo(3, 4, 1, 3, 4, 5), rail(Direction.SOUTH, Direction.DOWN), false);
        scene.world().showSection(util.select().fromTo(3, 4, 1, 3, 4, 5), Direction.UP);
        scene.world().setBlock(new BlockPos(5, 1, 2), carriage(Direction.SOUTH, Direction.UP), false);
        scene.world().setBlock(new BlockPos(5, 4, 2), carriage(Direction.SOUTH, Direction.DOWN), false);
        var lower = scene.world().showIndependentSectionImmediately(util.select().position(5, 1, 2));
        var upper = scene.world().showIndependentSectionImmediately(util.select().position(5, 4, 2));
        scene.world().moveSection(lower, new Vec3(-2, 0, 0), 0);
        scene.world().moveSection(upper, new Vec3(-2, 0, 0), 0);
        scene.world().setBlocks(util.select().fromTo(5, 2, 2, 5, 3, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        var door = scene.world().showIndependentSectionImmediately(util.select().fromTo(5, 2, 2, 5, 3, 2));
        scene.world().moveSection(door, new Vec3(-2, 0, 0), 0);
        text(scene, "Build the door between the carriages so the bodies touch by a face. Align them on the same block grid without overlaps.", new Vec3(3.5, 2.8, 2.5));
        scene.overlay().showControls(new Vec3(3.5, 1.7, 2.5), Pointing.DOWN, 40).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(45);
        scene.overlay().showControls(new Vec3(3.5, 4.7, 2.5), Pointing.DOWN, 40).rightClick().withItem(AllItems.WRENCH.asStack());
        text(scene, "Click the first carriage, then the second with Create's wrench. Both become one rigid body, retaining their rail joints.", new Vec3(3.5, 3, 2.5));
        for (var part : java.util.List.of(lower, upper, door)) scene.world().moveSection(part, new Vec3(0, 0, 2), 40);
        text(scene, "You can add more carriages: there is no fixed limit of two. Each merge moves at most 2048 blocks; all guides must allow the same motion.", new Vec3(3.5, 3, 4.5));
        text(scene, "Shift-right-click a rail or carriage with the wrench to cancel selection. Breaking the physical connection lets Sable separate the parts again.", new Vec3(3.5, 2.5, 4.5));
    }
    public static void reentry(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "reentry", "Leaving and re-entering a rail"); track(scene, util);
        scene.world().setBlock(new BlockPos(3, 4, 3), carriage(Direction.SOUTH, Direction.UP), false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(3, 4, 3));
        text(scene, "A detached carriage remains a physical body. Bring it back to a rail with matching axis and mounting face.", new Vec3(3.5, 4.7, 3.5));
        scene.world().moveSection(body, new Vec3(0, -3, 0), 35);
        text(scene, "When the profiles meet in alignment, the joint reconnects automatically. It also works on moving rails.", new Vec3(3.5, 1.8, 3.5));
        scene.world().moveSection(body, new Vec3(0, 0, 1), 35);
        text(scene, "It does not attract distant carriages or correct a sideways or upside-down approach.", new Vec3(3.5, 1.8, 4.5));
    }
}
