package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Armor {
    /**
     * http://www.minecraftwiki.net/wiki/Item_Durability#Armor_durability
     * We rely on getArmorContents() to return 4 armor pieces in the order
     * of: boots, pants, chest, helmet
     */
    private static final short armorPoints[] = {3, 6, 8, 3};

    public static final int getArmorPoints(Player player) {
        float currentDurability = 0;
        float baseDurability = 0;
        short baseArmorPoints = 0;
        ItemStack inventory[] = player.getInventory().getArmorContents();
        for(int i=0;i<inventory.length;i++) {
            final short maxDurability = inventory[i].getType().getMaxDurability();
            if(maxDurability < 0)
                continue;
            final short durability = inventory[i].getDurability();
            baseDurability += maxDurability;
            currentDurability += maxDurability - durability;
            baseArmorPoints += armorPoints[i];
        }
        return (int)(baseArmorPoints*currentDurability/baseDurability);
    }
}
