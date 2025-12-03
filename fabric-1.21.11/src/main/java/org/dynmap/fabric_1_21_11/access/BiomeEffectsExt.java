package org.dynmap.fabric_1_21_11.access;
import java.util.Optional;
import net.minecraft.world.biome.BiomeEffects;
import java.util.OptionalInt;

public interface BiomeEffectsExt {
    int dynmap$getWaterColor();
    Optional<Integer> dynmap$getFoliageColor();
    Optional<Integer> dynmap$getDryFoliageColor();
    Optional<Integer> dynmap$getGrassColor();
    BiomeEffects.GrassColorModifier dynmap$getGrassColorModifier();
}