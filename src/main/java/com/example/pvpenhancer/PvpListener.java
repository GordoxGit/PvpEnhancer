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
    private boolean feelCombo;    private String soundHit; private String soundVictim; private String particles; private boolean overrideVanillaSounds;
    private boolean dynamicAudioEnabled; private float dynamicBasePitch; private float dynamicPitchPerHit; private float dynamicMaxPitch;

    private boolean intelligentKb;

    private boolean intentVectoringEnabled; private double verticalSculptFactor; private double horizontalSculptFactor; private double activationThreshold;

    private boolean rhythmResonanceEnabled; private double maxDeviationFactor;

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
        overrideVanillaSounds = c.getBoolean("feel.override-vanilla-sounds", true);
        dynamicAudioEnabled = c.getBoolean("feel.dynamic-audio.enabled", true);
        dynamicBasePitch = (float) c.getDouble("feel.dynamic-audio.base-pitch", 0.9);
        dynamicPitchPerHit = (float) c.getDouble("feel.dynamic-audio.pitch-per-combo-hit", 0.05);
        dynamicMaxPitch = (float) c.getDouble("feel.dynamic-audio.max-pitch", 1.6);

        intelligentKb = c.getBoolean("intelligent-kb.enabled", true);

        intentVectoringEnabled = c.getBoolean("intent-vectoring.enabled", true);
        verticalSculptFactor = c.getDouble("intent-vectoring.vertical-sculpt-factor", 0.02);
        horizontalSculptFactor = c.getDouble("intent-vectoring.horizontal-sculpt-factor", 0.015);
        activationThreshold = c.getDouble("intent-vectoring.activation-threshold", 1.5);

        rhythmResonanceEnabled = c.getBoolean("rhythm-resonance.enabled", true);
        maxDeviationFactor = c.getDouble("rhythm-resonance.max-deviation-factor", 0.10);
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
        if (inst != null) inst.setBaseValue(1024.0);
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
    public void onSweepAttack(EntityDamageByEntityEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
        }
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
        if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!intelligentKb) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj) {
            ProjectileSource ps = proj.getShooter();
            if (ps instanceof Player p) attacker = p;
        }

        final Player finalAttacker = attacker;
        final float startPitch = (attacker != null) ? attacker.getLocation().getPitch() : 0f;
        final float startYaw = (attacker != null) ? attacker.getLocation().getYaw() : 0f;
        final double[] deltas = {0.0, 0.0};

        double purityScore = 1.0;
        long now = System.currentTimeMillis();
        IntelligentEngine atkEngine = null;
        if (attacker != null) {
            atkEngine = plugin.getEngineForPlayer(attacker);
            long last = atkEngine.getLastHitTimestamp();
            long interval = (last > 0) ? (now - last) : 0;
            if (last > 0 && interval < 2000) {
                atkEngine.addHitInterval(interval);
            }
            atkEngine.setLastHitTimestamp(now);
            atkEngine.recordHit();

            double tempo = atkEngine.getPlayerTempo();
            if (interval > 0 && tempo > 0) {
                double diff = Math.abs(interval - tempo);
                purityScore = 1.0 - Math.min(diff / tempo, 1.0);
            }
        }

        if (particles != null && !particles.isEmpty()) {
            try { victim.getWorld().spawnParticle(Particle.valueOf(particles), victim.getLocation().add(0,1,0), 6, 0.15,0.15,0.15, 0.01); } catch (IllegalArgumentException ignored) {}
        }

        if (attacker != null) {
            if (dynamicAudioEnabled && soundHit != null && !soundHit.isEmpty() && atkEngine != null) {
                int comboCount = atkEngine.getCurrentCombo() - 1;
                if (comboCount < 0) comboCount = 0;
                float calculatedPitch = dynamicBasePitch + (comboCount * dynamicPitchPerHit);
                if (calculatedPitch > dynamicMaxPitch) {
                    calculatedPitch = dynamicMaxPitch;
                }
                try {
                    Sound hitSound = Sound.valueOf(soundHit);
                    attacker.playSound(attacker.getLocation(), hitSound, 0.8f, calculatedPitch);
                } catch (IllegalArgumentException ignored) {}
            } else if (feelCombo && soundHit != null && !soundHit.isEmpty()) {
                try { attacker.playSound(attacker.getLocation(), Sound.valueOf(soundHit), 0.7f, 1.05f); } catch (IllegalArgumentException ignored) {}
            }
            if (feelCombo) {
                attacker.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "✦"));
            }
        }

        if (ensureDamage) {
            double min = Math.max(0.0, ensureMinHearts * 2.0);
            if (e.getDamage() < min) e.setDamage(min);
        }

        if (overrideVanillaSounds) {
            double finalDamage = e.getFinalDamage();
            e.setCancelled(true);
            double newHealth = victim.getHealth() - finalDamage;
            if (newHealth < 0) newHealth = 0;
            victim.setHealth(newHealth);

            if (soundVictim != null && !soundVictim.isEmpty()) {
                float calculatedPitch = 1f;
                if (dynamicAudioEnabled && atkEngine != null) {
                    int comboCount = atkEngine.getCurrentCombo() - 1;
                    if (comboCount < 0) comboCount = 0;
                    calculatedPitch = dynamicBasePitch + (comboCount * dynamicPitchPerHit);
                    if (calculatedPitch > dynamicMaxPitch) {
                        calculatedPitch = dynamicMaxPitch;
                    }
                }
                try {
                    Sound victimSound = Sound.valueOf(soundVictim);
                    victim.getWorld().playSound(victim.getLocation(), victimSound, 1.0f, calculatedPitch);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        final LivingEntity vic = victim;
        if (!(vic instanceof Player)) return;
        final Player victimPlayer = (Player) vic;
        final IntelligentEngine engine = plugin.getEngineForPlayer(victimPlayer);
        final Player atk = finalAttacker;
        final Entity damager = e.getDamager();
        final Vector playerInputVector = getPlayerDirectionalInput(victimPlayer);
        final double attackCooldown = (attacker != null) ? attacker.getAttackCooldown() : 1.0;
        final Vector atkVelocity = (attacker != null) ? attacker.getVelocity().clone() : null;
        final Vector atkLookDir;
        if (attacker != null) {
            Vector look = attacker.getLocation().getDirection().clone().setY(0);
            atkLookDir = (look.lengthSquared() > 0.0001) ? look.normalize() : null;
        } else {
            atkLookDir = null;
        }
        final Vector atkMoveDir;
        if (attacker != null && atkVelocity != null) {
            Vector horiz = atkVelocity.clone().setY(0);
            if (horiz.lengthSquared() > 0.0001) {
                atkMoveDir = horiz.normalize();
            } else {
                Vector look = attacker.getLocation().getDirection().clone().setY(0);
                atkMoveDir = (look.lengthSquared() > 0.0001) ? look.normalize() : null;
            }
        } else {
            atkMoveDir = null;
        }
        final double purity = purityScore;

        Runnable applyKb = () -> {
            Vector dir;
            if (atk != null) {
                Vector diff = vic.getLocation().toVector().subtract(atk.getLocation().toVector()).setY(0);
                dir = (diff.lengthSquared() > 0.0001) ? diff.normalize() : new Vector(0, 0, 0);
            } else if (damager != null) {
                Vector diff = vic.getLocation().toVector().subtract(damager.getLocation().toVector()).setY(0);
                dir = (diff.lengthSquared() > 0.0001) ? diff.normalize() : new Vector(0, 0, 0);
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
                Vector look = atk.getLocation().getDirection().clone().setY(0);
                Vector atkDir = (look.lengthSquared() > 0.0001) ? look.normalize() : new Vector(0, 0, 0);
                Vector toVic = vic.getLocation().toVector().subtract(atk.getLocation().toVector()).setY(0);
                if (toVic.lengthSquared() > 0.0001) {
                    toVic.normalize();
                } else {
                    toVic = new Vector(0, 0, 0);
                }
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

            if (atkVelocity != null && atkLookDir != null && atkMoveDir != null) {
                double speed = atkVelocity.clone().setY(0).length();
                if (speed > 0.001) {
                    Vector moveDir = atkMoveDir.clone();
                    double frontal = atkLookDir.dot(moveDir);
                    if (frontal > 0.5) {
                        kb.add(moveDir.multiply(speed * 0.25));
                    } else if (Math.abs(frontal) <= 0.3) {
                        Vector lateral = moveDir.clone().subtract(atkLookDir.clone().multiply(frontal));
                        if (lateral.lengthSquared() > 1e-4) {
                            lateral.normalize();
                            kb.add(lateral.multiply(speed * 0.2));
                        }
                    } else if (frontal < -0.3) {
                        kb.multiply(0.85);
                    }
                }
            }

            double influence = p.influenceStrength;
            Vector finalKb = kb.clone().multiply(1.0 - influence)
                    .add(playerInputVector.clone().multiply(kb.length() * influence));

            if (attackCooldown < 0.8) {
                double spamFactor = 0.8 - attackCooldown;
                double randX = (Math.random() - 0.5) * spamFactor * 0.2 * kb.length();
                double randZ = (Math.random() - 0.5) * spamFactor * 0.2 * kb.length();
                finalKb.add(new Vector(randX, 0, randZ));
            }

            if (rhythmResonanceEnabled && purity < 1.0) {
                double deviationStrength = (1.0 - purity) * maxDeviationFactor;
                Vector deviation = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
                if (deviation.lengthSquared() > 0.0001) {
                    deviation.normalize();
                    finalKb.add(deviation.multiply(kb.length() * deviationStrength));
                }
            }

            if (intentVectoringEnabled) {
                double deltaPitch = deltas[0];
                double deltaYaw = deltas[1];
                if (Math.abs(deltaPitch) > activationThreshold && deltaPitch < 0) {
                    double horizontalMagnitude = new Vector(kb.getX(), 0, kb.getZ()).length();
                    double conversion = horizontalMagnitude * Math.abs(deltaPitch) * verticalSculptFactor;
                    kb.setY(kb.getY() + conversion);
                }
                if (Math.abs(deltaYaw) > activationThreshold) {
                    double angle = Math.toRadians(deltaYaw * horizontalSculptFactor);
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    double x = finalKb.getX();
                    double z = finalKb.getZ();
                    finalKb.setX(x * cos - z * sin);
                    finalKb.setZ(x * sin + z * cos);
                }
            }

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

        if (finalAttacker != null) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                float endPitch = finalAttacker.getLocation().getPitch();
                float endYaw = finalAttacker.getLocation().getYaw();
                deltas[0] = endPitch - startPitch;
                deltas[1] = endYaw - startYaw;
                Bukkit.getScheduler().runTask(plugin, applyKb);
            }, 2L);
        } else {
            Bukkit.getScheduler().runTask(plugin, applyKb);
        }
    }

    private Vector getPlayerDirectionalInput(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0);
        return (dir.lengthSquared() > 0.0001) ? dir.normalize() : new Vector(0, 0, 0);
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
