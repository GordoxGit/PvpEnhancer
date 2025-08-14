package com.example.pvpenhancer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PvpCommand implements CommandExecutor, TabCompleter {
    private final PvPEnhancerPlugin plugin;
    private final PvpListener listener;
    private final AdminMenu admin;

    public PvpCommand(PvPEnhancerPlugin plugin, PvpListener listener, AdminMenu admin) {
        this.plugin = plugin;
        this.listener = listener;
        this.admin = admin;
    }

    private void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "=== PvPEnhancer v1.4.0 ===");
        s.sendMessage(ChatColor.YELLOW + "/pvp admin" + ChatColor.GRAY + " - menu admin (ON/OFF + mode AUTO/HIKABRAIN/ARENA)");
        s.sendMessage(ChatColor.YELLOW + "/pvp reload" + ChatColor.GRAY + " - reload config");
        s.sendMessage(ChatColor.YELLOW + "/pvp mode <joueur> <mode>" + ChatColor.GRAY + " - force le mode d'un joueur");
        s.sendMessage(ChatColor.YELLOW + "/pvp info" + ChatColor.GRAY + " - état courant");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "admin": return admin.handleAdmin(sender);
            case "reload": {
                if (!sender.hasPermission("pvpe.admin")) { sender.sendMessage(ChatColor.RED + "Permission pvpe.admin"); return true; }
                plugin.reloadLocal();
                listener.loadCfg();
                sender.sendMessage(ChatColor.GREEN + "PvPEnhancer rechargé.");
                return true;
            }
            case "mode": {
                if (!sender.hasPermission("pvpe.admin")) {
                    sender.sendMessage(ChatColor.RED + "Permission pvpe.admin");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /pvp mode <joueur> <mode>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Joueur introuvable");
                    return true;
                }
                PvpEnhancerAPI.setPlayerGamemode(target, args[2]);
                sender.sendMessage(ChatColor.GREEN + "Mode pour " + target.getName() + ": " + args[2].toUpperCase());
                return true;
            }
            case "info": {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage(ChatColor.RED + "Commande réservée aux joueurs");
                    return true;
                }
                IntelligentEngine engine = plugin.getEngineForPlayer(player);
                sender.sendMessage(engine.settingsSummary(player.getName()).split("\n"));
                return true;
            }
            default: help(sender); return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("help","admin","reload","mode","info");
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mode")) {
            return Arrays.asList("FACTION", "HIKABRAIN", "DUEL", "TRAINING", "DEFAULT");
        }
        return new ArrayList<>();
    }
}
