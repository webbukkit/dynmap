package org.dynmap.utils;

import org.dynmap.Log;
import org.dynmap.modsupport.BlockSide;
import org.dynmap.modsupport.ModelBlockModel;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

//
//          (v = 1)         umin    umax
// x0+xv,y0+yv,z0+zv  *-----|-------|--------* (u=1, v=1)      x0,y0,z0 = lower left corner relative to cube origin (0,0,0 to 1,1,1)
//                   |     |       |        |                  length of xu,yu,zu = width of whole texture (u=0 to u=1)
//                  |-----+=======+--------| vmax              length of xv,yv,zv = height of whole texture (v=0 to v=1)
//                 |     [visible]        |                    umin to umax = clipping (visible portion of texture) horizontally
//                |-----+=======+--------| vmin                vmin to vmax = clipping (visible portion of texture) vertically
//      (u=0,v=0)|     |       |        |
//      x0,y0,z0 *----|-------|--------* x0+xu, y0+yu, z0+zu (u = 1)
//
/* Define patch in surface-based models - origin (xyz), u-vector (xyz) v-vector (xyz), u limits and v limits */
public class PatchDefinition implements RenderPatch {
    public double x0, y0, z0;   /* Origin of patch (lower left corner of texture) */
    public double xu, yu, zu;   /* Coordinates of end of U vector (relative to origin) - corresponds to u=1.0 (lower right corner) */
    public double xv, yv, zv;   /* Coordinates of end of V vector (relative to origin) - corresponds to v=1.0 (upper left corner) */
    public double umin, umax;   /* Limits of patch - minimum and maximum u value */
    public double vmin, vmax;   /* Limits of patch - minimum and maximum v value */
    public double vmaxatumax;   /* Limits of patch - max v value at max u (allows triangle or trapezoid) */
    public double vminatumax;   /* Limits of patch - min v value at max u (allows triangle or trapezoid) */
    public Vector3D u, v;       /* U and V vector, relative to origin */
    public SideVisible sidevis;  /* Which side is visible */
    public int textureindex;
    public BlockStep step; /* Best approximation of orientation of surface, from top (positive determinent) */
    public boolean shade;	// If false, patch is not shaded
    private int hc;
    /* Offset vector of middle of block */
    private static final Vector3D offsetCenter = new Vector3D(0.5,0.5,0.5);
    
    PatchDefinition() {
        x0 = y0 = z0 = 0.0;
        xu = zu = 0.0; yu = 1.0;
        yv = zv = 0.0; xv = 1.0;
        umin = vmin = 0.0;
        umax = vmax = 1.0;
        vmaxatumax = 1.0;
        vminatumax = 0.0;
        u = new Vector3D();
        v = new Vector3D();
        sidevis = SideVisible.BOTH;
        textureindex = 0;
        shade = true;
        update();
    }
    PatchDefinition(PatchDefinition pd) {
        this.x0 = pd.x0;
        this.y0 = pd.y0;
        this.z0 = pd.z0;
        this.xu = pd.xu;
        this.yu = pd.yu;
        this.zu = pd.zu;
        this.xv = pd.xv;
        this.yv = pd.yv;
        this.zv = pd.zv;
        this.umin = pd.umin;
        this.vmin = pd.vmin;
        this.umax = pd.umax;
        this.vmax = pd.vmax;
        this.vmaxatumax = pd.vmaxatumax;
        this.vminatumax = pd.vminatumax;
        this.u = new Vector3D(pd.u);
        this.v = new Vector3D(pd.v);
        this.sidevis = pd.sidevis;
        this.textureindex = pd.textureindex;
        this.step = pd.step;
        this.shade = pd.shade;
        this.hc = pd.hc;
    }
    /**
     * Construct patch, based on rotation of existing patch clockwise by N
     * 90 degree steps
     * @param orig - original patch to copy and rotate
     * @param rotatex - x rotation in degrees
     * @param rotatey - y rotation in degrees
     * @param rotatez - z rotation in degrees
     * @param textureindex - texture index for new patch (-1 = use same as original patch)
     */
    PatchDefinition(PatchDefinition orig, double rotatex, double rotatey, double rotatez, int textureindex) {
    	this(orig, rotatex, rotatey, rotatez, null, textureindex);
    }
    /**
     * Construct patch, based on rotation of existing patch clockwise by N
     * 90 degree steps
     * @param orig - original patch to copy and rotate
     * @param rotatex - x rotation in degrees
     * @param rotatey - y rotation in degrees
     * @param rotatez - z rotation in degrees
     * @param rotorigin - rotation origin (x, y, z)
     * @param textureindex - texture index for new patch (-1 = use same as original patch)
     */
    PatchDefinition(PatchDefinition orig, double rotatex, double rotatey, double rotatez, Vector3D rotorigin, int textureindex) {
    	if (rotorigin == null) rotorigin = offsetCenter;
        Vector3D vec = new Vector3D(orig.x0, orig.y0, orig.z0);
        rotate(vec, rotatex, rotatey, rotatez, rotorigin); /* Rotate origin */
        x0 = vec.x; y0 = vec.y; z0 = vec.z;
        /* Rotate U */
        vec.x = orig.xu; vec.y = orig.yu; vec.z = orig.zu;
        rotate(vec, rotatex, rotatey, rotatez, rotorigin); /* Rotate origin */
        xu = vec.x; yu = vec.y; zu = vec.z;
        /* Rotate V */
        vec.x = orig.xv; vec.y = orig.yv; vec.z = orig.zv;
        rotate(vec, rotatex, rotatey, rotatez, rotorigin); /* Rotate origin */
        xv = vec.x; yv = vec.y; zv = vec.z;
        umin = orig.umin; vmin = orig.vmin;
        umax = orig.umax; vmax = orig.vmax;
        vmaxatumax = orig.vmaxatumax;
        vminatumax = orig.vminatumax;
        sidevis = orig.sidevis;
        shade = orig.shade;
        this.textureindex = (textureindex < 0) ? orig.textureindex : textureindex;
        u = new Vector3D();
        v = new Vector3D();
        update();
    }
    
