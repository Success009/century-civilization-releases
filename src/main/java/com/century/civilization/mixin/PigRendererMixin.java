package com.century.civilization.mixin;

import com.century.civilization.duck.RenderCrownDuck;
import com.century.civilization.feature.TechnoCrownFeatureRenderer;
import net.minecraft.client.model.animal.pig.BabyPigModel;
import net.minecraft.client.model.animal.pig.PigModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.world.entity.animal.pig.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PigRenderer.class)
public abstract class PigRendererMixin extends LivingEntityRenderer<Pig, PigRenderState, PigModel> {

    public PigRendererMixin(EntityRendererProvider.Context context, PigModel model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addTechnoCrownFeature(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new TechnoCrownFeatureRenderer<>(
            this,
            new PigModel(context.bakeLayer(ModelLayers.PIG_SADDLE)),
            new BabyPigModel(context.bakeLayer(ModelLayers.PIG_BABY))
        ));
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onUpdateRenderState(Pig pig, PigRenderState renderState, float f, CallbackInfo ci) {
        ((RenderCrownDuck) renderState).technomodel$setRenderCrown(pig.getName().getString().equals("Technoblade"));
    }
}
