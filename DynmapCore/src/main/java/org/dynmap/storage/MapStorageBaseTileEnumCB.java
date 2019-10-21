package org.dynmap.storage;

import org.dynmap.MapType.ImageEncoding;

public interface MapStorageBaseTileEnumCB {
    /**
     * Callback for base (non-zoomed) tile enumeration calls
     * @param tile - tile found
     * @param enc - image encoding
     */
    public void tileFound(MapStorageTile tile, ImageEncoding enc);
}
