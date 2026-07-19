package org.dynmap.utils;

import java.util.HashMap;
import java.util.Map;

import org.dynmap.hdmap.TexturePack;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;

public class PatchDefinitionFactory implements RenderPatchFactory {
    private HashMap<PatchDefinition,PatchDefinition> patches = new HashMap<PatchDefinition,PatchDefinition>();
    private Object lock = new Object();
    private PatchDefinition lookup = new PatchDefinition();
    private Map<String, PatchDefinition> namemap = null;

    public PatchDefinitionFactory() {
        
    }

    public void setPatchNameMape(Map<String, PatchDefinition> nmap) {
        namemap = nmap;
    }
    
    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, SideVisible sidevis,
            int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, umin, umax, vmin, vmax, sidevis, textureids, vmin, vmax, true);
    }

    @Override
    public RenderPatch getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv,
            double uplusvmax, SideVisible sidevis, int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, 0.0, uplusvmax, 0.0, uplusvmax, sidevis, textureids, 0.0, 0.0, true);
    }
    @Override
    public PatchDefinition getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vminatumax, double vmax, double vmaxatumax, SideVisible sidevis,
            int textureids) {
        return getPatch(x0, y0, z0, xu, yu, zu,xv, yv, zv, umin, umax, vmin, vmax, sidevis, textureids, vminatumax, vmaxatumax, true);
    }
    
    public PatchDefinition getPatch(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, SideVisible sidevis,
            int textureids, double vminatumax, double vmaxatumax, boolean shade) {
        synchronized(lock) {
            lookup.update(x0, y0, z0, xu, yu, zu, xv, yv, zv, umin,
                    umax, vmin, vmax, sidevis, textureids, vminatumax, vmaxatumax, shade);
            if(lookup.validate() == false)
                return null;
            PatchDefinition pd2 = patches.get(lookup);  /* See if in cache already */
            if(pd2 == null) {
                PatchDefinition pd = new PatchDefinition(lookup);
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }

    }

    public PatchDefinition getModelFace(double[] from, double[] to, BlockSide face, double[] uv, ModelBlockModel.SideRotation rot, boolean shade, int textureid) {
        synchronized(lock) {
            lookup.updateModelFace(from, to, face, uv, rot, shade, textureid);
            if(lookup.validate() == false)
                return null;
            PatchDefinition pd2 = patches.get(lookup);  /* See if in cache already */
            if(pd2 == null) {
                PatchDefinition pd = new PatchDefinition(lookup);
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }    	
    }
    
    @Override
    public RenderPatch getRotatedPatch(RenderPatch patch, double xrot, double yrot,
            double zrot, int textureindex) {
        return getPatch((PatchDefinition)patch, xrot, yrot, zrot, textureindex);
    }
    @Override
    public RenderPatch getRotatedPatch(RenderPatch patch, double xrot, double yrot,
            double zrot, double rotorigx, double rotorigy, double rotorigz, int textureindex) {
        return getPatch((PatchDefinition)patch, xrot, yrot, zrot, 
        		new Vector3D(rotorigx, rotorigy, rotorigz), textureindex);
    }
    @Override
    public RenderPatch getRotatedPatch(RenderPatch patch, int xrot, int yrot,
            int zrot, int textureindex) {
        return getPatch((PatchDefinition)patch, xrot, yrot, zrot, textureindex);
    }

    public PatchDefinition getPatch(PatchDefinition patch, double xrot, double yrot,
            double zrot, Vector3D rotorig, int textureindex) {
        PatchDefinition pd = new PatchDefinition((PatchDefinition)patch, xrot, yrot, zrot, rotorig, textureindex);
        if (pd.validate() == false)
            return null;
        synchronized(lock) {
            PatchDefinition pd2 = patches.get(pd);  /* See if in cache already */
            if (pd2 == null) {
                patches.put(pd,  pd);
                pd2 = pd;
            }
            return pd2;
        }
    }
    public PatchDefinition getPatch(PatchDefinition patch, double xrot, double yrot,
            double zrot, int textureindex) {
    	return getPatch(patch, xrot, yrot, zrot, null, textureindex);
    }
    /**
     * Get named patch with given attributes.  Name can encode rotation and patch index info
     * "name" - simple name
     * "name@rot" - name with rotation around Y
     * "name@x/y/z" - name with rotation around x, then y, then z axis
     * "name#patch" - name with explicit patch index
     * 
     * @param name - name of patch (must be defined in same config file as custom renderer): supports name@yrot, name@xrot/yrot/zrot
     * @param textureidx - texture index to be used for patch, if not provided in name encoding (#patchid suffix)
     * @return patch requested
     */
    @Override
    public RenderPatch getNamedPatch(final String name, int textureidx) {
        return getPatchByName(name, textureidx);
    }
    public PatchDefinition getPatchByName(final String name, int textureidx) {
        PatchDefinition pd = null;
        if(namemap != null) {
            pd = namemap.get(name);
            if((pd != null) && (textureidx != pd.textureindex)) {
                pd = null;
            }
            if(pd == null) {
                String patchid = name;
                int txt_idx = -1;
                int off = patchid.lastIndexOf('#');
                if(off > 0) {
                    try {
                        txt_idx = Integer.valueOf(patchid.substring(off+1));
                    } catch (NumberFormatException nfx) {
                        return null;
                    }
                    patchid = patchid.substring(0,  off);
                }
                int rotx = 0, roty = 0, rotz = 0;
                /* See if ID@rotation */
                off = patchid.indexOf('@');
                if(off > 0) {
                    String[] rv = patchid.substring(off+1).split("/");
                    if(rv.length == 1) {
                        roty = Integer.parseInt(rv[0]);
                    }
                    else if(rv.length == 2) {
                        rotx = Integer.parseInt(rv[0]);
                        roty = Integer.parseInt(rv[1]);
                    }
                    else if(rv.length == 3) {
                        rotx = Integer.parseInt(rv[0]);
                        roty = Integer.parseInt(rv[1]);
                        rotz = Integer.parseInt(rv[2]);
                    }
                    patchid = patchid.substring(0, off);
                }
                pd = namemap.get(patchid);
                if(pd == null) {
                    return null;
                }
                else if(txt_idx >= 0) { /* If set texture index */
                    pd = getPatch(pd, rotx,  roty,  rotz, txt_idx);
                }
                else {
                    pd = getPatch(pd, rotx,  roty,  rotz, textureidx);
                }
                if(pd != null) {
                    namemap.put(name, pd);
                }
            }
        }
        return pd;
    }

    @Override
    public int getTextureIndexFromMap(String id, int key) {
        return TexturePack.getTextureIndexFromTextureMap(id, key);
    }

    @Override
    public int getTextureCountFromMap(String id) {
        return TexturePack.getTextureMapLength(id);
    }
    
//    public static void main(String[] args) {
//    	PatchDefinition pd;
//    	
//    	// box=0.000000/3.000000/9.000000:2.000000/6.000000/13.000000:
//    	// w/0/13.000000/7.000000/15.000000/10.000000:d/0/0.000000/9.000000/2.000000/13.000000:e/0/13.000000/7.000000/15.000000/10.000000:u/0/0.000000/9.000000/2.000000/13.000000:R/0/180/0
//    	BlockSide[] faces = { BlockSide.WEST, BlockSide.BOTTOM, BlockSide.EAST, BlockSide.TOP };
//    	// campfire log:box=1/0/0:5/4/16:n/0/0/4/4/8:e/0/0/1/16/5:s/0/0/4/4/8:w/0/16/0/0/4:u90/0/0/0/16/4:d90/0/0/0/16/4
//    	double[][] uvs = { { 13, 7, 15, 10 }, { 0, 9, 2, 13 }, { 13, 7, 15, 10 }, { 0, 9, 2, 13 } };
//    	ModelBlockModel.SideRotation[] rots = { ModelBlockModel.SideRotation.DEG0, ModelBlockModel.SideRotation.DEG0, ModelBlockModel.SideRotation.DEG0,
//    	                                        ModelBlockModel.SideRotation.DEG0 };
//    	double[] from = { 0, 3, 9 };
//    	double[] to = { 2, 6, 13 };
//    	
//    	// Do normal faces, default limits
//    	pd = new PatchDefinition();
//    	for (int i = 0; i < faces.length; i++) {
//    		pd.updateModelFace(from,  to, faces[i], uvs[i], rots[i], true, 0);
//    		System.out.println("Log " + faces[i] + ": " + pd);
//    	}    	
//    	
//    	// Do normal faces, default limits
//    	pd = new PatchDefinition();
//    	for (BlockSide face : faces) {
//    		pd.updateModelFace(from,  to, face, null, 0);
//    		System.out.println("Full cube " + face + ": " + pd);
//    	}    	
//    	
//    	double[] toquarter = { 8,8,8 };
//    	for (BlockSide face : faces) {
//    		pd.updateModelFace(from,  toquarter, face, null, 0);
//    		System.out.println("8x8x8 cube " + face + ": " + pd);
//    	}    	
//    	
//
//    	for (BlockSide face : faces) {
//    		pd.updateModelFace(from,  toquarter, face, new double[] { 4, 4, 12, 12 }, 0);
//    		System.out.println("Full cube, middle half of texture " + face + ": " + pd);
//    	}    	
//
//    }
}
