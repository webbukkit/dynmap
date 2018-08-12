package org.dynmap.modsupport;

public enum TransparencyMode {
    OPAQUE, // Block is solid and opaque: block light and has faces lit based on adjacent block's light level
    TRANSPARENT,    // Block does not block light, and is lit based on its own light level
    SEMITRANSPARENT // Block is not solid, but blocks light and is lit by adjacent blocks (slabs, stairs)
}
