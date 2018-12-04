package org.dynmap.bukkit.helper;

public class BukkitMaterial {
	public final String name;
	public final boolean isSolid;
	public final boolean isLiquid;
	public BukkitMaterial(String n, boolean sol, boolean liq) {
		name = n;
		isSolid = sol;
		isLiquid = liq;
	}
}
