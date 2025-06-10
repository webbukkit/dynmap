package org.dynmap.storage.aws_s3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

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

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;

public class AWSS3MapStorage extends MapStorage {
    public class StorageTile extends MapStorageTile {
        private final String baseKey;
        private final String uri;

        StorageTile(DynmapWorld world, MapType map, int x, int y,
                int zoom, ImageVariant var) {
            super(world, map, x, y, zoom, var);

            String baseURI;
            if (zoom > 0) {
                baseURI = map.getPrefix() + var.variantSuffix + "/" + (x >> 5) + "_" + (y >> 5) + "/"
                        + "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".substring(0, zoom) + "_" + x + "_" + y;
            } else {
                baseURI = map.getPrefix() + var.variantSuffix + "/" + (x >> 5) + "_" + (y >> 5) + "/" + x + "_" + y;
            }
            uri = baseURI + "." + map.getImageFormat().getFileExt();
            baseKey = AWSS3MapStorage.this.prefix + "tiles/" + world.getName() + "/" + uri;
        }

        @Override
        public boolean exists() {
            // Check if the operation should be skipped
            if (shouldSkipOperation()) {
                return false; // Assume the file does not exist during backoff
            }

            boolean exists = false;
            MinioClient minioClient = null;
            try {
                minioClient = getConnection();
                minioClient.statObject(StatObjectArgs.builder().bucket(bucketname).object(baseKey).build());
                exists = true;
                recordSuccess(); // Record success
            } catch (ErrorResponseException x) {
                if ("NoSuchKey".equals(x.errorResponse().code())) {
                    // File not found is a normal case, not an error
                    recordSuccess();
                } else {
                    recordError(x, "exists check");
                }
            } catch (Exception x) {
                recordError(x, "exists check");
            } finally {
                releaseConnection(minioClient);
            }
            return exists;
        }

        @Override
        public boolean matchesHashCode(long hash) {
            return false;
        }

        @Override
        public TileRead read() {
            // Check if the operation should be skipped
            if (shouldSkipOperation()) {
                return null; // Return null during backoff to indicate read failure
            }

            MinioClient minioClient = null;
            try {
                minioClient = getConnection();
                try (java.io.InputStream stream = minioClient
                        .getObject(GetObjectArgs.builder().bucket(bucketname).object(baseKey).build())) {
                    byte[] buf = stream.readAllBytes();
                    if (buf != null && buf.length > 0) {
                        TileRead tr = new TileRead();
                        tr.image = new BufferInputStream(buf);
                        tr.format = map.getImageFormat().getEncoding(); // Use map's format since MinIO doesn't return
                        // content-type metadata easily

                        // Try to get metadata for hash and timestamp
                        try {
                            io.minio.StatObjectResponse stat = minioClient
                                    .statObject(StatObjectArgs.builder().bucket(bucketname).object(baseKey).build());
                            Map<String, String> meta = stat.userMetadata();
                            if (meta != null) {
                                String v = meta.get("x-dynmap-hash");
                                if (v != null) {
                                    tr.hashCode = Long.parseLong(v, 16);
                                }
                                v = meta.get("x-dynmap-ts");
                                if (v != null) {
                                    tr.lastModified = Long.parseLong(v);
                                }
                            }
                        } catch (Exception metaEx) {
                            // Ignore metadata errors
                        }
                        recordSuccess(); // Record success
                        return tr;
                    }
                }
                recordSuccess(); // Even if the file is empty, count as success
            } catch (ErrorResponseException x) {
                if ("NoSuchKey".equals(x.errorResponse().code())) {
                    recordSuccess(); // File not found is a normal case
                    return null; // Nominal case if it doesn't exist
                }
                recordError(x, "read");
            } catch (Exception x) {
                recordError(x, "read");
            } finally {
                releaseConnection(minioClient);
            }
            return null;
        }

