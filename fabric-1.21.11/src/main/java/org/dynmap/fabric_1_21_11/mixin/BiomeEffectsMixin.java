package org.dynmap.fabric_1_21_11.mixin;

import net.minecraft.world.biome.BiomeEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.dynmap.fabric_1_21_11.access.BiomeEffectsExt;
import java.util.Optional;

@Mixin(BiomeEffects.class)
public class BiomeEffectsMixin implements BiomeEffectsExt {

    @Shadow private int waterColor;
    @Shadow private Optional<Integer> foliageColor;
    @Shadow private Optional<Integer> dryFoliageColor;
    @Shadow private Optional<Integer> grassColor;
    @Shadow private BiomeEffects.GrassColorModifier grassColorModifier;

    private int dynmap$waterColor;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int waterColor,
                        Optional<Integer> foliageColor,
                        Optional<Integer> dryFoliageColor,
                        Optional<Integer> grassColor,
                        BiomeEffects.GrassColorModifier grassColorModifier,
                        CallbackInfo ci)
    {
        this.dynmap$waterColor = waterColor;
    }

    @Override
    public int dynmap$getWaterColor() {
        return dynmap$waterColor;
    }

    @Override
    public Optional<Integer> dynmap$getFoliageColor() { return foliageColor; }

    @Override
    public Optional<Integer> dynmap$getDryFoliageColor() { return dryFoliageColor; }

    @Override
    public Optional<Integer> dynmap$getGrassColor() { return grassColor; }

    @Override
    public BiomeEffects.GrassColorModifier dynmap$getGrassColorModifier() {
        return grassColorModifier;
    }
}
