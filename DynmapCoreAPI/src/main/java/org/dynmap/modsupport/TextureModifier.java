package org.dynmap.modsupport;

/**
 * Texture presentation modifier
 */
public enum TextureModifier {
    NONE,           // No modifier
    GRASSTONED,     // Tone texture using grass biome colormap
    FOLIAGETONED,   // Tone texture using foliage biome colormap
    WATERTONED,     // Tone texture using water biome colormap
    ROT90,          // Rotate texture 90 degrees
    ROT180,         // Rotate texture 180 degrees
    ROT270,         // Rotate texture 270 degrees
    FLIPHORIZ,      // Flip texture horizontally
    SHIFTDOWNHALF,  // Shift texture down 50% of block height 
    SHIFTDOWNHALFANDFLIPHORIZ,  // SHIFTDOWNHALF and FLIPHORIZ
    INCLINEDTORCH,  // Shear texture for side of torch
    GRASSSIDE,      // Block is grass side: overlay grass side or snow side if snow above
    CLEARINSIDE,    // Block is transparent solid: don't render interior surfaces
    PINETONED,      // Tone texture using pine leaves colormap
    BIRCHTONED,     // Tone texture using birch leaves colormap
    LILYTONED,      // Tone texture using lily pad colormap
    MULTTONED,      // Tone texture using fixed color multipler, or custom multiplier
    GRASSTONED270,  // GRASSTONED and ROT280
    FOLIAGETONED270,    // FOLIAGETONED and ROT270
    WATERTONED270,  // WATERTONED and ROT270
    MULTTONED_CLEARINSIDE,  // MULTTONED and CLEARINSIDE
    FOLIAGEMULTTONED    // FOLIAGETONED and MULTTONED
}
