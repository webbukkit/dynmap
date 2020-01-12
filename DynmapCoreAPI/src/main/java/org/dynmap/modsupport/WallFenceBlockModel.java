package org.dynmap.modsupport;

/**
 * Standard fence or wall block model
 * <p>
 * patch0 - the texture to be used for any of the sides (vertical faces) of the block
 * patch1 - the texture to be used for the tops of the block
 * patch2 - the texture to be used for the bottom of the block
 */
public interface WallFenceBlockModel extends BlockModel {
    int PATCH_SIDES = 0;
    int PATCH_TOP = 1;
    int PATCH_BOTTOM = 2;

    /**
     * Type wall/fence
     */
    enum FenceType {
        FENCE,  // Standard fence
        WALL    // Standard wall
    }

    /**
     * Get fence type
     * return fence type
     */
    FenceType getFenceType();

    /**
     * Add block IDs linked with (beyond normal self and opaque blocks)
     *
     * @param blkid - block ID to link to
     */
    void addLinkedBlockID(int blkid);

    /**
     * Get linked block IDs
     *
     * @return linked block ids
     */
    int[] getLinkedBlockIDs();
}
