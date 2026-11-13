package io.github.brzmaster.createlinearengineering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.brzmaster.createlinearengineering.CreateLinearEngineering;
import io.github.brzmaster.createlinearengineering.LinearBlock;
import io.github.brzmaster.createlinearengineering.RailEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Only moving geometry belongs here; rail and sensor use vanilla baked models. */
public final class RailRenderer extends SafeBlockEntityRenderer<RailEntity> {
    private static final PartialModel SHAFT = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateLinearEngineering.ID, "block/screw_shaft"));
    public static void init() {} // Register the partial before model baking.
    public RailRenderer(BlockEntityRendererProvider.Context context) {}
    @Override protected void renderSafe(RailEntity entity, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = entity.getBlockState();
        if (!LinearBlock.isScrew(state)) return;
        var axis = entity.getBlockState().getValue(LinearBlock.FACING).getAxis();
        var model = CachedBuffers.partialFacingVertical(SHAFT, entity.getBlockState(), Direction.get(Direction.AxisDirection.POSITIVE, axis));
        KineticBlockEntityRenderer.renderRotatingBuffer(entity, model, pose, buffers.getBuffer(RenderType.solid()), light);
    }
}
