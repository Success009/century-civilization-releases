package com.century.civilization.feature;

import com.century.civilization.duck.RenderCrownDuck;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class TechnoCrownFeatureRenderer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>> extends RenderLayer<S, RM> {
    private final Identifier TEXTURE_ADULT;
    private final Identifier TEXTURE_BABY;
    private final EM adultModel;
    private final EM babyModel;

    public TechnoCrownFeatureRenderer(RenderLayerParent<S, RM> parent, EM adultModel, EM babyModel) {
        super(parent);
        this.TEXTURE_ADULT = Identifier.withDefaultNamespace("textures/entity/pig/technocrown_adult.png");
        this.TEXTURE_BABY = Identifier.withDefaultNamespace("textures/entity/pig/technocrown_baby.png");
        this.adultModel = adultModel;
        this.babyModel = babyModel;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int i, S state, float f, float g) {
        if (((RenderCrownDuck) state).technomodel$shouldRenderCrown()) {
            EM model = state.isBaby ? this.babyModel : this.adultModel;
            model.setupAnim(state);
            
            if (state.isBaby) {
                poseStack.translate(0.0D, 1.0625D, -0.25D);
                poseStack.translate(0.0D, -0.0625D, 0.0D);
                poseStack.scale(1.125F, 1.125F, 1.125F);
                poseStack.translate(0.0D, -1.0625D, 0.25D);
                
                collector.submitModel(
                    model,
                    state,
                    poseStack,
                    RenderTypes.entityCutout(this.TEXTURE_BABY),
                    i,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor,
                    null
                );
            } else {
                collector.submitModel(
                    model,
                    state,
                    poseStack,
                    RenderTypes.entityCutout(this.TEXTURE_ADULT),
                    i,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor,
                    null
                );
            }
        }
    }
}
