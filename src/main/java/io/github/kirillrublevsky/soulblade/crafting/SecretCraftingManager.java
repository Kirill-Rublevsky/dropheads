package io.github.kirillrublevsky.soulblade.crafting;

import io.github.kirillrublevsky.soulblade.item.SoulbladeItemFactory;
import io.github.kirillrublevsky.soulblade.storage.SoulbladeStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SecretCraftingManager implements Listener {
    private enum Match { NONE, HEART, BLADE }
    private static final Material[] HEART = { Material.NETHERITE_BLOCK,Material.NETHER_STAR,Material.NETHERITE_BLOCK, Material.NETHERITE_INGOT,Material.BEACON,Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK,Material.NETHER_STAR,Material.NETHERITE_BLOCK };
    private static final Material[] BLADE = { Material.NETHERITE_BLOCK,Material.NETHER_STAR,Material.NETHERITE_BLOCK, Material.NETHERITE_INGOT,null,Material.NETHERITE_INGOT, Material.BEACON,Material.NETHERITE_SWORD,Material.DIAMOND_BLOCK };
    private final Plugin plugin; private final SoulbladeItemFactory items; private final SoulbladeStorage storage;
    private final AtomicLong previews=new AtomicLong(), crafted=new AtomicLong(), failures=new AtomicLong();
    private final Set<UUID> inFlight=ConcurrentHashMap.newKeySet();

    public SecretCraftingManager(Plugin plugin, SoulbladeItemFactory items, SoulbladeStorage storage) { this.plugin=plugin;this.items=items;this.storage=storage; }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        Match match=match(e.getInventory());
        if(match==Match.HEART)e.getInventory().setResult(items.createHeart());
        else if(match==Match.BLADE)e.getInventory().setResult(items.createBlade(UUID.randomUUID(),0));
        else if(isOurResult(e.getInventory().getResult()))e.getInventory().setResult(null);
        if(match!=Match.NONE)previews.incrementAndGet();
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player player) || !(e.getInventory() instanceof CraftingInventory inv) || e.getRawSlot()!=0)return;
        Match match=match(inv); if(match==Match.NONE)return;
        e.setCancelled(true);
        if(!canReceive(player))return;
        if(match==Match.HEART){ consume(inv); give(player,items.createHeart()); crafted.incrementAndGet(); return; }
        if(!inFlight.add(player.getUniqueId()))return;
        ItemStack[] snapshot=Arrays.stream(inv.getMatrix()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        UUID id=UUID.randomUUID();
        storage.create(id,player.getUniqueId(),player.getName()).whenComplete((data,error)->Bukkit.getScheduler().runTask(plugin,()->{
            inFlight.remove(player.getUniqueId());
            if(error!=null){failures.incrementAndGet();plugin.getLogger().severe("Blade registration failed: "+error.getMessage());return;}
            if(!player.isOnline()||!sameMatrix(inv.getMatrix(),snapshot)){ failures.incrementAndGet(); plugin.getLogger().warning("Craft cancelled: matrix changed while registering " + id); return; }
            consume(inv); give(player,items.createBlade(id,0)); crafted.incrementAndGet();
        }));
    }

    private Match match(CraftingInventory inv) {
        ItemStack[] m=inv.getMatrix(); if(m.length!=9)return Match.NONE;
        if(materials(m,HEART))return Match.HEART;
        if(materials(m,BLADE)&&items.isAuthenticHeart(m[4])&&validBaseSword(m[7]))return Match.BLADE;
        return Match.NONE;
    }
    private boolean materials(ItemStack[] m, Material[] expected){for(int i=0;i<9;i++){if(expected[i]==null)continue;if(m[i]==null||m[i].getType()!=expected[i]||m[i].getAmount()<1)return false;}return true;}
    private boolean validBaseSword(ItemStack i){return i!=null&&i.getType()==Material.NETHERITE_SWORD&&i.getDurability()==0&&i.getEnchantments().isEmpty()&&!items.isAuthenticBlade(i);}
    private void consume(CraftingInventory inv){ItemStack[] m=inv.getMatrix();for(int i=0;i<m.length;i++){if(m[i]!=null){m[i].subtract();if(m[i].getAmount()<=0)m[i]=null;}}inv.setMatrix(m);inv.setResult(null);}
    private boolean canReceive(Player p){return p.getInventory().firstEmpty()!=-1||p.getItemOnCursor().isEmpty();}
    private void give(Player p,ItemStack i){var left=p.getInventory().addItem(i);left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x));}
    private boolean sameMatrix(ItemStack[] a,ItemStack[] b){return a.length==b.length&&java.util.stream.IntStream.range(0,a.length).allMatch(i->java.util.Objects.equals(a[i],b[i]));}
    private boolean isOurResult(ItemStack i){return items.isAuthenticHeart(i)||items.isAuthenticBlade(i);}
    public String diagnostics(){return "previews="+previews.get()+", crafted="+crafted.get()+", failures="+failures.get()+", vanilla recipes registered=0";}
}
