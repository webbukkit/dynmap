package org.dynmap.modsupport.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.modsupport.ModelBlockModel.SideRotation;

public class ModelBlockModelImpl extends BlockModelImpl implements ModelBlockModel {

	private static class ModelSide {
		private double[] uv;
		private SideRotation rot;
		int textureid;
	};
	private static class ModelBlockImpl implements ModelBlock {
		private HashMap<BlockSide, ModelSide> sides = new HashMap<BlockSide, ModelSide>();
		private double[] from = { 0, 0, 0 };
		private double[] to = { 16, 16, 16 }; 
		private double xrot = 0, yrot = 0, zrot = 0;
		@Override
		public void addBlockSide(BlockSide side, double[] uv, SideRotation rot, int textureid) {
			ModelSide ms = new ModelSide();
			ms.textureid = textureid;
			if (uv != null) {
				ms.uv = Arrays.copyOf(uv, uv.length);
			}
			if (rot != null) {
				ms.rot = rot;
			}
			if (side == BlockSide.FACE_0 || side == BlockSide.Y_MINUS) side = BlockSide.BOTTOM;
			if (side == BlockSide.FACE_1 || side == BlockSide.Y_PLUS) side = BlockSide.TOP;
			if (side == BlockSide.FACE_2 || side == BlockSide.Z_MINUS) side = BlockSide.NORTH;
			if (side == BlockSide.FACE_3 || side == BlockSide.Z_PLUS) side = BlockSide.SOUTH;
			if (side == BlockSide.FACE_4 || side == BlockSide.X_MINUS) side = BlockSide.WEST;
			if (side == BlockSide.FACE_5 || side == BlockSide.X_PLUS) side = BlockSide.EAST;
			
			sides.put(side,  ms);
		}		
	}
	private ArrayList<ModelBlockImpl> boxes = new ArrayList<ModelBlockImpl>();    
	private String rotsourceblockname;
	private int rotsourcemetaindex;
	private int xrot, yrot, zrot;
    
    public ModelBlockModelImpl(String blkname, ModModelDefinitionImpl mdf) {
        super(blkname, mdf);
    }

    public ModelBlockModelImpl(String blkname, ModModelDefinitionImpl mdf, ModelBlockModel mod, int xrot, int yrot, int zrot) {
        super(blkname, mdf);
        this.rotsourceblockname = mod.getBlockNames()[0];
        this.rotsourcemetaindex = Integer.numberOfTrailingZeros(mod.getMetaValueMask());
        this.xrot = xrot; this.yrot = yrot; this.zrot = zrot;
    }
    
    private static HashMap<BlockSide, String> fromBlockSide = new HashMap<BlockSide, String>();
    static {
    	fromBlockSide.put(BlockSide.TOP, "u");
    	fromBlockSide.put(BlockSide.BOTTOM, "d");
    	fromBlockSide.put(BlockSide.NORTH, "n");
    	fromBlockSide.put(BlockSide.SOUTH, "s");
    	fromBlockSide.put(BlockSide.WEST, "e");
    	fromBlockSide.put(BlockSide.EAST, "w");
    };

    @Override
    public String getLine() {
        String ids = this.getIDsAndMeta();
        if (ids == null) return null;
        String line = String.format("patchblock:%s", ids);
        // If rotating another model
        if (rotsourceblockname != null) {
        	line += "\npatchrotate:id=" + rotsourceblockname + ",data=" + rotsourcemetaindex;
        	if (xrot != 0) {
        		line += ",rotx=" + xrot;
        	}
        	if (yrot != 0) {
        		line += ",roty=" + yrot;
        	}
        	if (zrot != 0) {
        		line += ",rotz=" + yrot;
        	}
        }
        else {
        	for (ModelBlockImpl mb: boxes) {
        		line += String.format(",box=%f/%f/%f:%f/%f/%f", mb.from[0], mb.from[1], mb.from[2], mb.to[0], mb.to[1], mb.to[2]);
        		if ((mb.xrot != 0) || (mb.yrot != 0) || (mb.zrot != 0)) {	// If needed, add rotation
        			line += String.format("/%f/%f/%f", mb.xrot, mb.yrot, mb.zrot);
        		}
        		for (BlockSide bs : fromBlockSide.keySet()) {
        			String side = fromBlockSide.get(bs);
        			ModelSide mside = mb.sides.get(bs);
        			if (mside != null) {
        				String rval = side;
        				switch (mside.rot) {
	        				case DEG0:
	        				default:
	        					break;
	        				case DEG90:
	        					rval += "90";
	        					break;
	        				case DEG180:
	        					rval += "180";
	        					break;
	        				case DEG270:
	        					rval += "270";
	        					break;
        				}
        				if (mside.uv != null) {
        					line += String.format(":%s/%d/%f/%f/%f/%f", rval, mside.textureid, mside.uv[0], mside.uv[1], mside.uv[2], mside.uv[3]);
        				}
        				else {
        					line += String.format(":%s/%d", rval, mside.textureid);  					
        				}
        			}
        		}
        	}
        }
        return line;
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
	 * @return model block to add faces to
     */
    public ModelBlock addModelBlock(double[] from, double[] to, double xrot, double yrot, double zrot) {
    	ModelBlockImpl mbi = new ModelBlockImpl();
    	if (from != null) { mbi.from[0] = from[0]; mbi.from[1] = from[1]; mbi.from[2] = from[2]; }
    	if (to != null) { mbi.to[0] = to[0]; mbi.to[1] = to[1]; mbi.to[2] = to[2]; }    	
    	mbi.xrot = xrot; mbi.yrot = yrot; mbi.zrot = zrot;
    	boxes.add(mbi);
    	return mbi;
    }
}
