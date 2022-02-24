package org.dynmap.storage.aws_s3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import io.github.linktosriram.s3lite.api.client.S3Client;
import io.github.linktosriram.s3lite.api.exception.NoSuchKeyException;
import io.github.linktosriram.s3lite.api.exception.S3Exception;
import io.github.linktosriram.s3lite.api.region.Region;
import io.github.linktosriram.s3lite.api.request.DeleteObjectRequest;
import io.github.linktosriram.s3lite.api.request.GetObjectRequest;
import io.github.linktosriram.s3lite.api.request.ListObjectsV2Request;
import io.github.linktosriram.s3lite.api.request.PutObjectRequest;
import io.github.linktosriram.s3lite.api.response.GetObjectResponse;
import io.github.linktosriram.s3lite.api.response.ListObjectsV2Response;
import io.github.linktosriram.s3lite.api.response.ResponseBytes;
import io.github.linktosriram.s3lite.api.response.S3Object;
import io.github.linktosriram.s3lite.core.auth.AwsBasicCredentials;
import io.github.linktosriram.s3lite.core.client.DefaultS3ClientBuilder;
import io.github.linktosriram.s3lite.http.spi.request.RequestBody;
import io.github.linktosriram.s3lite.http.urlconnection.URLConnectionSdkHttpClient;

public class AWSS3MapStorage extends MapStorage {
    public class StorageTile extends MapStorageTile {
        private final String baseKey;
        private final String uri;
        
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
            uri = baseURI + "." + map.getImageFormat().getFileExt();
            baseKey = AWSS3MapStorage.this.prefix + "tiles/" + world.getName() + "/" + uri;
        }
        @Override
        public boolean exists() {
        	boolean exists = false;
        	S3Client s3 = getConnection();
        	try {
        		ListObjectsV2Request req = ListObjectsV2Request.builder().bucketName(bucketname).prefix(baseKey).maxKeys(1).build();
        	    ListObjectsV2Response rslt = s3.listObjectsV2(req);
        		if ((rslt != null) && (rslt.getKeyCount() > 0))
        			exists = true;
            } catch (S3Exception x) {
            	if (!x.getCode().equals("SignatureDoesNotMatch")) {	// S3 behavior when no object match....
            		Log.severe("AWS Exception", x);
            	}
        	} finally {
        		releaseConnection(s3);
        	}
            return exists;
        }

        @Override
        public boolean matchesHashCode(long hash) {
        	return false;
        }

        @Override
        public TileRead read() {
        	S3Client s3 = getConnection();
        	try {
        		GetObjectRequest req = GetObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
    			ResponseBytes<GetObjectResponse> obj = s3.getObjectAsBytes(req);
    			if (obj != null) {
    				GetObjectResponse rsp = obj.getResponse();
                    TileRead tr = new TileRead();
                    byte[] buf = obj.getBytes();
                    tr.image = new BufferInputStream(buf);
                    tr.format = ImageEncoding.fromContentType(rsp.getContentType());
                    Map<String, String> meta = rsp.getMetadata();
                    String v = meta.get("x-dynmap-hash");
                    if (v != null) {
                    	tr.hashCode = Long.parseLong(v, 16);
                    }
                    v = meta.get("x-dynmap-ts");
                    if (v != null) {
                    	tr.lastModified = Long.parseLong(v);
                    }
                    return tr;
    			}
        	} catch (NoSuchKeyException nskx) {
        		return null;	// Nominal case if it doesn't exist
            } catch (S3Exception x) {
        		Log.severe("AWS Exception", x);
        	} finally {
        		releaseConnection(s3);
        	}
        	return null;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage, long timestamp) {
        	boolean done = false;
        	S3Client s3 = getConnection();
        	try {
        		if (encImage == null) { // Delete?
        			DeleteObjectRequest req = DeleteObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
        			s3.deleteObject(req);
        		}
        		else {
        			PutObjectRequest req = PutObjectRequest.builder().bucketName(bucketname).key(baseKey).contentType(map.getImageFormat().getEncoding().getContentType())
        					.addMetadata("x-dynmap-hash", Long.toHexString(hash)).addMetadata("x-dynmap-ts", Long.toString(timestamp)).build();
        			s3.putObject(req, RequestBody.fromBytes(encImage.buf, encImage.len));
        		}
    			done = true;
            } catch (S3Exception x) {
            	Log.severe("AWS Exception", x);
        	} finally {
        		releaseConnection(s3);
        	}
            // Signal update for zoom out
            if (zoom == 0) {
                world.enqueueZoomOutUpdate(this);
            }
            return done;
        }

