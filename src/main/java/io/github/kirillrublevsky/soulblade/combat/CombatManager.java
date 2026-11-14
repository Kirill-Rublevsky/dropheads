package io.github.kirillrublevsky.soulblade.combat;

import io.github.kirillrublevsky.soulblade.head.MobHeadRegistry;
import io.github.kirillrublevsky.soulblade.item.SoulbladeItemFactory;
import io.github.kirillrublevsky.soulblade.level.LevelingManager;
import io.github.kirillrublevsky.soulblade.storage.SoulbladeStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatManager implements Listener {
    private record LethalHit(UUID eventId, UUID blade, UUID attacker) {}
    private final Plugin plugin; private final SoulbladeItemFactory items; private final SoulbladeStorage storage; private final LevelingManager leveling; private final MobHeadRegistry heads;
    private final Map<UUID,LethalHit> lethal=new HashMap<>(); private final Set<UUID> spawnEggEntities=new HashSet<>();
    private final long cooldown;
    public CombatManager(Plugin plugin,SoulbladeItemFactory items,SoulbladeStorage storage,LevelingManager leveling,MobHeadRegistry heads){this.plugin=plugin;this.items=items;this.storage=storage;this.leveling=leveling;this.heads=heads;this.cooldown=plugin.getConfig().getLong("anti-farm.players.same-victim-cooldown-minutes",30)*60_000L;}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity victim))return;
        ItemStack held=p.getInventory().getItemInMainHand(); Optional<UUID> id=items.bladeId(held); if(id.isEmpty()||!p.hasPermission("ovenquill.soulblade.use"))return;
        e.setDamage(0.5);
        if(e.getFinalDamage()>=victim.getHealth()){
            UUID victimId=victim.getUniqueId();LethalHit hit=new LethalHit(UUID.randomUUID(),id.get(),p.getUniqueId());lethal.put(victimId,hit);
            Bukkit.getScheduler().runTaskLater(plugin,()->lethal.remove(victimId,hit),2L);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(EntityDeathEvent e){
        LethalHit hit=lethal.remove(e.getEntity().getUniqueId()); if(hit==null)return;
        boolean fromEgg=spawnEggEntities.remove(e.getEntity().getUniqueId());
        if(fromEgg&&!plugin.getConfig().getBoolean("anti-farm.mobs.count-spawn-egg-mobs",false))return;
        if(e.getEntityType().name().equals("WITHER_SKELETON"))e.getDrops().removeIf(i->i.getType()==Material.WITHER_SKELETON_SKULL);
        Player attacker=Bukkit.getPlayer(hit.attacker()); if(attacker==null)return;
        boolean player=e.getEntity() instanceof Player; UUID victim=e.getEntity().getUniqueId();
        storage.get(hit.blade()).thenCompose(beforeOpt->{
            if(beforeOpt.isEmpty())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Unregistered blade"));
            var before=beforeOpt.get(); int oldLevel=leveling.level(before.kills()); boolean drop=ThreadLocalRandom.current().nextDouble(100.0)<leveling.headChancePercent(oldLevel);
            return storage.countKill(hit.eventId(),hit.blade(),victim,e.getEntityType().name(),player,cooldown,drop).thenApply(result->Map.entry(result,drop));
        }).whenComplete((pair,error)->Bukkit.getScheduler().runTask(plugin,()->{
            if(error!=null){plugin.getLogger().warning("Kill not committed: "+error.getMessage());return;}
            var result=pair.getKey(); if(!result.counted())return;
            ItemStack current=attacker.getInventory().getItemInMainHand(); if(items.bladeId(current).filter(hit.blade()::equals).isPresent())items.update(current,result.blade().kills());
            int newLevel=leveling.level(result.blade().kills()); int oldLevel=leveling.level(result.blade().kills()-1);
            if(newLevel>oldLevel){attacker.playSound(attacker.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1,1);attacker.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,attacker.getLocation().add(0,1,0),24);}
            if(pair.getValue())createHead(e,attacker);
        }));
    }

    private void createHead(EntityDeathEvent e,Player attacker){
        ItemStack head=null;
        if(e.getEntity() instanceof Player victim){head=new ItemStack(Material.PLAYER_HEAD);var meta=(org.bukkit.inventory.meta.SkullMeta)head.getItemMeta();meta.setOwningPlayer(victim);head.setItemMeta(meta);}
        else if(!e.getEntityType().name().equals("WITHER_SKELETON"))head=heads.get(e.getEntityType()).orElse(null);
        if(head!=null)e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(),head);
    }

    @EventHandler public void spawned(CreatureSpawnEvent e){String reason=e.getSpawnReason().name();if(reason.contains("EGG"))spawnEggEntities.add(e.getEntity().getUniqueId());}
}
