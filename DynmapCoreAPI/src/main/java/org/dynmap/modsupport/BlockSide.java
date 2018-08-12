package org.dynmap.modsupport;

/**
 * Standard block sides (and sets of sides) - for non-patch-index based models and default solid blocks
 */
public enum BlockSide {
    FACE_0,     // Standard MC face 0 (bottom - negative Y)
    FACE_1,     // Standard MC face 1 (top - positive Y)
    FACE_2,     // Standard MC face 2 (north - negative Z)
    FACE_3,     // Standard MC face 3 (south - positive Z)
    FACE_4,     // Standard MC face 4 (west - negative X)
    FACE_5,     // Standard MC face 5 (east - positive X)
    BOTTOM,     // FACE0
    TOP,        // FACE1
    NORTH,      // FACE2
    SOUTH,      // FACE3
    WEST,       // FACE4
    EAST,       // FACE5
    Y_MINUS,    // FACE0
    Y_PLUS,     // FACE1
    Z_MINUS,    // FACE2
    Z_PLUS,     // FACE3
    X_MINUS,    // FACE4
    X_PLUS,     // FACE5
    ALLSIDES,   // All sides: FACE2 | FACE3 | FACE4 | FACE5
    ALLFACES    // All faces: FACE0 | FACE1 | ALLSIDES
}
