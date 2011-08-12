package org.dynmap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;

public class Armor {
    /**
     * http://www.minecraftwiki.net/wiki/Item_Durability#Armor_durability
     * We rely on getArmorContents() to return 4 armor pieces in the order
     * of: boots, pants, chest, helmet
     */
    private static final int armorPoints[] = {3, 6, 8, 3};

    public static final int getArmorPoints(Player player) {
        int currentDurability = 0;
        int baseDurability = 0;
        int baseArmorPoints = 0;
        ItemStack[] itm = new ItemStack[4];
        PlayerInventory inv = player.getInventory();
        itm[0] = inv.getBoots();
        itm[1]= inv.getLeggings();
        itm[2] = inv.getChestplate();
        itm[3] = inv.getHelmet();
        for(int i = 0; i < 4; i++) {
    		if(itm[i] == null) continue;
        	int dur = itm[i].getDurability();
        	int max = itm[i].getType().getMaxDurability();
        	if(max <= 0) continue;
        	if(i == 2)
    			max = max + 1;	/* Always 1 too low for chestplate */
        	else
        		max = max - 3;	/* Always 3 too high, versus how client calculates it */
        	baseDurability += max;
        	currentDurability += max - dur;
        	baseArmorPoints += armorPoints[i];
        }
        int ap = 0;
        if(baseDurability > 0)
    		ap = ((baseArmorPoints - 1) * currentDurability) / baseDurability + 1;
        return ap;
    }
}
