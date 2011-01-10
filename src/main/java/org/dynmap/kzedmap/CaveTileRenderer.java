package org.dynmap.kzedmap;

import java.awt.Color;
import java.util.Map;

import org.bukkit.World;
import org.dynmap.MapManager;
import org.dynmap.debug.Debugger;

public class CaveTileRenderer extends DefaultTileRenderer {

	public CaveTileRenderer(String name, Debugger debugger) {
		super(name, debugger);
	}

	@Override
	protected Color scan(World world, int x, int y, int z, int seq)
	{
		boolean air = true;

		for(;;) {
			if(y < 0)
				return Color.BLACK;

			int id = world.getBlockAt(x, y, z).getTypeID();

			switch(seq) {
			case 0:
				x--;
				break;
			case 1:
				y--;
				break;
			case 2:
				z++;
				break;
			case 3:
				y--;
				break;
			}

			seq = (seq + 1) & 3;

			switch(id) {
			case 20:
			case 18:
			case 17:
			case 78:
			case 79:
				id = 0;
				break;
			default:
			}

			if(id != 0) {
				air = false;
				continue;
			}

			if(id == 0 && !air) {
				int cr, cg, cb;
				int mult = 256;

				if(y < 64) {
					cr = 0;
					cg = 64 + y * 3;
					cb = 255 - y * 4;
				} else {
					cr = (y-64) * 4;
					cg = 255;
					cb = 0;
				}

				switch(seq) {
				case 0:
					mult = 224;
					break;
				case 1:
					mult = 256;
					break;
				case 2:
					mult = 192;
					break;
				case 3:
					mult = 160;
					break;
				}

				cr = cr * mult / 256;
				cg = cg * mult / 256;
				cb = cb * mult / 256;

				return new Color(cr, cg, cb);
			}
		}
	}
}
