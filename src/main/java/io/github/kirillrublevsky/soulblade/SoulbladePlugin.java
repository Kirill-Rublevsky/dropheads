package io.github.kirillrublevsky.soulblade;

import io.github.kirillrublevsky.soulblade.command.SoulbladeCommand;
import io.github.kirillrublevsky.soulblade.combat.CombatManager;
import io.github.kirillrublevsky.soulblade.crafting.SecretCraftingManager;
import io.github.kirillrublevsky.soulblade.head.MobHeadRegistry;
import io.github.kirillrublevsky.soulblade.item.SoulbladeItemFactory;
import io.github.kirillrublevsky.soulblade.level.LevelingManager;
import io.github.kirillrublevsky.soulblade.protection.ItemProtectionListener;
import io.github.kirillrublevsky.soulblade.security.ItemSigner;
import io.github.kirillrublevsky.soulblade.storage.SoulbladeStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Base64;

public final class SoulbladePlugin extends JavaPlugin {
    private SoulbladeStorage storage;
    @Override public void onEnable(){
        saveDefaultConfig();saveResourceIfMissing("messages.yml");saveResourceIfMissing("recipes.yml");saveResourceIfMissing("mobs.yml");ensureSecret();
        try{
            storage=new SoulbladeStorage(this);LevelingManager leveling=new LevelingManager();SoulbladeItemFactory items=new SoulbladeItemFactory(this,new ItemSigner(getConfig().getString("security.signing-secret")),leveling);MobHeadRegistry heads=new MobHeadRegistry(this);
            SecretCraftingManager crafting=new SecretCraftingManager(this,items,storage);
            getServer().getPluginManager().registerEvents(crafting,this);getServer().getPluginManager().registerEvents(new CombatManager(this,items,storage,leveling,heads),this);getServer().getPluginManager().registerEvents(new ItemProtectionListener(items),this);
            SoulbladeCommand handler=new SoulbladeCommand(this,items,storage,leveling,crafting);PluginCommand command=getCommand("soulblade");if(command==null)throw new IllegalStateException("Command missing");command.setExecutor(handler);command.setTabCompleter(handler);
            getLogger().info("OVENQUILL Soulblade enabled for Paper/Purpur 26.2");
        }catch(Exception e){getLogger().severe("Cannot initialize: "+e.getMessage());getServer().getPluginManager().disablePlugin(this);}
    }
    private void saveResourceIfMissing(String name){if(!new java.io.File(getDataFolder(),name).exists())saveResource(name,false);}
    private void ensureSecret(){String secret=getConfig().getString("security.signing-secret","");if(secret.equals("CHANGE_ME_GENERATED_ON_FIRST_START")||secret.length()<32){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);getConfig().set("security.signing-secret",Base64.getEncoder().encodeToString(bytes));saveConfig();getLogger().warning("Generated a new signing secret. Back up config.yml; changing it invalidates existing items.");}}
    @Override public void onDisable(){if(storage!=null)storage.close();}
}
