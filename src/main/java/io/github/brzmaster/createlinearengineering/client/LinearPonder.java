package io.github.brzmaster.createlinearengineering.client;

import io.github.brzmaster.createlinearengineering.CreateLinearEngineering;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Ponder supplies the hold-W tooltip, player, navigation and scene renderer. */
@EventBusSubscriber(modid = CreateLinearEngineering.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LinearPonder implements PonderPlugin {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        RailRenderer.init();
        event.enqueueWork(() -> PonderIndex.addPlugin(new LinearPonder()));
    }
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateLinearEngineering.RAIL_ENTITY.get(), RailRenderer::new);
    }
    @Override public String getModId() { return CreateLinearEngineering.ID; }
    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CreateLinearEngineering.SENSOR.getId()).addStoryBoard("linear", SensorScenes::sensor);
        helper.forComponents(CreateLinearEngineering.LOW_GREASE.getId(), CreateLinearEngineering.HIGH_GREASE.getId(),
                CreateLinearEngineering.CARRIAGE.getId(), CreateLinearEngineering.SCREW_CARRIAGE.getId())
                .addStoryBoard("linear", LinearScenes::grease);
        var scenes = helper.forComponents(CreateLinearEngineering.RAIL.getId(), CreateLinearEngineering.CARRIAGE.getId());
        scenes.addStoryBoard("linear", LinearScenes::horizontal);
        scenes.addStoryBoard("linear", LinearScenes::carriage);
        scenes.addStoryBoard("linear", LinearScenes::physics);
        scenes.addStoryBoard("linear", LinearScenes::vertical);
        scenes.addStoryBoard("linear", LinearScenes::orientations);
        scenes.addStoryBoard("linear", LinearScenes::coupling);
        scenes.addStoryBoard("linear", LinearScenes::reentry);
        helper.forComponents(CreateLinearEngineering.SCREW_RAIL.getId(), CreateLinearEngineering.SCREW_CARRIAGE.getId())
                .addStoryBoard("linear", ScrewScenes::drive)
                .addStoryBoard("linear", ScrewScenes::lift);
    }
}
