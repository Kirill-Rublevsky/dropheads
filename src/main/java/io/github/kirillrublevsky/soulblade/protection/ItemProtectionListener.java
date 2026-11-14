package io.github.kirillrublevsky.soulblade.protection;

import io.github.kirillrublevsky.soulblade.item.SoulbladeItemFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;

public final class ItemProtectionListener implements Listener {
    private final SoulbladeItemFactory items;
    public ItemProtectionListener(SoulbladeItemFactory items){this.items=items;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void enchant(EnchantItemEvent e){if(items.isAuthenticBlade(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void anvil(PrepareAnvilEvent e){if(items.isAuthenticBlade(e.getInventory().getFirstItem())||items.isAuthenticBlade(e.getInventory().getSecondItem()))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void smith(PrepareSmithingEvent e){if(items.isAuthenticBlade(e.getInventory().getInputEquipment()))e.setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(PlayerItemDamageEvent e){if(items.isAuthenticBlade(e.getItem()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void grindstone(InventoryClickEvent e){if(e.getInventory().getType()==InventoryType.GRINDSTONE&&(items.isAuthenticBlade(e.getInventory().getItem(0))||items.isAuthenticBlade(e.getInventory().getItem(1))))e.setCancelled(true);}
}
