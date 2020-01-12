package org.dynmap.modsupport;

/**
 * Glass pane / iron fence block model
 * <p>
 * patch0 - Texture used for all the faces of the pane model (the "glass")
 * patch1 - Texture used for the edges of the panel model (the "frame")
 */
public interface PaneBlockModel extends BlockModel {
    int PATCH_FACE = 0;
    int PATCH_EDGE = 1;

}
