package org.dynmap.storage;

import org.dynmap.MapType.ImageEncoding;

public interface MapStorageTileSearchEndCB {
    /**
     * Callback for end of tile enumeration calls
     */
    public void searchEnded();
}
