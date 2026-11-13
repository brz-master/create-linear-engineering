package io.github.brzmaster.createlinearengineering.client;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import io.github.brzmaster.createlinearengineering.CreateLinearEngineering;
import io.github.brzmaster.createlinearengineering.LinearBlock;
import io.github.brzmaster.createlinearengineering.RailSensor;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class SensorScenes {
    private SensorScenes() {}
    public static void sensor(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("rail_sensor", "Detecting a carriage along the track");
        scene.configureBasePlate(0, 0, 7); scene.scaleSceneView(.85f); scene.showBasePlate();
        var rail = CreateLinearEngineering.RAIL.get().defaultBlockState().setValue(LinearBlock.FACING, Direction.EAST);
        var track = util.select().fromTo(1, 2, 3, 5, 2, 3);
        scene.world().setBlocks(track, rail, false);
        scene.world().setBlocks(util.select().fromTo(1, 1, 3, 5, 1, 3), Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 3, 5, 2, 3), Direction.DOWN);
        text(scene, "Install a Rail Sensor on any free or screw rail. It shares the existing block and leaves the carriage path clear.", new Vec3(3.5, 2.2, 3));
        scene.world().setBlocks(track, rail.setValue(LinearBlock.SENSOR, RailSensor.Mode.APPROXIMATE), false);
        scene.world().setBlock(new BlockPos(1, 3, 3), CreateLinearEngineering.CARRIAGE.get().defaultBlockState().setValue(LinearBlock.FACING, Direction.EAST), false);
        var body = scene.world().showIndependentSectionImmediately(util.select().position(1, 3, 3));
        scene.world().moveSection(body, new Vec3(0, -1, 0), 0);
        light(scene, 1, true);
        text(scene, "Approximate mode activates as soon as any part of the carriage overlaps this rail segment. Signal strength is 15, only through the back of the base.", new Vec3(1.5, 1.5, 3));
        for (int x = 2; x <= 4; x++) {
            scene.world().moveSection(body, new Vec3(.5, 0, 0), 12);
            scene.idle(12); light(scene, x, true);
            scene.world().moveSection(body, new Vec3(.5, 0, 0), 13);
            scene.idle(13); light(scene, x - 1, false);
            scene.idle(10);
        }
        text(scene, "Lamps behind successive sensors follow the carriage. During a transition, Approximate mode can detect the overlap on two neighbouring segments.", new Vec3(3.5, 1.5, 3));
        scene.world().setBlocks(track, rail.setValue(LinearBlock.SENSOR, RailSensor.Mode.PRECISE), false);
        scene.world().moveSection(body, new Vec3(.35, 0, 0), 20); light(scene, 4, false); scene.idle(20);
        text(scene, "Shift-right-click with a Create wrench to select Precise mode. It activates only within 1/32 block of the segment centre, not while the carriage is offset.", new Vec3(4.5, 2.4, 3));
        scene.world().moveSection(body, new Vec3(-.35, 0, 0), 20); scene.idle(20); light(scene, 4, true);
        text(scene, "Fast crossings also trigger the sensor, with a pulse of at least two game ticks. Use the signal in your door circuit: the sensor is not an automatic precision brake.", new Vec3(4.5, 1.5, 3));
        text(scene, "The base side remains the output on walls, ceilings and moving bodies. Breaking the rail in survival drops both the rail and its installed sensor.", new Vec3(2.5, 2.1, 3));
    }
    private static void light(CreateSceneBuilder scene, int x, boolean on) {
        scene.world().modifyBlock(new BlockPos(x, 2, 3), state -> state.setValue(LinearBlock.POWERED, on), false);
        scene.world().modifyBlock(new BlockPos(x, 1, 3), state -> state.setValue(BlockStateProperties.LIT, on), false);
    }
    private static void text(SceneBuilder scene, String message, Vec3 point) {
        scene.overlay().showText(85).text(message).pointAt(point).placeNearTarget().attachKeyFrame(); scene.idle(90);
    }
}
