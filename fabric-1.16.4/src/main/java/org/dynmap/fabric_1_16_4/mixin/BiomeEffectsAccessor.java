package org.dynmap.fabric_1_16_4.mixin;

import net.minecraft.world.biome.BiomeEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(BiomeEffects.class)
public interface BiomeEffectsAccessor {
    @Accessor
    int getWaterColor();
    @Accessor
    Optional<Integer> getFoliageColor();
    @Accessor
    Optional<Integer> getGrassColor();
    @Accessor
    BiomeEffects.GrassColorModifier getGrassColorModifier();
}
