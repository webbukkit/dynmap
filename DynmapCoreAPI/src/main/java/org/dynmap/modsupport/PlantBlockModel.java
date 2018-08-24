package org.dynmap.modsupport;

/**
 * Standard plant block model - two texture (patch0, patch1), one for each surface (typically the same)
 */
public interface PlantBlockModel extends BlockModel {
    public static final int PATCH_FACE0 = 0;
    public static final int PATCH_FACE1 = 1;
}
