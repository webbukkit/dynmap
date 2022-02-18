package org.dynmap.storage.aws_s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dynmap.DynmapCore;
import org.dynmap.DynmapWorld;
import org.dynmap.Log;
import org.dynmap.MapType;
import org.dynmap.MapType.ImageEncoding;
import org.dynmap.MapType.ImageVariant;
import org.dynmap.PlayerFaces.FaceType;
import org.dynmap.WebAuthManager;
import org.dynmap.storage.MapStorage;
import org.dynmap.storage.MapStorageTile;
import org.dynmap.storage.MapStorageTileEnumCB;
import org.dynmap.storage.MapStorageBaseTileEnumCB;
import org.dynmap.storage.MapStorageTileSearchEndCB;
import org.dynmap.utils.BufferInputStream;
import org.dynmap.utils.BufferOutputStream;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class AWSS3MapStorage extends MapStorage {
    public class StorageTile extends MapStorageTile {
        private final String baseKey;
        
        StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);
            String baseURI;
            if (zoom > 0) {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + "zzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y;
            }
            else {
                baseURI = map.getPrefix() + var.variantSuffix + "/"+ (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y;
            }
            baseKey = "tiles/" + world.getName() + "/" + baseURI + "." + map.getImageFormat().getFileExt();
        }
        @Override
        public boolean exists() {
        	boolean exists = false;
        	try {
        		AccessControlList rslt = s3.getObjectAcl(bucketname, baseKey);
        		if (rslt != null)
        			exists = true;
            } catch (AmazonServiceException x) {
            	Log.severe("AWS Exception", x);
        	}
            return exists;
        }

        @Override
        public boolean matchesHashCode(long hash) {
        	return false;
        }

        @Override
        public TileRead read() {
        	AWSS3MapStorage.this.getWriteLock(baseKey);
        	try {
    			S3Object obj = s3.getObject(bucketname, baseKey);
    			if (obj != null) {
    				ObjectMetadata md = obj.getObjectMetadata();
                    TileRead tr = new TileRead();
                    byte[] buf = new byte[(int) md.getContentLength()];
                    InputStream fis = obj.getObjectContent();
                    fis.read(buf, 0, buf.length);   // Read whole thing
                    tr.image = new BufferInputStream(buf);
                    tr.format = ImageEncoding.fromContentType(md.getContentType());
                    tr.hashCode = md.getContentMD5().hashCode();
                    tr.lastModified = md.getLastModified().getTime();
                    
                    return tr;
    			}
            } catch (IOException x) {
            	Log.severe("AWS Exception", x);
            } catch (AmazonServiceException x) {
            	Log.severe("AWS Exception", x);
        	} finally {
        		AWSS3MapStorage.this.releaseWriteLock(baseKey);
        	}
        	return null;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage, long timestamp) {
        	boolean done = false;
        	AWSS3MapStorage.this.getWriteLock(baseKey);
        	try {
        		if (encImage == null) { // Delete?
        			s3.deleteObject(bucketname, baseKey);
        		}
        		else {
        			ObjectMetadata md = new ObjectMetadata();
        			md.setContentType(map.getImageFormat().getEncoding().getContentType());
        			s3.putObject(bucketname, baseKey, new ByteArrayInputStream(encImage.buf), md);
        		}
    			done = true;
            } catch (AmazonServiceException x) {
            	Log.severe("AWS Exception", x);
        	} finally {
        		AWSS3MapStorage.this.releaseWriteLock(baseKey);
        	}
            // Signal update for zoom out
            if (zoom == 0) {
                world.enqueueZoomOutUpdate(this);
            }
            return done;
        }

        @Override
        public boolean getWriteLock() {
            return AWSS3MapStorage.this.getWriteLock(baseKey);
        }

        @Override
        public void releaseWriteLock() {
            AWSS3MapStorage.this.releaseWriteLock(baseKey);
        }

        @Override
        public boolean getReadLock(long timeout) {
            return AWSS3MapStorage.this.getReadLock(baseKey, timeout);
        }

        @Override
        public void releaseReadLock() {
            AWSS3MapStorage.this.releaseReadLock(baseKey);
        }

        @Override
        public void cleanup() {
        }
        
        @Override
        public String getURI() {
            return null;
        }
        
        @Override
        public void enqueueZoomOutUpdate() {
            world.enqueueZoomOutUpdate(this);
        }
        @Override
        public MapStorageTile getZoomOutTile() {
            int xx, yy;
            int step = 1 << zoom;
            if(x >= 0)
                xx = x - (x % (2*step));
            else
                xx = x + (x % (2*step));
            yy = -y;
            if(yy >= 0)
                yy = yy - (yy % (2*step));
            else
                yy = yy + (yy % (2*step));
            yy = -yy;
            return new StorageTile(world, map, xx, yy, zoom+1, var);
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof StorageTile) {
                StorageTile st = (StorageTile) o;
                return baseKey.equals(st.baseKey);
            }
            return false;
        }
        @Override
        public int hashCode() {
            return baseKey.hashCode();
        }
        @Override
        public String toString() {
            return baseKey;
        }
    }
    
    private String bucketname;
    private String region;
    private String profile_id;
    private AmazonS3 s3;

    public AWSS3MapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        if (!core.isInternalWebServerDisabled) {
        	Log.severe("AWS S3 storage is not supported option with internal web server: set disable-webserver: true in configuration.txt");
            return false;
        }
        // Get our settings
        bucketname = core.configuration.getString("storage/bucketname", "dynmap");
        region = core.configuration.getString("storage/region", "us-east-1");
        profile_id = core.configuration.getString("storage/aws_profile_id", System.getenv("AWS_PROFILE"));
        try {
	        // Now creste the access client for the S3 service
	        Log.info("Using AWS S3 storage: web site at S3 bucket " + bucketname + " in region " + region + " using AWS_PROFILE_ID=" + profile_id);
	        s3 = AmazonS3ClientBuilder.standard().withRegion(region)
	                //.withCredentials(new ProfileCredentialsProvider("profile " + profile_id))
	                .build();        
	        if (s3 == null) {
	        	Log.severe("Error creating S3 access client");      
	        	return false;
	        }
	        // Make sure bucket exists and get ACL
	        AccessControlList bucketACL = s3.getBucketAcl(bucketname);
	        if (bucketACL == null) {
	        	Log.severe("Error: cannot find or access S3 bucket");
	        	return false;
	        }
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
        	return false;
        }
        return true;
    }
    
    @Override
    public MapStorageTile getTile(DynmapWorld world, MapType map, int x, int y,
            int zoom, ImageVariant var) {
        return new StorageTile(world, map, x, y, zoom, var);
    }
    
    @Override
    public MapStorageTile getTile(DynmapWorld world, String uri) {
        String[] suri = uri.split("/");
        if (suri.length < 2) return null;
        String mname = suri[0]; // Map URI - might include variant
        MapType mt = null;
        ImageVariant imgvar = null;
        // Find matching map type and image variant
        for (int mti = 0; (mt == null) && (mti < world.maps.size()); mti++) {
            MapType type = world.maps.get(mti);
            ImageVariant[] var = type.getVariants();
            for (int ivi = 0; (imgvar == null) && (ivi < var.length); ivi++) {
                if (mname.equals(type.getPrefix() + var[ivi].variantSuffix)) {
                    mt = type;
                    imgvar = var[ivi];
                }
            }
        }
        if (mt == null) {   // Not found?
            return null;
        }
        // Now, take the last section and parse out coordinates and zoom
        String fname = suri[suri.length-1];
        String[] coord = fname.split("[_\\.]");
        if (coord.length < 3) { // 3 or 4
            return null;
        }
        int zoom = 0;
        int x, y;
        try {
            if (coord[0].charAt(0) == 'z') {
                zoom = coord[0].length();
                x = Integer.parseInt(coord[1]);
                y = Integer.parseInt(coord[2]);
            }
            else {
                x = Integer.parseInt(coord[0]);
                y = Integer.parseInt(coord[1]);
            }
            return getTile(world, mt, x, y, zoom, imgvar);
        } catch (NumberFormatException nfx) {
            return null;
        }
    }


    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
    	String basekey = "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
    	try {
    		ListObjectsV2Result result = s3.listObjectsV2(bucketname, basekey);
    		List<S3ObjectSummary> objects = result.getObjectSummaries();
    		for (S3ObjectSummary os : objects) { 
    			String key = os.getKey();
    			key = key.substring(basekey.length());	// Strip off base
    			// Parse the extension
                String ext = null;
                int extoff = key.lastIndexOf('.');
                if (extoff >= 0) {
                    ext = key.substring(extoff+1);
                    key = key.substring(0, extoff);
                }
                // If not valid image extension, ignore
                ImageEncoding fmt = ImageEncoding.fromExt(ext);
                if (fmt == null) {
                    continue;
                }
                // See if zoom tile: figure out zoom level
                int zoom = 0;
                if (key.startsWith("z")) {
                    while (key.startsWith("z")) {
                        key = key.substring(1);
                        zoom++;
                    }
                    if (key.startsWith("_")) {
                        key = key.substring(1);
                    }
                }
                // Split remainder to get coords
                String[] coord = key.split("_");
                if (coord.length == 2) {    // Must be 2 to be a tile
                    try {
                        int x = Integer.parseInt(coord[0]);
                        int y = Integer.parseInt(coord[1]);
                        // Invoke callback
                        MapStorageTile t = new StorageTile(world, map, x, y, zoom, var);
                        if(cb != null)
                            cb.tileFound(t, fmt);
                        if(cbBase != null && t.zoom == 0)
                            cbBase.tileFound(t, fmt);
                        t.cleanup();
                    } catch (NumberFormatException nfx) {
                    }
                }
    		}
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
        }
        if(cbEnd != null) {
            cbEnd.searchEnded();
        }
    }
    
    @Override
    public void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, cb, null, null);
            }
        }
    }

    @Override
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase, MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processEnumMapTiles(world, mt, var, null, cbBase, cbEnd);
            }
        }
    }

    private void processPurgeMapTiles(DynmapWorld world, MapType map, ImageVariant var) {
    	String basekey = "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
    	try {
    		ListObjectsV2Result result = s3.listObjectsV2(bucketname, basekey);
    		List<S3ObjectSummary> objects = result.getObjectSummaries();
    		ArrayList<String> keys = new ArrayList<String>();
    		for (S3ObjectSummary os : objects) { 
    			String key = os.getKey();
    			keys.add(key);
    			if (keys.size() >= 100) {
    				DeleteObjectsRequest dor = new DeleteObjectsRequest(bucketname).withKeys(keys.toArray(new String[0]));
    			    s3.deleteObjects(dor);
    			    keys.clear();
    			}
    		}
    		// Any left?
			if (keys.size() > 0) {
				DeleteObjectsRequest dor = new DeleteObjectsRequest(bucketname).withKeys(keys.toArray(new String[0]));
			    s3.deleteObjects(dor);
			    keys.clear();
			}
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        }
        else {  // Else, add all directories under world directory (for maps)
            mtlist = new ArrayList<MapType>(world.maps);
        }
        for (MapType mt : mtlist) {
            ImageVariant[] vars = mt.getVariants();
            for (ImageVariant var : vars) {
                processPurgeMapTiles(world, mt, var);
            }
        }
    }

    @Override
    public boolean setPlayerFaceImage(String playername, FaceType facetype,
            BufferOutputStream encImage) {
    	boolean done = false;
    	String baseKey = "faces/" + facetype.id + "/" + playername + ".png";
        getWriteLock(baseKey);
    	try {
    		if (encImage == null) { // Delete?
    			s3.deleteObject(bucketname, baseKey);
    		}
    		else {
    			ObjectMetadata md = new ObjectMetadata();
    			md.setContentType("image/png");
    			s3.putObject(bucketname, baseKey, new ByteArrayInputStream(encImage.buf), md);
    		}
			done = true;
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
    	} finally {
	        releaseWriteLock(baseKey);
    	}
        return done;
    }

    @Override
    public BufferInputStream getPlayerFaceImage(String playername,
            FaceType facetype) {
    	return null;
    }
    
    @Override
    public boolean hasPlayerFaceImage(String playername, FaceType facetype) {
    	String baseKey = "faces/" + facetype.id + "/" + playername + ".png";
    	boolean exists = false;
    	try {
    		AccessControlList rslt = s3.getObjectAcl(bucketname, baseKey);
    		if (rslt != null)
    			exists = true;
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
    	}
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
    	boolean done = false;
    	String baseKey = "_markers_/" + markerid + ".png";
        getWriteLock(baseKey);
    	try {
    		if (encImage == null) { // Delete?
    			s3.deleteObject(bucketname, baseKey);
    		}
    		else {
    			ObjectMetadata md = new ObjectMetadata();
    			md.setContentType("image/png");
    			s3.putObject(bucketname, baseKey, new ByteArrayInputStream(encImage.buf), md);
    		}
			done = true;
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
    	} finally {
	        releaseWriteLock(baseKey);
    	}
        return done;
    }

    @Override
    public BufferInputStream getMarkerImage(String markerid) {
    	return null;
    }

    @Override
    public boolean setMarkerFile(String world, String content) {
    	boolean done = false;
    	String baseKey = "_markers_/marker_" + world + ".json";
        getWriteLock(baseKey);
    	try {
    		if (content == null) { // Delete?
    			s3.deleteObject(bucketname, baseKey);
    		}
    		else {
    			ObjectMetadata md = new ObjectMetadata();
    			md.setContentType("application/json");
    			s3.putObject(bucketname, baseKey, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), md);
    		}
			done = true;
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
    	} finally {
	        releaseWriteLock(baseKey);
    	}
        return done;
    }

    @Override
    public String getMarkerFile(String world) {
    	return null;
    }
    
    @Override
    // For external web server only
    public String getMarkersURI(boolean login_enabled) {
        return login_enabled?"standalone/markers.php?marker=":"tiles/";
    }

    @Override
    // For external web server only
    public String getTilesURI(boolean login_enabled) {
        return login_enabled?"standalone/tiles.php?tile=":"tiles/";
    }
    
    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        String p = core.getTilesFolder().getAbsolutePath();
        if(!p.endsWith("/"))
            p += "/";
        sb.append("$tilespath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        sb.append("$markerspath = \'");
        sb.append(WebAuthManager.esc(p));
        sb.append("\';\n");
        
        // Need to call base to add webpath
        super.addPaths(sb, core);
    }


    @Override
    public BufferInputStream getStandaloneFile(String fileid) {
    	return null;
    }

    @Override
    public boolean setStandaloneFile(String fileid, BufferOutputStream content) {
    	
    	boolean done = false;
    	String baseKey = "standalone/" + fileid;
        getWriteLock(baseKey);
    	try {
    		if (content == null) { // Delete?
    			s3.deleteObject(bucketname, baseKey);
    		}
    		else {
    			ObjectMetadata md = new ObjectMetadata();
    			md.setContentType("text/plain");
    			s3.putObject(bucketname, baseKey, new ByteArrayInputStream(content.buf), md);
    		}
			done = true;
        } catch (AmazonServiceException x) {
        	Log.severe("AWS Exception", x);
    	} finally {
	        releaseWriteLock(baseKey);
    	}
        return done;

    }
}
