package com.example.pvpenhancer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AdminMenu implements Listener {

    private final PvPEnhancerPlugin plugin;
    private final PvpListener listener;

    public AdminMenu(PvPEnhancerPlugin plugin, PvpListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    public boolean handleAdmin(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("In-game only"); return true; }
        if (!p.hasPermission("pvpe.admin")) { p.sendMessage(ChatColor.RED + "Permission pvpe.admin"); return true; }
        openMain(p);
        return true;
    }

    private ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && lore.length > 0) meta.setLore(java.util.Arrays.asList(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    public void openMain(Player p) {
        FileConfiguration c = plugin.getConfig();
        Inventory inv = Bukkit.createInventory(p, 27, "PvP Admin");
        boolean intel = c.getBoolean("intelligent-kb.enabled", true);
        String mode   = c.getString("intelligent-kb.mode","AUTO");

        inv.setItem(11, named(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "KB Intelligent",
                ChatColor.GRAY + "Actuel: " + intel,
                ChatColor.YELLOW + "Clique pour basculer (ON/OFF)"));
        inv.setItem(12, named(Material.COMPASS, ChatColor.AQUA + "Mode Intelligent",
                ChatColor.GRAY + "Actuel: " + mode,
                ChatColor.YELLOW + "Clique: AUTO → HIKABRAIN → ARENA"));
        inv.setItem(15, named(Material.ANVIL, ChatColor.GOLD + "Sauver & Recharger",
                ChatColor.GRAY + "Recharge config & presets"));

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals("PvP Admin")) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;
        Material t = it.getType();
        switch (t) {
            case NETHER_STAR -> {
                boolean cur = plugin.getConfig().getBoolean("intelligent-kb.enabled", true);
                plugin.getConfig().set("intelligent-kb.enabled", !cur);
                plugin.saveConfig(); listener.loadCfg(); openMain(p);
            }
            case COMPASS -> {
                String mode = plugin.getConfig().getString("intelligent-kb.mode","AUTO");
                String next = "AUTO".equalsIgnoreCase(mode) ? "HIKABRAIN" : ("HIKABRAIN".equalsIgnoreCase(mode) ? "ARENA" : "AUTO");
                plugin.getConfig().set("intelligent-kb.mode", next);
                plugin.saveConfig(); listener.loadCfg(); openMain(p);
            }
            case ANVIL -> {
                plugin.saveConfig(); listener.loadCfg();
                p.sendMessage(ChatColor.GREEN + "Config & presets rechargés.");
                p.closeInventory();
            }
            default -> {}
        }
    }
}
