package org.dynmap.fabric_26_1.access;
import java.util.Optional;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import java.util.OptionalInt;

public interface BiomeEffectsExt {
    int dynmap$getWaterColor();
    Optional<Integer> dynmap$getFoliageColor();
    Optional<Integer> dynmap$getDryFoliageColor();
    Optional<Integer> dynmap$getGrassColor();
    BiomeSpecialEffects.GrassColorModifier dynmap$getGrassColorModifier();
}