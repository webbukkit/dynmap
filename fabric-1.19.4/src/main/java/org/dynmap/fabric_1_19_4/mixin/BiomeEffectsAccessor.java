package org.dynmap.fabric_1_19_4.mixin;

import net.minecraft.world.biome.BiomeEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BiomeEffects.class)
public interface BiomeEffectsAccessor {
    @Accessor
    int getWaterColor();
}
