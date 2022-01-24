package org.dynmap.modsupport;

// Model for more direct translation of MC models
//   All coordinates are 0-16 range per block, and 0-16 range for UV

public interface ModelBlockModel extends BlockModel {
	
	public interface ModelBlock {
	    /**
	     * Factory method for adding a side to a model block started using addModelBlock.
	     * 
	     * @param face - which face (determines use of xyz-min vs xyz-max
	     * @param uv - bounds on UV (umin, vmin, umax, vmax): if null, default based on face range
	     * @param textureid - texture ID
	     */
		public void addBlockSide(BlockSide side, double[] uv, int textureid);
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
	 * @return model block to add faces to
     */
    public ModelBlock addModelBlock(double[] from, double[] to);
}
