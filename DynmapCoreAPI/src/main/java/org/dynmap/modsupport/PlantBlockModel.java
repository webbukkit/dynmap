package org.dynmap.modsupport;

/**
 * Standard plant block model - two texture (patch0, patch1), one for each surface (typically the same)
 */
public interface PlantBlockModel extends BlockModel {
    int PATCH_FACE0 = 0;
    int PATCH_FACE1 = 1;
}