    private void rotate(Vector3D vec, double xcnt, double ycnt, double zcnt, Vector3D origin) {
    	// If no rotation, skip
    	if ((xcnt == 0) && (ycnt == 0) && (zcnt == 0)) return;
        vec.subtract(origin); /* Shoft to center of block */
        /* Do X rotation */
        double rot = Math.toRadians(xcnt);
        double nval = vec.z * Math.sin(rot) + vec.y * Math.cos(rot);
        vec.z = vec.z * Math.cos(rot) - vec.y * Math.sin(rot);
        vec.y = nval;
        /* Do Y rotation */
        rot = Math.toRadians(ycnt);
        nval = vec.x * Math.cos(rot) - vec.z * Math.sin(rot);
        vec.z = vec.x * Math.sin(rot) + vec.z * Math.cos(rot);
        vec.x = nval;
        /* Do Z rotation */
        rot = Math.toRadians(zcnt);
        nval = vec.y * Math.sin(rot) + vec.x * Math.cos(rot);
        vec.y = vec.y * Math.cos(rot) - vec.x * Math.sin(rot);
        vec.x = nval;
        vec.add(origin); /* Shoft back to corner */
    }
    public void update(double x0, double y0, double z0, double xu,
            double yu, double zu, double xv, double yv, double zv, double umin,
            double umax, double vmin, double vmax, SideVisible sidevis,
            int textureids, double vminatumax, double vmaxatumax, boolean shade) {
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.xu = xu;
        this.yu = yu;
        this.zu = zu;
        this.xv = xv;
        this.yv = yv;
        this.zv = zv;
        this.umin = umin;
        this.umax = umax;
        this.vmin = vmin;
        this.vmax = vmax;
        this.vmaxatumax = vmaxatumax;
        this.vminatumax = vminatumax;
        this.sidevis = sidevis;
        this.textureindex = textureids;
        this.shade = shade;
        update();
    }
    public void update() {
        u.x = xu - x0; u.y = yu - y0; u.z = zu - z0;
        v.x = xv - x0; v.y = yv - y0; v.z = zv - z0;
        /* Compute hash code */
        hc = (int)((Double.doubleToLongBits(x0 + xu + xv) >> 32) ^
                (Double.doubleToLongBits(y0 + yu + yv) >> 34) ^
                (Double.doubleToLongBits(z0 + yu + yv) >> 36) ^
                (Double.doubleToLongBits(umin + umax + vmin + vmax + vmaxatumax) >> 38)) ^
                (sidevis.ordinal() << 8) ^ textureindex;
        /* Now compute normal of surface - U cross V */
        double crossx = (u.y*v.z) - (u.z*v.y);
        double crossy = (u.z*v.x) - (u.x*v.z);
        double crossz = (u.x*v.y) - (u.y*v.x); 
        /* Now, find the largest component of the normal (dominant direction) */
        if(Math.abs(crossx) > (Math.abs(crossy)*0.9)) { /* If X > 0.9Y */
            if(Math.abs(crossx) > Math.abs(crossz)) { /* If X > Z */
                if(crossx > 0) {
                    step = BlockStep.X_PLUS;
                }
                else {
                    step = BlockStep.X_MINUS;
                }
            }
            else { /* Else Z >= X */
                if(crossz > 0) {
                    step = BlockStep.Z_PLUS;
                }
                else {
                    step = BlockStep.Z_MINUS;
                }
            }
        }
        else { /* Else Y >= X */
            if((Math.abs(crossy)*0.9) > Math.abs(crossz)) { /* If 0.9Y > Z */
                if(crossy > 0) {
                    step = BlockStep.Y_PLUS;
                }
                else {
                    step = BlockStep.Y_MINUS;
                }
            }
            else { /* Else Z >= Y */
                if(crossz > 0) {
                    step = BlockStep.Z_PLUS;
                }
                else {
                    step = BlockStep.Z_MINUS;
                }
            }
        }        
    }
    private boolean outOfRange(double v) {
    	return (v < -1.0) || (v > 2.0);
    }
    public boolean validate() {
        boolean good = true;
        // Compute visible corners to see if we're inside cube
        double xx0 = x0 + (xu - x0) * umin + (xv - x0) * vmin;
        double xx1 = x0 + (xu - x0) * vmin + (xv - x0) * vmax;
        double xx2 = x0 + (xu - x0) * umax + (xv - x0) * vmin;
        double xx3 = x0 + (xu - x0) * vmax + (xv - x0) * vmax;;
        if (outOfRange(xx0) || outOfRange(xx1) || outOfRange(xx2) || outOfRange(xx3)) {
            Log.verboseinfo(String.format("Invalid visible range xu=[%f:%f], xv=[%f:%f]", xx0, xx2, xx1, xx3));
            good = false;        	
        }
        double yy0 = y0 + (yu - y0) * umin + (yv - y0) * vmin;
        double yy1 = y0 + (yu - y0) * vmin + (yv - y0) * vmax;
        double yy2 = y0 + (yu - y0) * umax + (yv - y0) * vmin;
        double yy3 = y0 + (yu - y0) * vmax + (yv - y0) * vmax;;
        if (outOfRange(yy0) || outOfRange(yy1) || outOfRange(yy2) || outOfRange(yy3)) {
            Log.verboseinfo(String.format("Invalid visible range yu=[%f:%f], yv=[%f:%f]", yy0, yy2, yy1, yy3));
            good = false;        	
        }
        double zz0 = z0 + (zu - z0) * umin + (zv - z0) * vmin;
        double zz1 = z0 + (zu - z0) * vmin + (zv - z0) * vmax;
        double zz2 = z0 + (zu - z0) * umax + (zv - z0) * vmin;
        double zz3 = z0 + (zu - z0) * vmax + (zv - z0) * vmax;
        if (outOfRange(zz0) || outOfRange(zz1) || outOfRange(zz2) || outOfRange(zz3)) {
            Log.verboseinfo(String.format("Invalid visible range zu=[%f:%f], zv=[%f:%f]", zz0, zz2, zz1, zz3));
            good = false;        	
        }
        if (!good) {
        	Log.verboseinfo("Bad patch: " + this);
        }
        return good;
    }
    @Override
    public boolean equals(Object o) {
        if(o == this)
            return true;
        if(o instanceof PatchDefinition) {
            PatchDefinition p = (PatchDefinition)o;
            if((hc == p.hc) && (textureindex == p.textureindex) && 
                    (x0 == p.x0) && (y0 == p.y0) && (z0 == p.z0) &&
                    (xu == p.xu) && (yu == p.yu) && (zu == p.zu) &&
                    (xv == p.xv) && (yv == p.yv) && (zv == p.zv) && 
                    (umin == p.umin) && (umax == p.umax) &&
                    (vmin == p.vmin) && (vmax == p.vmax) &&
                    (vmaxatumax == p.vmaxatumax) && 
                    (vminatumax == p.vminatumax) && (sidevis == p.sidevis) &&
                    (shade == p.shade)) {
                return true;
            }
        }
        return false;
    }
    @Override
    public int hashCode() {
        return hc;
    }
    @Override
    public int getTextureIndex() {
        return textureindex;
    }
    @Override
    public String toString() {
    	return String.format("xyz0=%f/%f/%f,xyzU=%f/%f/%f,xyzV=%f/%f/%f,minU=%f,maxU=%f,vMin=%f/%f,vmax=%f/%f,side=%s,txtidx=%d,shade=%b",
    			x0, y0, z0, xu, yu, zu, xv, yv, zv, umin, umax, vmin, vminatumax, vmax, vmaxatumax, sidevis, textureindex, shade);
    }
    
