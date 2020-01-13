package org.dynmap.modsupport;

/**
 * Standard door block model
 * 
 * patch0 - the texture to be used for the tops of the door
 * patch1 - the texture to be used for the bottom of the door
 */
public interface DoorBlockModel extends BlockModel {
    public static final int PATCH_TOP = 0;
    public static final int PATCH_BOTTOM = 1;
}
