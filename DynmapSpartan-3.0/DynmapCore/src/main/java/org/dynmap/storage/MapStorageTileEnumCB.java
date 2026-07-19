package org.dynmap.storage;

import org.dynmap.MapType.ImageEncoding;

public interface MapStorageTileEnumCB {
    /**
     * Callback for tile enumeration calls
     * @param tile - tile found
     * @param enc - image encoding
     */
    public void tileFound(MapStorageTile tile, ImageEncoding enc);
}
