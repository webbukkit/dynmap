package org.dynmap.modsupport;

// Model for more direct translation of MC models
//   All coordinates are 0-16 range per block, and 0-16 range for UV

public interface ModelBlockModel extends BlockModel {
	public enum SideRotation {
		DEG0,	// zero degrees (default)
		DEG90,	// 90 degrees
		DEG180,	// 180 degrees
		DEG270	// 270 degrees
	};
	public interface ModelBlock {
	    /**
	     * Factory method for adding a side to a model block started using addModelBlock.
	     * 
	     * @param face - which face (determines use of xyz-min vs xyz-max
	     * @param uv - bounds on UV (umin, vmin, umax, vmax): if null, default based on face range
	     * @param rot - rotation of the block side (default id DEG0)
	     * @param textureid - texture ID
	     * @param tintidx - tintindex (-1 if none)
	     */
		public void addBlockSide(BlockSide side, double[] uv, SideRotation rot, int textureid, int tintidx);
		public void addBlockSide(BlockSide side, double[] uv, SideRotation rot, int textureid);
	}
    /**
     * Factory method to build a block of patches relative to a typical element in a MC model file.
     * Specifically, all coordinates are relative to 0-16 range for
     * side of a cube, and relative to 0-16 range for U,V within a texture:
     * 
     *    from, to in model drive 'from', 'to' inputs
     *    
     *    face, uv of face, and texture in model drives face, uv, textureid (added using addBlockSide)
     * 
     * @param from - vector of lower left corner of box (0-16 range for coordinates - min x, y, z)
     * @param to - vector of upper right corner of box (0-16 range for coordinates max x, y, z)
     * @param xrot - degrees of rotation of block around X
     * @param yrot - degrees of rotation of block around Y
     * @param zrot - degrees of rotation of block around Z
     * @param shade - shade setting for model
     * @param rotorigin = rotation origin [x, y, z] (if null, [ 8,8,8 ] is assumed
     * @param modrotx - model level rotation in degrees (0, 90, 180, 270)
     * @param modroty - model level rotation in degrees (0, 90, 180, 270)
     * @param modrotz - model level rotation in degrees (0, 90, 180, 270)
	 * @return model block to add faces to
     */
    public ModelBlock addModelBlock(double[] from, double[] to, double xrot, double yrot, double zrot, 
		boolean shade, double[] rotorigin, int modrotx, int modroty, int modrotz);
    default public ModelBlock addModelBlock(double[] from, double[] to, double xrot, double yrot, double zrot, boolean shade) {
    	return addModelBlock(from, to, xrot, yrot, zrot, shade, null, 0, 0, 0);    	
    }
    default public ModelBlock addModelBlock(double[] from, double[] to, double xrot, double yrot, double zrot) {
    	return addModelBlock(from, to, xrot, yrot, zrot, true, null, 0, 0, 0);
    }
}
