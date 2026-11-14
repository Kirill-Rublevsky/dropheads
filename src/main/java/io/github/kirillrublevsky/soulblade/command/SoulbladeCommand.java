package io.github.kirillrublevsky.soulblade.command;

import io.github.kirillrublevsky.soulblade.crafting.SecretCraftingManager;
import io.github.kirillrublevsky.soulblade.item.SoulbladeItemFactory;
import io.github.kirillrublevsky.soulblade.level.LevelingManager;
import io.github.kirillrublevsky.soulblade.storage.SoulbladeStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class SoulbladeCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin; private final SoulbladeItemFactory items; private final SoulbladeStorage storage; private final LevelingManager leveling; private final SecretCraftingManager crafting;
    public SoulbladeCommand(Plugin plugin,SoulbladeItemFactory items,SoulbladeStorage storage,LevelingManager leveling,SecretCraftingManager crafting){this.plugin=plugin;this.items=items;this.storage=storage;this.leveling=leveling;this.crafting=crafting;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String sub=args.length==0?"info":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("recipe")){if(!sender.hasPermission("ovenquill.soulblade.recipe.admin"))return denied(sender);sender.sendMessage(Component.text("Сердце: BNB / IAI / BNB; Клинок: BNB / IHI / BSD",NamedTextColor.LIGHT_PURPLE));return true;}
        if(sub.equals("performance")){if(!sender.hasPermission("ovenquill.soulblade.performance"))return denied(sender);sender.sendMessage("Storage: active="+storage.activeOperations()+", queue="+storage.queueSize()+", completed="+storage.completedOperations()+", rejected="+storage.rejectedOperations());return true;}
        if(sub.equals("diagnose")){if(!sender.hasPermission("ovenquill.soulblade.diagnose"))return denied(sender);sender.sendMessage("SecretCrafting: "+crafting.diagnostics());return true;}
        if(sub.equals("admin"))return admin(sender,Arrays.copyOfRange(args,1,args.length));
        if(!(sender instanceof Player p)){sender.sendMessage("Только для игрока");return true;}
        Optional<UUID> id=items.bladeId(p.getInventory().getItemInMainHand());if(id.isEmpty()){p.sendMessage(Component.text("Возьмите подлинный Клинок душ в руку.",NamedTextColor.RED));return true;}
        storage.get(id.get()).whenComplete((data,error)->sync(()->{if(error!=null||data.isEmpty()){p.sendMessage(Component.text("Запись меча не найдена.",NamedTextColor.RED));return;}var b=data.get();p.sendMessage(Component.text("Клинок "+b.id()+" — уровень "+leveling.level(b.kills())+", убийств: "+b.kills(),NamedTextColor.AQUA));p.sendMessage(Component.text("Создатель: "+b.creatorName()+", создан: "+b.createdAt(),NamedTextColor.GRAY));}));return true;
    }
    private boolean admin(CommandSender s,String[] a){if(!s.hasPermission("ovenquill.soulblade.admin"))return denied(s);if(a.length==0){s.sendMessage("/soulblade admin give|setkills|setlevel|inspect|validate|stats|reload");return true;}
        String op=a[0].toLowerCase(Locale.ROOT);
        if(op.equals("give")&&a.length>=2){Player target=Bukkit.getPlayerExact(a[1]);if(target==null){s.sendMessage("Игрок не найден");return true;}UUID id=UUID.randomUUID();storage.create(id,target.getUniqueId(),target.getName()).whenComplete((d,e)->sync(()->{if(e!=null)s.sendMessage("Ошибка БД: "+e.getMessage());else{target.getInventory().addItem(items.createBlade(id,0));s.sendMessage("Клинок выдан: "+id);}}));return true;}
        if(op.equals("stats")){storage.countBlades().whenComplete((n,e)->sync(()->s.sendMessage(e==null?"Зарегистрировано мечей: "+n:"Ошибка: "+e.getMessage())));return true;}
        if(op.equals("reload")){plugin.reloadConfig();s.sendMessage("Конфигурация перечитана. Параметры подключения применятся после рестарта.");return true;}
        if(!(s instanceof Player p)){s.sendMessage("Для этой операции нужен игрок с мечом в руке");return true;}ItemStack held=p.getInventory().getItemInMainHand();Optional<UUID> id=items.bladeId(held);if(id.isEmpty()){s.sendMessage("Возьмите Клинок душ");return true;}
        if(op.equals("validate")){storage.get(id.get()).whenComplete((d,e)->sync(()->s.sendMessage(e==null&&d.isPresent()?"Подпись и запись БД действительны":"Проверка не пройдена")));return true;}
        if((op.equals("setkills")||op.equals("setlevel"))&&a.length>=2){try{long kills=op.equals("setlevel")?leveling.threshold(Integer.parseInt(a[1])):Long.parseLong(a[1]);if(kills<0)throw new NumberFormatException();storage.setKills(id.get(),kills).whenComplete((d,e)->sync(()->{if(e!=null)s.sendMessage("Ошибка: "+e.getMessage());else{items.update(held,d.kills());s.sendMessage("Убийства: "+d.kills()+", уровень: "+leveling.level(d.kills()));}}));}catch(Exception e){s.sendMessage("Некорректное значение");}return true;}
        return onCommand(s,null,"",new String[]{"info"});
    }
    private boolean denied(CommandSender s){s.sendMessage(Component.text("Недостаточно прав.",NamedTextColor.RED));return true;}
    private void sync(Runnable r){Bukkit.getScheduler().runTask(plugin,r);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){if(a.length==1)return List.of("info","history","recipe","performance","diagnose","admin");if(a.length==2&&a[0].equalsIgnoreCase("admin"))return List.of("give","setkills","setlevel","inspect","validate","stats","reload");return List.of();}
}
