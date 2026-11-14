package io.github.kirillrublevsky.soulblade.item;

import io.github.kirillrublevsky.soulblade.level.LevelingManager;
import io.github.kirillrublevsky.soulblade.security.ItemSigner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SoulbladeItemFactory {
    public static final int DATA_VERSION = 1;
    private final NamespacedKey typeKey, idKey, versionKey, signatureKey;
    private final NamespacedKey damageModifierKey;
    private final ItemSigner signer;
    private final LevelingManager leveling;

    public SoulbladeItemFactory(Plugin plugin, ItemSigner signer, LevelingManager leveling) {
        this.signer = signer;
        this.leveling = leveling;
        typeKey = new NamespacedKey(plugin, "item_type");
        idKey = new NamespacedKey(plugin, "blade_uuid");
        versionKey = new NamespacedKey(plugin, "data_version");
        signatureKey = new NamespacedKey(plugin, "signature");
        damageModifierKey = new NamespacedKey(plugin, "soulblade_damage");
    }

    public ItemStack createHeart() {
        UUID id = UUID.randomUUID();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Сердце душ", TextColor.color(0xC8A2FF)));
        writeIdentity(meta.getPersistentDataContainer(), "SOUL_HEART", id);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createBlade(UUID id, long kills) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        writeIdentity(meta.getPersistentDataContainer(), "SOULBLADE", id);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
            new AttributeModifier(damageModifierKey, -0.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        applyDisplay(meta, kills);
        item.setItemMeta(meta);
        return item;
    }

    public void update(ItemStack item, long kills) {
        if (!isAuthenticBlade(item)) return;
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        for (var enchantment : List.copyOf(meta.getEnchants().keySet())) meta.removeEnchant(enchantment);
        applyDisplay(meta, kills);
        item.setItemMeta(meta);
    }

    private void applyDisplay(ItemMeta meta, long kills) {
        int level = leveling.level(kills);
        NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"));
        meta.displayName(Component.text("✦ Клинок душ", TextColor.color(0xA8E6CF)));
        var lore = new java.util.ArrayList<Component>();
        lore.add(Component.text("OVENQUILL | ЛЕГЕНДАРНЫЙ АРТЕФАКТ", TextColor.color(0xC8A2FF)));
        lore.add(Component.text("Уровень: " + level + "/50", TextColor.color(0xF5F5F5)));
        lore.add(Component.text("Всего убийств: " + numbers.format(kills), TextColor.color(0xF5F5F5)));
        if (level == 50) lore.add(Component.text("✦ МАКСИМАЛЬНЫЙ УРОВЕНЬ", TextColor.color(0xC8A2FF)));
        else lore.add(Component.text("До следующего уровня: " + numbers.format(leveling.remaining(kills)), TextColor.color(0xF5F5F5)));
        lore.add(Component.text("Шанс головы: " + String.format(Locale.US, "%.1f%%", leveling.headChancePercent(level)), TextColor.color(0xA8E6CF)));
        lore.add(Component.text("Урон: 0.5 HP", TextColor.color(0xF5F5F5)));
        lore.add(Component.text("Прочность: ∞", TextColor.color(0xF5F5F5)));
        lore.add(Component.text("Зачарования недоступны", TextColor.color(0x315C4B)));
        lore.add(Component.text("Души павших усиливают клинок.", TextColor.color(0xC8A2FF)));
        meta.lore(lore);
    }

    private void writeIdentity(PersistentDataContainer pdc, String type, UUID id) {
        pdc.set(typeKey, PersistentDataType.STRING, type);
        pdc.set(idKey, PersistentDataType.STRING, id.toString());
        pdc.set(versionKey, PersistentDataType.INTEGER, DATA_VERSION);
        pdc.set(signatureKey, PersistentDataType.STRING, signer.sign(type, id, DATA_VERSION));
    }

    public boolean isAuthenticHeart(ItemStack item) { return validate(item, "SOUL_HEART").isPresent(); }
    public boolean isAuthenticBlade(ItemStack item) { return validate(item, "SOULBLADE").isPresent(); }
    public Optional<UUID> bladeId(ItemStack item) { return validate(item, "SOULBLADE"); }

    private Optional<UUID> validate(ItemStack item, String expectedType) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(typeKey, PersistentDataType.STRING);
        String rawId = pdc.get(idKey, PersistentDataType.STRING);
        Integer version = pdc.get(versionKey, PersistentDataType.INTEGER);
        String signature = pdc.get(signatureKey, PersistentDataType.STRING);
        if (!expectedType.equals(type) || rawId == null || version == null) return Optional.empty();
        try {
            UUID id = UUID.fromString(rawId);
            return signer.verify(type, id, version, signature) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
}
