package org.dynmap.modsupport;

/**
 * Standard stairs block model
 * 
 * patch0 - the texture to be used for any of the sides (vertical faces) of the block
 * patch1 - the texture to be used for the tops of the block
 * patch2 - the texture to be used for the bottom of the block
 */
public interface StairBlockModel extends BlockModel {
    public static final int PATCH_SIDES = 0;
    public static final int PATCH_TOP = 1;
    public static final int PATCH_BOTTOM = 2;
}