    //
    // Update patch relative to typical parameters found in
    // minecraft model files.  Specifically, all coordinates are relative to 0-16 range for
    // side of a cube, and relative to 0-16 range for U,V within a texture:
    //
    //   from, to in model drive 'from', 'to' inputs
    //   face, uv of face, and texture in model drives face, uv, textureid
    //
    // @param from - vector of lower left corner of box (0-16 range for coordinates - min x, y, z)
    // @param to - vector of upper right corner of box (0-16 range for coordinates max x, y, z)
    // @param face - which face (determines use of xyz-min vs xyz-max
    // @param uv - bounds on UV (umin, vmin, umax, vmax): if undefined, default based on face range (minecraft UV is relative to top left corner of texture)
    // @param rot - texture rotation (default 0 - DEG0, DEG90, DEG180, DEG270)
    // @param shade - if false, no shadows on patch
    // @param textureid - texture ID
    public void updateModelFace(double[] from, double[] to, BlockSide face, double[] uv, ModelBlockModel.SideRotation rot, boolean shade, int textureid) {
    	if (rot == null) rot = ModelBlockModel.SideRotation.DEG0;
    	// Compute corners of the face
    	Vector3D lowleft;
    	Vector3D lowright;
    	Vector3D upleft;
    	Vector3D upright;
    	// Default UV, if not defined
    	double[] patchuv = null;
    	boolean flipU = false, flipV = false;
    	if (uv != null) {	// MC V is top down, so flip
    		patchuv = new double[] { uv[0] / 16.0, 1 - uv[3] / 16.0, uv[2] / 16.0, 1 - uv[1] / 16.0 }; 
//    		if (patchuv[0] > patchuv[2]) { flipU = true; double save = patchuv[0]; patchuv[0] = patchuv[2]; patchuv[2] = save; }
//    		if (patchuv[1] > patchuv[3]) { flipV = true; double save = patchuv[1]; patchuv[1] = patchuv[3]; patchuv[3] = save; }
    		if (patchuv[0] > patchuv[2]) { flipU = true; patchuv[0] = 1.0 - patchuv[0]; patchuv[2] = 1.0 - patchuv[2]; }
    		if (patchuv[1] > patchuv[3]) { flipV = true; patchuv[1] = 1.0 - patchuv[1]; patchuv[3] = 1.0 - patchuv[3]; }
    	}
    	
    	switch (face) {
    		case BOTTOM:
			case FACE_0:
			case Y_MINUS:
		    	// Bottom - Y-negative (top towards south (+Z), right towards east (+x))
				lowleft = new Vector3D(from[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				lowright = new Vector3D(to[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				upleft = new Vector3D(from[0] / 16.0, from[1] / 16.0, to[2] / 16.0);
				upright = new Vector3D(to[0] / 16.0, from[1] / 16.0, to[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { from[0] / 16.0, from[2] / 16.0, to[0] / 16.0, to[2] / 16.0 };
				}
				break;
			case TOP:
			case FACE_1:
			case Y_PLUS:
				// Top - Y-positive  (top towards north (-Z), right towards east (+x))
				lowleft = new Vector3D(from[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				lowright = new Vector3D(to[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				upleft = new Vector3D(from[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				upright = new Vector3D(to[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { from[0] / 16.0, 1 - to[2] / 16.0, to[0] / 16.0, 1 - from[2] / 16.0 };
				}
				break;
			case NORTH:
			case FACE_2:
			case Z_MINUS:    			
				// North - Z-negative (top towards up (+Y), right towards west (-X))
				lowleft = new Vector3D(to[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				lowright = new Vector3D(from[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				upleft = new Vector3D(to[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				upright = new Vector3D(from[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { 1 - to[0] / 16.0, from[1] / 16.0, 1 - from[0] / 16.0, to[1] / 16.0 };
				}
				break;
			case SOUTH:
			case FACE_3:
			case Z_PLUS:    			
				// South - Z-positive (top towards up (+Y), right towards east (+X))
				lowleft = new Vector3D(from[0] / 16.0, from[1] / 16.0, to[2] / 16.0);
				lowright = new Vector3D(to[0] / 16.0, from[1] / 16.0,to[2] / 16.0);
				upleft = new Vector3D(from[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				upright = new Vector3D(to[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { from[0] / 16.0, from[1] / 16.0, to[0] / 16.0, to[1] / 16.0 };
				}
				break;
			case WEST:
			case FACE_4:
			case X_MINUS:    			
				// West - X-negative (top towards up (+Y), right towards south (+Z))
				lowleft = new Vector3D(from[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				lowright = new Vector3D(from[0] / 16.0, from[1] / 16.0, to[2] / 16.0);
				upleft = new Vector3D(from[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				upright = new Vector3D(from[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { from[2] / 16.0, from[1] / 16.0, to[2] / 16.0, to[1] / 16.0 };
				}
				break;
			case EAST:
			case FACE_5:
			case X_PLUS:    			
				// East - X-positive (top towards up (+Y), right towards north (-Z))
				lowleft = new Vector3D(to[0] / 16.0, from[1] / 16.0, to[2] / 16.0);
				lowright = new Vector3D(to[0] / 16.0, from[1] / 16.0, from[2] / 16.0);
				upleft = new Vector3D(to[0] / 16.0, to[1] / 16.0, to[2] / 16.0);
				upright = new Vector3D(to[0] / 16.0, to[1] / 16.0, from[2] / 16.0);
				if (patchuv == null) {
					patchuv = new double[] { 1 - to[2] / 16.0, from[1] / 16.0, 1 - from[2] / 16.0, to[1] / 16.0 };
				}
				break;    		
			default:
				Log.severe("Invalid side: " + face);
				return;
    	}
    	// Clamp patchuv to avoid extending off of patch, while maintaining width and height of patch area
    	if (patchuv[0] < 0) { patchuv[2] -= patchuv[0]; patchuv[0] = 0.0; }
    	if (patchuv[1] < 0) { patchuv[3] -= patchuv[1]; patchuv[1] = 0.0; }
    	if (patchuv[2] > 1) { patchuv[0] -= (patchuv[2] - 1); patchuv[2] = 1; }
    	if (patchuv[3] > 1) { patchuv[1] -= (patchuv[3] - 1); patchuv[3] = 1; }
    	// If rotation, rotate face corners
    	if (rot == ModelBlockModel.SideRotation.DEG270) {
    		// 270 degrees CCW - origin is now upper left (V), V is now upper right (U+V-O), U is lower left (O)
    		Vector3D save = lowleft;
    		lowleft = lowright;
    		lowright = upright;
    		upright = upleft;
    		upleft = save;
    	}
    	else if (rot == ModelBlockModel.SideRotation.DEG180) {
    		// 180 degrees CCW - origin is now upper right, U is now upper left (V), V is lower right (U)
    		Vector3D save = lowleft;
    		lowleft = upright;
    		upright = save;
    		save = lowright;
    		lowright = upleft;
    		upleft = save;
    	}
    	else if (rot == ModelBlockModel.SideRotation.DEG90) {
    		// 90 degrees CCW - origin is now lower right (V), U is now upper right (topright), V is lower right (O)
    		Vector3D save = lowright;
    		lowright = lowleft;
    		lowleft = upleft;
    		upleft = upright;
    		upright = save;
    	}
    	// Compute texture origin, based on corners and patchuv
    	Vector3D txtorig = new Vector3D();
    	Vector3D txtU = new Vector3D();
    	Vector3D txtV = new Vector3D();
    	Vector3D wrk = new Vector3D();
    	// If nonzero texture size
    	if ((patchuv[0] != patchuv[2]) && (patchuv[1] != patchuv[3])) {
        	// Get scale along U axis
        	double du = patchuv[2] - patchuv[0];
        	txtU.set(lowright).subtract(lowleft);	// vector along U 
        	double uScale = txtU.length() / du;
        	txtU.scale(uScale / txtU.length());	// Compute full U vect
        	// Compute V axis
        	double dv = patchuv[3] - patchuv[1];
        	txtV.set(upleft).subtract(lowleft);	// vector along V
        	double vScale = txtV.length() / dv;
        	txtV.scale(vScale / txtV.length());	// Compute full V vect
        	// Compute texture origin
        	txtorig.set(txtU).scale(-patchuv[0]).add(lowleft);
        	wrk.set(txtV).scale(-patchuv[1]);
        	txtorig.add(wrk);
        	// Compute full U and V
        	txtU.add(txtorig);	// And add it for full U
        	txtV.add(txtorig);	// And add it to compute full V 	
    	}
    	update(txtorig.x, txtorig.y, txtorig.z, txtU.x, txtU.y, txtU.z, txtV.x, txtV.y, txtV.z,
    		patchuv[0], patchuv[2], patchuv[1], patchuv[3], flipU ? (flipV ? SideVisible.TOPFLIPHV : SideVisible.TOPFLIP) : (flipV ? SideVisible.TOPFLIPV : SideVisible.TOP), textureid,
			patchuv[1], patchuv[3], shade);
    }
}