        @Override
        public boolean write(long hash, BufferOutputStream encImage, long timestamp) {
            // Check if the operation should be skipped
            if (shouldSkipOperation()) {
                return false; // Skip write operation during backoff
            }

            boolean done = false;
            MinioClient minioClient = null;
            try {
                minioClient = getConnection();
                if (encImage == null) { // Delete?
                    minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(baseKey).build());
                } else {
                    Map<String, String> metadata = new java.util.HashMap<>();
                    metadata.put("x-dynmap-hash", Long.toHexString(hash));
                    metadata.put("x-dynmap-ts", Long.toString(timestamp));

                    java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(encImage.buf);
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketname)
                            .object(baseKey)
                            .stream(inputStream, encImage.buf.length, -1)
                            .contentType(map.getImageFormat().getEncoding().getContentType())
                            .userMetadata(metadata)
                            .build());
                }
                done = true;
                recordSuccess(); // Record success
            } catch (Exception x) {
                recordError(x, "write");
            } finally {
                releaseConnection(minioClient);
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
            if (x >= 0)
                xx = x - (x % (2 * step));
            else
                xx = x + (x % (2 * step));
            yy = -y;
            if (yy >= 0)
                yy = yy - (yy % (2 * step));
            else
                yy = yy + (yy % (2 * step));
            yy = -yy;
            return new StorageTile(world, map, xx, yy, zoom + 1, var);
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
    private String endpoint;
    private String access_key_id;
    private String secret_access_key;
    private String prefix;
    private boolean path_style_access;

    private int POOLSIZE = 4;
    private int cpoolCount = 0;
    private MinioClient[] cpool = new MinioClient[POOLSIZE];

    // Error handling and backoff mechanism
    private final AtomicLong lastErrorTime = new AtomicLong(0);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicLong lastErrorLogTime = new AtomicLong(0);
    private static final long MIN_BACKOFF_MS = 1000; // Minimum backoff time: 1 second
    private static final long MAX_BACKOFF_MS = 300000; // Maximum backoff time: 5 minutes
    private static final int MAX_CONSECUTIVE_ERRORS = 10; // Maximum consecutive errors
    private static final long ERROR_LOG_INTERVAL_MS = 60000; // Error log interval: 1 minute

    public AWSS3MapStorage() {
    }

    @Override
    public boolean init(DynmapCore core) {
        if (!super.init(core)) {
            return false;
        }
        if (!core.isInternalWebServerDisabled) {
            Log.severe(
                    "AWS S3 storage is not supported option with internal web server: set disable-webserver: true in configuration.txt");
            return false;
        }
        if (core.isLoginSupportEnabled()) {
            Log.severe(
                    "AWS S3 storage is not supported option with loegin support enabled: set login-enabled: false in configuration.txt");
            return false;
        }
        // Get our settings
        bucketname = core.configuration.getString("storage/bucketname", "dynmap");
        access_key_id = core.configuration.getString("storage/aws_access_key_id", System.getenv("AWS_ACCESS_KEY_ID"));
        secret_access_key = core.configuration.getString("storage/aws_secret_access_key",
                System.getenv("AWS_SECRET_ACCESS_KEY"));
        prefix = core.configuration.getString("storage/prefix", "");
        path_style_access = core.configuration.getBoolean("storage/path_style_access", false);

        // Either use a custom endpoint, or one of the default AWS regions
        String region_name = core.configuration.getString("storage/region", "us-east-1");
        String region_endpoint = core.configuration.getString("storage/override_endpoint", "");

        if (region_endpoint.length() > 0) {
            endpoint = region_endpoint;
        } else {
            // Use AWS S3 endpoint format
            if ("us-east-1".equals(region_name)) {
                endpoint = "https://s3.amazonaws.com";
            } else {
                endpoint = "https://s3." + region_name + ".amazonaws.com";
            }
        }

        Log.info("Using AWS S3 storage: web site at S3 bucket " + bucketname + " with endpoint " + endpoint + " ("
                + (path_style_access ? "path-style" : "virtual-hosted-style") + " access)");

        if ((prefix.length() > 0) && (prefix.charAt(prefix.length() - 1) != '/')) {
            prefix += '/';
        }
        // Now create the access client for the S3 service
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            if (minioClient == null) {
                Log.severe("Error creating MinIO S3 access client");
                return false;
            }
            // Make sure bucket exists
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketname).build());
            if (!bucketExists) {
                Log.severe("Error: cannot find or access S3 bucket: " + bucketname);
                return false;
            }
        } catch (Exception ex) {
            Log.severe("AWS Exception", ex);
            return false;
        } finally {
            releaseConnection(minioClient);
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
        if (suri.length < 2)
            return null;
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
        if (mt == null) { // Not found?
            return null;
        }
        // Now, take the last section and parse out coordinates and zoom
        String fname = suri[suri.length - 1];
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
            } else {
                x = Integer.parseInt(coord[0]);
                y = Integer.parseInt(coord[1]);
            }
            return getTile(world, mt, x, y, zoom, imgvar);
        } catch (NumberFormatException nfx) {
            return null;
        }
    }

    private void processEnumMapTiles(DynmapWorld world, MapType map, ImageVariant var, MapStorageTileEnumCB cb,
            MapStorageBaseTileEnumCB cbBase,
            MapStorageTileSearchEndCB cbEnd) {
        String basekey = prefix + "tiles/" + world.getName() + "/" + map.getPrefix() + var.variantSuffix + "/";
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            Iterable<Result<Item>> results = minioClient
                    .listObjects(ListObjectsArgs.builder().bucket(bucketname).prefix(basekey).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String key = item.objectName();
                key = key.substring(basekey.length()); // Strip off base
                // Parse the extension
                String ext = null;
                int extoff = key.lastIndexOf('.');
                if (extoff >= 0) {
                    ext = key.substring(extoff + 1);
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
                if (coord.length == 2) { // Must be 2 to be a tile
                    try {
                        int x = Integer.parseInt(coord[0]);
                        int y = Integer.parseInt(coord[1]);
                        // Invoke callback
                        MapStorageTile t = new StorageTile(world, map, x, y, zoom, var);
                        if (cb != null)
                            cb.tileFound(t, fmt);
                        if (cbBase != null && t.zoom == 0)
                            cbBase.tileFound(t, fmt);
                        t.cleanup();
                    } catch (NumberFormatException nfx) {
                    }
                }
            }
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
        }
        if (cbEnd != null) {
            cbEnd.searchEnded();
        }
    }

    @Override
    public void enumMapTiles(DynmapWorld world, MapType map, MapStorageTileEnumCB cb) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else { // Else, add all directories under world directory (for maps)
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
    public void enumMapBaseTiles(DynmapWorld world, MapType map, MapStorageBaseTileEnumCB cbBase,
            MapStorageTileSearchEndCB cbEnd) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else { // Else, add all directories under world directory (for maps)
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
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            Iterable<Result<Item>> results = minioClient
                    .listObjects(ListObjectsArgs.builder().bucket(bucketname).prefix(basekey).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String key = item.objectName();
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(key).build());
            }
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
        }
    }

    @Override
    public void purgeMapTiles(DynmapWorld world, MapType map) {
        List<MapType> mtlist;

        if (map != null) {
            mtlist = Collections.singletonList(map);
        } else { // Else, add all directories under world directory (for maps)
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
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            if (encImage == null) { // Delete?
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(baseKey).build());
            } else {
                java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(encImage.buf);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketname)
                        .object(baseKey)
                        .stream(inputStream, encImage.buf.length, -1)
                        .contentType("image/png")
                        .build());
            }
            done = true;
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
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
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketname).object(baseKey).build());
            exists = true;
        } catch (ErrorResponseException x) {
            if (!"NoSuchKey".equals(x.errorResponse().code())) {
                Log.severe("AWS Exception", x);
            }
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
        }
        return exists;
    }

    @Override
    public boolean setMarkerImage(String markerid, BufferOutputStream encImage) {
        boolean done = false;
        String baseKey = prefix + "tiles/_markers_/" + markerid + ".png";
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            if (encImage == null) { // Delete?
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(baseKey).build());
            } else {
                java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(encImage.buf);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketname)
                        .object(baseKey)
                        .stream(inputStream, encImage.buf.length, -1)
                        .contentType("image/png")
                        .build());
            }
            done = true;
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
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
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            if (content == null) { // Delete?
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(baseKey).build());
            } else {
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(contentBytes);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketname)
                        .object(baseKey)
                        .stream(inputStream, contentBytes.length, -1)
                        .contentType("application/json")
                        .build());
            }
            done = true;
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
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
     * URI to use for loading configuration JSON files (for external web server
     * only)
     *
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getConfigurationJSONURI(boolean login_enabled) {
        return "standalone/dynmap_config.json?_={timestamp}";
    }

    /**
     * URI to use for loading update JSON files (for external web server only)
     *
     * @param login_enabled - selects based on login security enabled
     * @return URI
     */
    public String getUpdateJSONURI(boolean login_enabled) {
        return "standalone/dynmap_{world}.json?_={timestamp}";
    }

    @Override
    public void addPaths(StringBuilder sb, DynmapCore core) {
        String p = core.getTilesFolder().getAbsolutePath();
        if (!p.endsWith("/"))
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
     *
     * @param fileid  - file path
     * @param content - content for file
     * @return true if successful
     */
    public boolean setStaticWebFile(String fileid, BufferOutputStream content) {

        boolean done = false;
        String baseKey = prefix + fileid;
        MinioClient minioClient = null;
        try {
            minioClient = getConnection();
            byte[] cacheval = standalone_cache.get(fileid);

            if (content == null) { // Delete?
                if ((cacheval != null) && (cacheval.length == 0)) { // Delete cached?
                    return true;
                }
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketname).object(baseKey).build());
                standalone_cache.put(fileid, new byte[0]); // Mark in cache
            } else {
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
                } else if (fileid.endsWith(".php")) {
                    ct = "application/x-httpd-php";
                } else if (fileid.endsWith(".html")) {
                    ct = "text/html";
                } else if (fileid.endsWith(".css")) {
                    ct = "text/css";
                } else if (fileid.endsWith(".js")) {
                    ct = "application/x-javascript";
                }
                java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(content.buf);
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketname)
                        .object(baseKey)
                        .stream(inputStream, content.buf.length, -1)
                        .contentType(ct)
                        .build());
                standalone_cache.put(fileid, digest);
            }
            done = true;
        } catch (Exception x) {
            Log.severe("AWS Exception", x);
        } finally {
            releaseConnection(minioClient);
        }
        return done;
    }

    private MinioClient getConnection() throws MapStorage.StorageShutdownException {
        MinioClient c = null;
        if (isShutdown)
            throw new MapStorage.StorageShutdownException();
        synchronized (cpool) {
            while (c == null) {
                for (int i = 0; i < cpool.length; i++) { // See if available connection
                    if (cpool[i] != null) { // Found one
                        c = cpool[i];
                        cpool[i] = null;
                        break;
                    }
                }
                if (c == null) {
                    if (cpoolCount < POOLSIZE) { // Still more we can have
                        try {
                            MinioClient.Builder builder = MinioClient.builder()
                                    .endpoint(endpoint)
                                    .credentials(access_key_id, secret_access_key);

                            // 设置路径样式访问
                            if (path_style_access) {
                                // MinIO 客户端通过 endpoint 自动处理路径样式访问
                                // 不需要额外配置
                            }

                            c = builder.build();
                            if (c == null) {
                                Log.severe("Error creating MinIO S3 access client");
                                return null;
                            }
                            cpoolCount++;
                        } catch (Exception e) {
                            Log.severe("Error creating MinIO S3 access client", e);
                            return null;
                        }
                    } else {
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

    private void releaseConnection(MinioClient c) {
        if (c == null)
            return;
        synchronized (cpool) {
            for (int i = 0; i < POOLSIZE; i++) {
                if (cpool[i] == null) {
                    cpool[i] = c;
                    c = null; // Mark it recovered (no close needed)
                    cpool.notifyAll();
                    break;
                }
            }
            if (c != null) { // If broken, just toss it
                cpoolCount--; // And reduce count
                cpool.notifyAll();
            }
        }
    }

    /**
     * Checks if the operation should be skipped (based on backoff mechanism)
     *
     * @return true if the operation should be skipped
     */
    private boolean shouldSkipOperation() {
        long currentTime = System.currentTimeMillis();
        long lastError = lastErrorTime.get();
        int errors = consecutiveErrors.get();

        if (errors == 0) {
            return false; // No errors, execute normally
        }

        if (errors >= MAX_CONSECUTIVE_ERRORS) {
            // Maximum error count reached, log warning and skip
            long lastLogTime = lastErrorLogTime.get();
            if (currentTime - lastLogTime > ERROR_LOG_INTERVAL_MS) {
                if (lastErrorLogTime.compareAndSet(lastLogTime, currentTime)) {
                    Log.warning("AWS S3 operations suspended due to " + errors + " consecutive errors. " +
                            "Please check your S3 credentials and configuration. " +
                            "Next retry in " + (MAX_BACKOFF_MS / 1000) + " seconds.");
                }
            }

            // Check if it's time to retry
            if (currentTime - lastError < MAX_BACKOFF_MS) {
                return true; // Still within maximum backoff time, skip operation
            } else {
                // Reset error count, allow retry
                consecutiveErrors.set(0);
                return false;
            }
        }

        // Calculate backoff time (exponential backoff)
        long backoffTime = Math.min(MIN_BACKOFF_MS * (1L << Math.min(errors - 1, 10)), MAX_BACKOFF_MS);

        if (currentTime - lastError < backoffTime) {
            return true; // Still within backoff time, skip operation
        }

        return false; // Operation can be executed
    }

    /**
     * Record operation success
     */
    private void recordSuccess() {
        consecutiveErrors.set(0);
    }

    /**
     * Record operation failure
     *
     * @param exception Exception information
     * @param operation Operation name
     */
    private void recordError(Exception exception, String operation) {
        long currentTime = System.currentTimeMillis();
        lastErrorTime.set(currentTime);
        int errors = consecutiveErrors.incrementAndGet();

        // Determine if it's an authentication/permission error
        boolean isAuthError = isAuthenticationError(exception);

        // Control log frequency
        long lastLogTime = lastErrorLogTime.get();
        boolean shouldLog = false;

        if (isAuthError) {
            // Authentication errors: log immediately for the first time, then once per minute
            shouldLog = (errors == 1) || (currentTime - lastLogTime > ERROR_LOG_INTERVAL_MS);
        } else {
            // Other errors: log immediately for the first 3 times, then once per minute
            shouldLog = (errors <= 3) || (currentTime - lastLogTime > ERROR_LOG_INTERVAL_MS);
        }

        if (shouldLog && lastErrorLogTime.compareAndSet(lastLogTime, currentTime)) {
            if (isAuthError) {
                Log.severe("AWS S3 " + operation + " failed (attempt " + errors + "): " +
                        "Authentication/Authorization error. Please check your AWS credentials and bucket permissions. "
                        +
                        "Error: " + exception.getMessage());
                if (errors >= 3) {
                    Log.severe("Subsequent S3 authentication errors will be logged less frequently to avoid log spam.");
                }
            } else {
                Log.severe("AWS S3 " + operation + " failed (attempt " + errors + "): " + exception.getMessage());
                if (errors > 3) {
                    Log.severe("S3 operations will be retried with exponential backoff. Current backoff: " +
                            Math.min(MIN_BACKOFF_MS * (1L << Math.min(errors - 1, 10)), MAX_BACKOFF_MS) / 1000
                            + " seconds");
                }
            }
        }
    }

    /**
     * Determine if it's an authentication/permission related error
     */
    private boolean isAuthenticationError(Exception exception) {
        if (exception instanceof ErrorResponseException) {
            ErrorResponseException ere = (ErrorResponseException) exception;
            String errorCode = ere.errorResponse().code();
            return "AccessDenied".equals(errorCode) ||
                    "InvalidAccessKeyId".equals(errorCode) ||
                    "SignatureDoesNotMatch".equals(errorCode) ||
                    "TokenRefreshRequired".equals(errorCode) ||
                    "ExpiredToken".equals(errorCode);
        }

        String message = exception.getMessage();
        if (message != null) {
            message = message.toLowerCase();
            return message.contains("access denied") ||
                    message.contains("invalid access key") ||
                    message.contains("signature does not match") ||
                    message.contains("expired token") ||
                    message.contains("unauthorized");
        }

        return false;
    }
}
