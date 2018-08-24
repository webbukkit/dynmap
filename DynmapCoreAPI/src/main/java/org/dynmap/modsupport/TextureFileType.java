package org.dynmap.modsupport;

public enum TextureFileType {
    GRID,       // Default type - file is N x N grid of square textures (including simple single texture files
    CHEST,      // Standard single chest texture
    BIGCHEST,   // Standard double chest texture
    SIGN,       // Standard signpost texture
    SKIN,       // Standard player or humanoid model texture (zombie, skeleton)
    SHULKER,    // Standard shulker and shulker box texture
    CUSTOM,     // Custom patch layout in texture
    BIOME       // Biome color map (256 x 256)
}
