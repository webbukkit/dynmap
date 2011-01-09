package org.dynmap.kzedmap;

import java.awt.image.BufferedImage;

import org.dynmap.MapTile;

public class KzedZoomedMapTile extends MapTile {
	@Override
	public String getName() {
		return "z" + originalTile.renderer.getName() + "_" + ztilex(originalTile.px) + "_" + ztiley(originalTile.py);
	}
	public BufferedImage unzoomedImage;
	public KzedMapTile originalTile;
	public KzedZoomedMapTile(KzedMap map, BufferedImage unzoomedImage, KzedMapTile original) {
		super(map);
		this.unzoomedImage = unzoomedImage;
		this.originalTile = original;
	}

	public int getTileX() {
		return ztilex(originalTile.px);
	}
	
	public int getTileY() {
		return ztiley(originalTile.py);
	}
	
	static int ztilex(int x) {
		if(x < 0)
			return x + (x % (KzedMap.tileWidth*2));
		else
			return x - (x % (KzedMap.tileWidth*2));
	}

	/* zoomed-out tile Y for tile position y */
	static int ztiley(int y)
	{
		if(y < 0)
			return y + (y % (KzedMap.tileHeight*2));
			//return y - (zTileHeight + (y % zTileHeight));
		else
			return y - (y % (KzedMap.tileHeight*2));
	}
	
	@Override
	public int hashCode() {
		return getName().hashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof KzedZoomedMapTile) {
			return ((KzedZoomedMapTile)obj).originalTile.equals(originalTile);
		}
		return super.equals(obj);
	}
}
