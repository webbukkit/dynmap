package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Armor {
    private static final double armorPoints[] = {1.5, 3.0, 4.0, 1.5};

    public static final int getArmorPoints(Player player) {
        int currentDurability = 0;
        int baseDurability = 0;
        double baseArmorPoints = 0;
        ItemStack inventory[] = player.getInventory().getArmorContents();
        for(int i=0;i<inventory.length;i++) {
            final int durability = inventory[i].getDurability();
            if(durability < 0)
                continue;
            final int maxDurability = inventory[i].getType().getMaxDurability();
            baseDurability += maxDurability;
            currentDurability += maxDurability - durability;
            baseArmorPoints += armorPoints[i];
        }
        return (int)(2*baseArmorPoints*currentDurability/baseDurability);
    }
}
