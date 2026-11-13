package io.github.brzmaster.createlinearengineering.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import io.github.brzmaster.createlinearengineering.CreateLinearEngineering;
import io.github.brzmaster.createlinearengineering.LinearBlock;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class ScrewScenes {
    private ScrewScenes() {}
    public static void drive(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("screw_drive", "Turning rotation into linear motion");
        scene.configureBasePlate(0, 0, 7); scene.scaleSceneView(.85f); scene.showBasePlate();
        var rails = util.select().fromTo(3, 1, 2, 3, 1, 5);
        scene.world().setBlocks(rails, CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(3, 1, 1), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), false);
        scene.world().setBlock(new BlockPos(3, 1, 0), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH), false);
        scene.world().showSection(util.select().fromTo(3, 1, 0, 3, 1, 5), Direction.DOWN);
        scene.world().setBlock(new BlockPos(3, 2, 3), CreateLinearEngineering.SCREW_CARRIAGE.get().defaultBlockState(), false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(3, 2, 3));
        scene.world().moveSection(body, new Vec3(0, -1, 0), 0);
        scene.world().setKineticSpeed(util.select().fromTo(3, 1, 0, 3, 1, 5), 0);
        text(scene, "Install a toothed carriage on a screw rail. Unlike the free carriage, it holds its position without rotation.", new Vec3(3.5, 1.8, 3.5));
        scene.world().setKineticSpeed(util.select().fromTo(3, 1, 0, 3, 1, 5), 64);
        scene.world().moveSection(body, new Vec3(0, 0, 1.2), 90);
        text(scene, "Connect a Create shaft at either end. The native kinetic network rotates every connected screw segment and drives the carriage.", new Vec3(3.5, 1.5, 1.5));
        scene.world().setKineticSpeed(util.select().fromTo(3, 1, 0, 3, 1, 5), -64);
        scene.world().moveSection(body, new Vec3(0, 0, -1.2), 90);
        text(scene, "Reverse the RPM to reverse travel. One revolution advances one quarter block; the fixed base does not rotate.", new Vec3(3.5, 1.8, 3.5));
    }
    public static void lift(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("screw_lift", "Lifting and holding a load");
        scene.configureBasePlate(0, 0, 7); scene.scaleSceneView(.85f); scene.showBasePlate();
        var rail = CreateLinearEngineering.SCREW_RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, Direction.UP).setValue(LinearBlock.NORMAL, Direction.NORTH);
        var carriage = CreateLinearEngineering.SCREW_CARRIAGE.get().defaultBlockState().setValue(LinearBlock.FACING, Direction.UP).setValue(LinearBlock.NORMAL, Direction.NORTH);
        var rails = util.select().fromTo(3, 1, 3, 3, 5, 3);
        scene.world().setBlocks(rails, rail, false); scene.world().showSection(rails, Direction.SOUTH);
        scene.world().setBlock(new BlockPos(3, 2, 2), carriage, false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(3, 2, 2));
        scene.world().moveSection(body, new Vec3(0, 0, 1), 0);
        scene.world().setKineticSpeed(rails, 128);
        scene.world().moveSection(body, new Vec3(0, 2, 0), 75);
        text(scene, "A powered vertical screw lifts its carriage against gravity. Floor, ceiling, wall and moving-body mounting are all supported.", new Vec3(3.5, 3.5, 3));
        scene.world().setKineticSpeed(rails, 0);
        text(scene, "At zero RPM, or when Create's network is overstressed, the drive holds the current position instead of sliding down.", new Vec3(3.5, 4.5, 3));
        text(scene, "Empty screw rails consume no stress. Carriage load increases stress; grease reduces it. Linked carriages need compatible drive directions and speeds.", new Vec3(3.5, 2, 3));
    }
    private static void text(SceneBuilder scene, String message, Vec3 point) {
        scene.overlay().showText(85).text(message).pointAt(point).placeNearTarget().attachKeyFrame(); scene.idle(90);
    }
}
