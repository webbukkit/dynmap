package org.dynmap.utils;

/* Represents last step of movement of the ray (don't alter order here - ordinal sensitive) */
public enum BlockStep {
    X_PLUS(4, 1, 0, 0),
    Y_PLUS(0, 0, 1, 0),
    Z_PLUS(2, 0, 0, 1),
    X_MINUS(5, -1, 0, 0),
    Y_MINUS(1, 0, -1, 0),
    Z_MINUS(3, 0, 0, -1);
    
    private final int face; // Index of MC block face entered through with step (Y_MINUS = enter from top)
    public final int xoff;
    public final int yoff;
    public final int zoff;
    
    public static final BlockStep oppositeValues[] = { X_MINUS, Y_MINUS, Z_MINUS, X_PLUS, Y_PLUS, Z_PLUS };
            
    BlockStep(int f, int xoff, int yoff, int zoff) {
        face = f;
        this.xoff = xoff;
        this.yoff = yoff;
        this.zoff = zoff;
    }
    
    public final BlockStep opposite() {
        return oppositeValues[ordinal()];
    }
    // MC index of face entered by step
    public final int getFaceEntered() {
        return face;
    }
}

