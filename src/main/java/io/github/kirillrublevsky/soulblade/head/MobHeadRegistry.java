package io.github.kirillrublevsky.soulblade.head;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import com.destroystokyo.paper.profile.PlayerProfile;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class MobHeadRegistry {
    private final Plugin plugin;
    private final Map<EntityType, Supplier<ItemStack>> heads = new EnumMap<>(EntityType.class);
    private final MiniMessage mini = MiniMessage.miniMessage();

    public MobHeadRegistry(Plugin plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        heads.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mobs.yml"));
        ConfigurationSection root = y.getConfigurationSection("mobs");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null || !s.getBoolean("enabled", true)) continue;
            try {
                EntityType type = EntityType.valueOf(key);
                Material material = Material.valueOf(s.getString("material", "PLAYER_HEAD"));
                String name = s.getString("name", key);
                String texture = s.getString("texture", "");
                heads.put(type, () -> make(material, name, texture));
            } catch (IllegalArgumentException e) { plugin.getLogger().warning("Unknown mob/material in mobs.yml: " + key); }
        }
    }

    private ItemStack make(Material material, String name, String texture) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta(); meta.displayName(mini.deserialize(name));
        if (meta instanceof SkullMeta skull && !texture.isBlank()) {
            PlayerProfile profile = plugin.getServer().createProfile(UUID.nameUUIDFromBytes(texture.getBytes()));
            profile.setProperty(new ProfileProperty("textures", texture));
            skull.setPlayerProfile(profile);
        }
        item.setItemMeta(meta); return item;
    }
    public Optional<ItemStack> get(EntityType type) { Supplier<ItemStack> supplier=heads.get(type); return supplier==null?Optional.empty():Optional.of(supplier.get()); }
}
