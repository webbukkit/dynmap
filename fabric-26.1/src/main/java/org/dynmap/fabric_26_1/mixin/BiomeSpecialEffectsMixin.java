package org.dynmap.fabric_26_1.mixin;

import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.dynmap.fabric_26_1.access.BiomeSpecialEffectsExt;
import java.util.Optional;

@Mixin(BiomeSpecialEffects.class)
public class BiomeSpecialEffectsMixin implements BiomeSpecialEffectsExt {

    @Shadow private int waterColor;
    @Shadow private Optional<Integer> foliageColorOverride;
    @Shadow private Optional<Integer> dryFoliageColorOverride;
    @Shadow private Optional<Integer> grassColorOverride;
    @Shadow private BiomeSpecialEffects.GrassColorModifier grassColorModifier;

    private int dynmap$waterColor;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int waterColor,
                        Optional<Integer> foliageColorOverride,
                        Optional<Integer> dryFoliageColorOverride,
                        Optional<Integer> grassColorOverride,
                        BiomeSpecialEffects.GrassColorModifier grassColorModifier,
                        CallbackInfo ci)
    {
        this.dynmap$waterColor = waterColor;
    }

    @Override
    public int dynmap$getWaterColor() {
        return dynmap$waterColor;
    }

    @Override
    public Optional<Integer> dynmap$getFoliageColorOverride() { return foliageColorOverride; }

    @Override
    public Optional<Integer> dynmap$getDryFoliageColorOverride() { return dryFoliageColorOverride; }

    @Override
    public Optional<Integer> dynmap$getGrassColorOverride() { return grassColorOverride; }

    @Override
    public BiomeSpecialEffects.GrassColorModifier dynmap$getGrassColorModifier() {
        return grassColorModifier;
    }
}
