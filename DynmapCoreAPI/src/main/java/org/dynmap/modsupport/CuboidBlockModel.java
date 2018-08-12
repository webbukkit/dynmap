package org.dynmap.modsupport;

/**
 * Cuboid block model
 */
public interface CuboidBlockModel extends BlockModel {
    /**
     * Add cuboid to model
     * 
     * @param xmin - minimum x
     * @param ymin - minimum y
     * @param zmin - minimum z
     * @param xmax - maximum x
     * @param ymax - maximum y
     * @param zmax - maximum z
     * @param patchIndices - array of patch indexes, ordered by standard block face order (y-, y+, z-, z+, x-, x+): if null, default is 0,1,2,3,4,5
     */
    public void addCuboid(double xmin, double ymin, double zmin, double xmax, double ymax, double zmax, int[] patchIndices);
    /**
     * Add crossed patches (like plants) to model
     * @param xmin - minimum x
     * @param ymin - minimum y
     * @param zmin - minimum z
     * @param xmax - maximum x
     * @param ymax - maximum y
     * @param zmax - maximum z
     * @param patchIndex - index of patch to use for both patches
     */
    public void addCrossedPatches(double xmin, double ymin, double zmin, double xmax, double ymax, double zmax, int patchIndex);
}
