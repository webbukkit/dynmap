package org.dynmap.renderer;

/**
 * Interface for an allocated render patch - constructred using factory provided to CustomRenderer during
 * initialize() and/or during getRenderPatchList() call (via MapDataContext).  Values are read-only once created,
 * and will be reused when possible.
 */
public interface RenderPatch {
    public int getTextureIndex();
}
