package de.varo.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemUtils {
    private ItemUtils() {}

    public static int countItems(Player p, Material mat) {
        int cnt = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == mat) cnt += is.getAmount();
        }
        return cnt;
    }

    public static void removeItems(Player p, Material mat, int amount) {
        int left = amount;
        ItemStack[] cont = p.getInventory().getContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack is = cont[i];
            if (is == null || is.getType() != mat) continue;
            int take = Math.min(left, is.getAmount());
            is.setAmount(is.getAmount() - take);
            if (is.getAmount() <= 0) cont[i] = null;
            left -= take;
        }
        p.getInventory().setContents(cont);
    }

    public static ItemStack named(ItemStack item, String displayName) {
        if (item == null) return null;
        ItemMeta im = item.getItemMeta();
        if (im != null) {
            im.setDisplayName(displayName);
            item.setItemMeta(im);
        }
        return item;
    }
}
