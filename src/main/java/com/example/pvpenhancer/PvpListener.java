package com.example.pvpenhancer;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public class PvpListener implements Listener {

    private final PvPEnhancerPlugin plugin;

    private boolean fallDamageOff;
    private boolean disableDrops;
    private boolean ensureDamage; private double ensureMinHearts;
    private boolean feelCombo;    private String soundHit; private String soundVictim; private String particles;

    private boolean intelligentKb;

    private boolean debug;

    public PvpListener(PvPEnhancerPlugin plugin) {
        this.plugin = plugin;
        loadCfg();
    }

    public void loadCfg() {
        FileConfiguration c = plugin.getConfig();

        fallDamageOff = c.getBoolean("fall-damage", true);
        disableDrops = c.getBoolean("disable-item-drops", true);

        ensureDamage = c.getBoolean("ensure-hit-damage.enabled", true);
        ensureMinHearts = c.getDouble("ensure-hit-damage.min-hearts", 0.5);

        feelCombo = c.getBoolean("feel.combo-actionbar", false);
        soundHit = c.getString("feel.hit-sound", "ENTITY_PLAYER_ATTACK_STRONG");
        soundVictim = c.getString("feel.victim-sound", "ENTITY_PLAYER_HURT");
        particles = c.getString("feel.particles", "CRIT");

        intelligentKb = c.getBoolean("intelligent-kb.enabled", true);
        plugin.reloadAllEngines();

        debug = c.getBoolean("debug", false);
    }

    public boolean isIntelligentEnabled() { return intelligentKb; }

    public void setIntelligentMode(String m) {
        plugin.getConfig().set("intelligent-kb.mode", m.toUpperCase());
        plugin.saveConfig();
        loadCfg();
    }

    // QoL apply
    public void applyAttackSpeed(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (inst != null) inst.setBaseValue(16.0);
    }
    public void applyMaxNoDamageTicks(Player p) {
        p.setMaximumNoDamageTicks(8);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        applyAttackSpeed(e.getPlayer());
        applyMaxNoDamageTicks(e.getPlayer());
        plugin.getEngineForPlayer(e.getPlayer());
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        plugin.removeEngineForPlayer(e.getPlayer());
        plugin.getLogger().info("Removed engine for " + e.getPlayer().getName());
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { applyAttackSpeed(e.getPlayer()); applyMaxNoDamageTicks(e.getPlayer()); }, 1L);
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { applyAttackSpeed(e.getPlayer()); applyMaxNoDamageTicks(e.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFall(EntityDamageEvent e) {
        if (fallDamageOff && e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        if (disableDrops) e.setCancelled(true);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (!intelligentKb) return;
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            IntelligentEngine engine = plugin.getEngineForPlayer(e.getPlayer());
            engine.recordSwing();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!intelligentKb) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj) {
            ProjectileSource ps = proj.getShooter();
            if (ps instanceof Player p) attacker = p;
        }

        if (attacker != null) {
            IntelligentEngine atkEngine = plugin.getEngineForPlayer(attacker);
            atkEngine.recordHit();
        }

        if (soundVictim != null && !soundVictim.isEmpty()) {
            try { victim.getWorld().playSound(victim.getLocation(), Sound.valueOf(soundVictim), 1f, 1f); } catch (IllegalArgumentException ignored) {}
        }
        if (particles != null && !particles.isEmpty()) {
            try { victim.getWorld().spawnParticle(Particle.valueOf(particles), victim.getLocation().add(0,1,0), 6, 0.15,0.15,0.15, 0.01); } catch (IllegalArgumentException ignored) {}
        }

        if (attacker != null && feelCombo) {
            try { attacker.playSound(attacker.getLocation(), Sound.valueOf(soundHit), 0.7f, 1.05f); } catch (IllegalArgumentException ignored) {}
            attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "✦"));
        }

        if (ensureDamage) {
            double min = Math.max(0.0, ensureMinHearts * 2.0);
            if (e.getDamage() < min) e.setDamage(min);
        }

        final LivingEntity vic = victim;
        if (!(vic instanceof Player)) return;
        final Player victimPlayer = (Player) vic;
        final IntelligentEngine engine = plugin.getEngineForPlayer(victimPlayer);
        final Player atk = attacker;
        final Entity damager = e.getDamager();
        final Vector playerInputVector = getPlayerDirectionalInput(victimPlayer);

        Runnable applyKb = () -> {
            Vector dir;
            if (atk != null) {
                dir = vic.getLocation().toVector().subtract(atk.getLocation().toVector()).setY(0).normalize();
            } else if (damager != null) {
                dir = vic.getLocation().toVector().subtract(damager.getLocation().toVector()).setY(0).normalize();
            } else { dir = new Vector(0, 0, 0); }

            engine.decay();
            engine.onHitContext(vic, vic, dir);
            IntelligentEngine.Profile p = engine.active();
            if (p == null) return;

            Vector base = vic.getVelocity().multiply(0.90); // friction légère
            double h = p.baseH;
            double v = p.baseV;

            if (!vic.isOnGround()) { h *= p.airMultH; v *= p.airMultV; }

            // directionnal bonuses
            if (atk != null) {
                Vector atkDir = atk.getLocation().getDirection().clone().setY(0).normalize();
                Vector toVic = vic.getLocation().toVector().subtract(atk.getLocation().toVector()).setY(0).normalize();
                double dot = atkDir.dot(toVic);
                if (dot < -0.3) h += p.backBoost;
                else if (Math.abs(dot) < 0.4) h += p.sideBoost;
            }

            Vector kb = dir.multiply(h);
            double y = v;
            if (!vic.isOnGround()) y *= 0.85;
            if (y > p.clampY) y = p.clampY;
            if (y < p.minY) y = p.minY;
            kb.setY(y);

            double influence = p.influenceStrength;
            Vector finalKb = kb.clone().multiply(1.0 - influence)
                    .add(playerInputVector.clone().multiply(kb.length() * influence));
            finalKb.setY(kb.getY());

            boolean wouldVoid = wouldFallIntoVoid(victimPlayer, kb);

            vic.setVelocity(base.add(finalKb));

            if (wouldVoid) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!victimPlayer.isDead() && victimPlayer.getLocation().getY() > 0) {
                        engine.recordInfluenceSave();
                    }
                }, 20L);
            }
        };

        Bukkit.getScheduler().runTask(plugin, applyKb);
    }

    private Vector getPlayerDirectionalInput(Player player) {
        return player.getLocation().getDirection().setY(0).normalize();
    }

    private boolean wouldFallIntoVoid(Player player, Vector kb) {
        Vector horiz = kb.clone().setY(0);
        if (horiz.lengthSquared() < 0.001) return false;
        Location target = player.getLocation().clone().add(horiz.normalize().multiply(3));
        World w = player.getWorld();
        int y = target.getBlockY();
        for (int i = 0; i < 5 && y - i >= w.getMinHeight(); i++) {
            if (!w.getBlockAt(target.getBlockX(), y - i, target.getBlockZ()).getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
