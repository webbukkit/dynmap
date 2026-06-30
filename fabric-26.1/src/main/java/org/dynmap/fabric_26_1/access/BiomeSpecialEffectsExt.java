package org.dynmap.fabric_26_1.access;
import java.util.Optional;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import java.util.OptionalInt;

public interface BiomeSpecialEffectsExt {
    int dynmap$getWaterColor();
    Optional<Integer> dynmap$getFoliageColorOverride();
    Optional<Integer> dynmap$getDryFoliageColorOverride();
    Optional<Integer> dynmap$getGrassColorOverride();
    BiomeSpecialEffects.GrassColorModifier dynmap$getGrassColorModifier();
    default int dynmap$getFoliageColor() {
        return dynmap$getFoliageColorOverride().orElse(0); // Default value if not present
    }

    default int dynmap$getGrassColor() {
        return dynmap$getGrassColorOverride().orElse(0); // Default value if not present
    }
}