        @Override
        public boolean getWriteLock() {
            return true;
        }

        @Override
        public void releaseWriteLock() {
        }

        @Override
        public boolean getReadLock(long timeout) {
            return true;
        }

        @Override
        public void releaseReadLock() {
        }

        @Override
        public void cleanup() {
        }
        
        @Override
        public String getURI() {
            return uri;
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
    private String access_key_id;
    private String secret_access_key;
    private String prefix;

    private int POOLSIZE = 4;
    private int cpoolCount = 0;
    private S3Client[] cpool = new S3Client[POOLSIZE];
    
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
        if (core.isLoginSupportEnabled()) {
        	Log.severe("AWS S3 storage is not supported option with loegin support enabled: set login-enabled: false in configuration.txt");
            return false;        	
        }
        // Get our settings
        bucketname = core.configuration.getString("storage/bucketname", "dynmap");
        region = core.configuration.getString("storage/region", "us-east-1");
        access_key_id = core.configuration.getString("storage/aws_access_key_id", System.getenv("AWS_ACCESS_KEY_ID"));
        secret_access_key = core.configuration.getString("storage/aws_secret_access_key", System.getenv("AWS_SECRET_ACCESS_KEY"));
        prefix = core.configuration.getString("storage/prefix", "");
        if ((prefix.length() > 0) && (prefix.charAt(prefix.length()-1) != '/')) {
        	prefix += '/';
        }
        // Now creste the access client for the S3 service
        Log.info("Using AWS S3 storage: web site at S3 bucket " + bucketname + " in region " + region);
        S3Client s3 = getConnection();
        if (s3 == null) {
        	Log.severe("Error creating S3 access client");      
        	return false;
        }
        try {
	        // Make sure bucket exists (do list)
	        ListObjectsV2Request listreq = ListObjectsV2Request.builder()
	        		.bucketName(bucketname)
	        		.maxKeys(1)
	        		.prefix(prefix)
	        		.build();
	        ListObjectsV2Response rslt = s3.listObjectsV2(listreq);
	        if (rslt == null) {
	        	Log.severe("Error: cannot find or access S3 bucket");
	        	return false;
	        }
	        rslt.getContents();
        } catch (S3Exception s3x) {
    		Log.severe("AWS Exception", s3x);
    		return false;
        } finally {
    		releaseConnection(s3);
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


    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb, MapStorageBaseTileEnumCB cbBase, 
		MapStorageTileSearchEndCB cbEnd) {
    	String basekey = prefix + "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
    	ListObjectsV2Request req = ListObjectsV2Request.builder().bucketName(bucketname).prefix(basekey).maxKeys(1000).build();
    	boolean done = false;
    	S3Client s3 = getConnection();
    	try {
        	while (!done) {
        		ListObjectsV2Response result = s3.listObjectsV2(req);
        		List<S3Object> objects = result.getContents();
	    		for (S3Object os : objects) { 
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
	    		if (result.isTruncated()) {	// If more, build continuiation request
	    	    	req = ListObjectsV2Request.builder().bucketName(bucketname)
	    	    			.prefix(basekey).delimiter("").maxKeys(1000).continuationToken(result.getContinuationToken()).encodingType("url").requestPayer("requester").build();
	    		}
	    		else {	// Else, we're done
	    			done = true;
	    		}
        	}
        } catch (S3Exception x) {
        	if (!x.getCode().equals("SignatureDoesNotMatch")) {	// S3 behavior when no object match....
	        	Log.severe("AWS Exception", x);
	        	Log.severe("req=" + req);
        	}
        } finally {
    		releaseConnection(s3);
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
    	String basekey = prefix + "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
		ListObjectsV2Request req = ListObjectsV2Request.builder().bucketName(bucketname).prefix(basekey).delimiter("").maxKeys(1000).encodingType("url").requestPayer("requester").build();
		S3Client s3 = getConnection();
    	try {
    		boolean done = false;
    		while (!done) {
	    		ListObjectsV2Response result = s3.listObjectsV2(req);
	    		List<S3Object> objects = result.getContents();
	    		for (S3Object os : objects) { 
	    			String key = os.getKey();
	    			DeleteObjectRequest delreq = DeleteObjectRequest.builder().bucketName(bucketname).key(key).build();
	    			s3.deleteObject(delreq);
	    		}
	    		if (result.isTruncated()) {	// If more, build continuiation request
	    	    	req = ListObjectsV2Request.builder().bucketName(bucketname)
	    	    			.prefix(basekey).delimiter("").maxKeys(1000).continuationToken(result.getContinuationToken()).encodingType("url").requestPayer("requester").build();
	    		}
	    		else {	// Else, we're done
	    			done = true;
	    		}
    		}
        } catch (S3Exception x) {
        	if (!x.getCode().equals("SignatureDoesNotMatch")) {	// S3 behavior when no object match....
	        	Log.severe("AWS Exception", x);
	        	Log.severe("req=" + req);
        	}
        } finally {
    		releaseConnection(s3);
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
    	String baseKey = prefix + "tiles/faces/" + facetype.id + "/" + playername + ".png";
    	S3Client s3 = getConnection();
    	try {
    		if (encImage == null) { // Delete?
				DeleteObjectRequest delreq = DeleteObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
			    s3.deleteObject(delreq);
    		}
    		else {
    			PutObjectRequest req = PutObjectRequest.builder().bucketName(bucketname).key(baseKey).contentType("image/png").build();
    			s3.putObject(req, RequestBody.fromBytes(encImage.buf, encImage.len));
    		}
			done = true;
        } catch (S3Exception x) {
        	Log.severe("AWS Exception", x);
    	} finally {
    		releaseConnection(s3);
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
    	String baseKey = prefix + "tiles/faces/" + facetype.id + "/" + playername + ".png";
    	boolean exists = false;
    	S3Client s3 = getConnection();
    	try {
    		ListObjectsV2Request req = ListObjectsV2Request.builder().bucketName(bucketname).prefix(baseKey).maxKeys(1).build();
    	    ListObjectsV2Response rslt = s3.listObjectsV2(req);
    		if ((rslt != null) && (rslt.getKeyCount() > 0))
    			exists = true;
        } catch (S3Exception x) {
        	if (!x.getCode().equals("SignatureDoesNotMatch")) {	// S3 behavior when no object match....
        		Log.severe("AWS Exception", x);
        	}
    	} finally {
    		releaseConnection(s3);
    	}
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
    	boolean done = false;
    	String baseKey = prefix + "tiles/_markers_/" + markerid + ".png";
    	S3Client s3 = getConnection();
    	try {
    		if (encImage == null) { // Delete?
				DeleteObjectRequest delreq = DeleteObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
			    s3.deleteObject(delreq);
    		}
    		else {
       			PutObjectRequest req = PutObjectRequest.builder().bucketName(bucketname).key(baseKey).contentType("image/png").build();
    			s3.putObject(req, RequestBody.fromBytes(encImage.buf, encImage.len));
    		}
			done = true;
        } catch (S3Exception x) {
        	Log.severe("AWS Exception", x);
    	} finally {
    		releaseConnection(s3);
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
    	String baseKey = prefix + "tiles/_markers_/marker_" + world + ".json";
    	S3Client s3 = getConnection();
    	try {
    		if (content == null) { // Delete?
				DeleteObjectRequest delreq = DeleteObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
			    s3.deleteObject(delreq);
    		}
    		else {
       			PutObjectRequest req = PutObjectRequest.builder().bucketName(bucketname).key(baseKey).contentType("application/json").build();
    			s3.putObject(req, RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
    		}
			done = true;
        } catch (S3Exception x) {
        	Log.severe("AWS Exception", x);
    	} finally {
    		releaseConnection(s3);
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
        return "tiles/";
    }

    @Override
    // For external web server only
    public String getTilesURI(boolean login_enabled) {
        return "tiles/";
    }
    
    /**
     * URI to use for loading configuration JSON files (for external web server only)
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getConfigurationJSONURI(boolean login_enabled) {
        return "standalone/dynmap_config.json?_={timestamp}";
    }
    /**
     * URI to use for loading update JSON files (for external web server only)
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getUpdateJSONURI(boolean login_enabled) {
        return "standalone/dynmap_{world}.json?_={timestamp}";
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

    
    // Cache to avoid rewriting same standalong file repeatedly
    private ConcurrentHashMap<String, byte[]> standalone_cache = new ConcurrentHashMap<String, byte[]>();
    
    @Override
    public boolean setStandaloneFile(String fileid, BufferOutputStream content) {
    	return setStaticWebFile("standalone/" + fileid, content);
    }
    // Test if storage needs static web files
    public boolean needsStaticWebFiles() {
    	return true;
    }
    /**
     * Set static web file content
     * @param fileid - file path
     * @param content - content for file
     * @return true if successful
     */
    public boolean setStaticWebFile(String fileid, BufferOutputStream content) {
    	
    	boolean done = false;
    	String baseKey = prefix + fileid;
    	S3Client s3 = getConnection();
    	try {
    		byte[] cacheval = standalone_cache.get(fileid);
    		
    		if (content == null) { // Delete?
    			if ((cacheval != null) && (cacheval.length == 0)) {	// Delete cached?
    				return true;
    			}
				DeleteObjectRequest delreq = DeleteObjectRequest.builder().bucketName(bucketname).key(baseKey).build();
			    s3.deleteObject(delreq);
			    standalone_cache.put(fileid, new byte[0]);	// Mark in cache
    		}
    		else {
    			byte[] digest = content.buf;
    			try {
    				MessageDigest md = MessageDigest.getInstance("MD5");
    				md.update(content.buf);
    				digest = md.digest();
    			} catch (NoSuchAlgorithmException nsax) {
    				
    			}
    			// If cached and same, just return
    		    if (Arrays.equals(digest, cacheval)) {
    		    	return true;
    		    }
    			String ct = "text/plain";
    			if (fileid.endsWith(".json")) {
    				ct = "application/json";
    			}
    			else if (fileid.endsWith(".php")) {
    				ct = "application/x-httpd-php";
    			}
    			else if (fileid.endsWith(".html")) {
    				ct = "text/html";
    			}
    			else if (fileid.endsWith(".css")) {
    				ct = "text/css";
    			}
    			else if (fileid.endsWith(".js")) {
    				ct = "application/x-javascript";
    			}
       			PutObjectRequest req = PutObjectRequest.builder().bucketName(bucketname).key(baseKey).contentType(ct).build();
    			s3.putObject(req, RequestBody.fromBytes(content.buf, content.len));
        		standalone_cache.put(fileid, digest);
    		}
			done = true;
        } catch (S3Exception x) {
        	Log.severe("AWS Exception", x);
    	} finally {
    		releaseConnection(s3);
    	}
        return done;
    }

    private S3Client getConnection() throws S3Exception {
        S3Client c = null;
        synchronized (cpool) {
            while (c == null) {
                for (int i = 0; i < cpool.length; i++) {    // See if available connection
                    if (cpool[i] != null) { // Found one
                        c = cpool[i];
                        cpool[i] = null;
                        break;
                    }
                }
                if (c == null) {
                    if (cpoolCount < POOLSIZE) {  // Still more we can have
                        c = new DefaultS3ClientBuilder()
                        	    .credentialsProvider(() -> AwsBasicCredentials.create(access_key_id, secret_access_key))
                        	    .region(Region.fromString(region))
                        	    .httpClient(URLConnectionSdkHttpClient.create())
                        	    .build();
                        if (c == null) {
                        	Log.severe("Error creating S3 access client");      
                        	return null;
                        }
                        cpoolCount++;
                    }
                    else {
                        try {
                            cpool.wait();
                        } catch (InterruptedException e) {
                            return null;
                        }
                    }
                }
            }
        }
        return c;
    }
    
    private void releaseConnection(S3Client c) {
        if (c == null) return;
        synchronized (cpool) {
            for (int i = 0; i < POOLSIZE; i++) {
                if (cpool[i] == null) {
                    cpool[i] = c;
                    c = null; // Mark it recovered (no close needed
                    cpool.notifyAll();
                    break;
                }
            }
            if (c != null) {  // If broken, just toss it
            	try {
					c.close();
				} catch (IOException e) {
				}
                cpoolCount--;   // And reduce count
                cpool.notifyAll();
            }
        }
    }
}
