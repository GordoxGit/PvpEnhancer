package com.example.pvpenhancer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manages the PvP profile for a single player and computes adaptive knockback
 * values based on the player's combat performance.
 */
public class IntelligentEngine {

    public static class Profile {
        public double baseH, baseV, minY, clampY, airMultH, airMultV, sideBoost, backBoost;
        public double influenceStrength;
    }

    private static final double NEUTRAL_BASE_H = 0.40;
    private static final double NEUTRAL_BASE_V = 0.25;
    private static final double NEUTRAL_MIN_Y = 0.10;
    private static final double NEUTRAL_CLAMP_Y = 0.30;
    private static final double NEUTRAL_AIR_MULT_H = 1.02;
    private static final double NEUTRAL_AIR_MULT_V = 0.80;
    private static final double NEUTRAL_SIDE_BOOST = 0.03;
    private static final double NEUTRAL_BACK_BOOST = 0.05;

    private final PvPEnhancerPlugin plugin;
    private Profile smooth;
    private double baseInfluenceStrength = 0.15;
    private double maxIaAdjustment = 0.10;

    private String currentGameMode = "DEFAULT";

    // detector (unused for now but kept for future balancing)
    private int belowAirCheck = 4;
    private double hitWeightNearVoid = 2.0;
    private double wallProximityWeight = 0.6;
    private double switchThreshold = 8.0;
    private double decayPerSecond = 1.0;
    private double emaAlpha = 0.30;

    private long lastTickDecay = System.currentTimeMillis();

    // precision metrics
    private int swingCount = 0;
    private int hitCount = 0;
    private double accuracyRatio = 0.0; // percentage

    // combat metrics
    private int currentCombo = 0;
    private int highestCombo = 0;
    private long lastHitTime = 0;

    // mobility metrics (future use)
    private double averageDistancePerSecond = 0.0;

    // track directional influence performance
    private int successfulInfluenceSaves = 0;

    // rhythm resonance tracking
    private final List<Long> hitIntervals = new ArrayList<>();
    private long lastHitTimestamp = 0;
    private double playerTempo = 500; // Tempo de base en ms

    public IntelligentEngine(PvPEnhancerPlugin plugin) {
        this.plugin = plugin;
        this.smooth = new Profile();
        this.smooth.baseH = NEUTRAL_BASE_H;
        this.smooth.baseV = NEUTRAL_BASE_V;
        this.smooth.minY = NEUTRAL_MIN_Y;
        this.smooth.clampY = NEUTRAL_CLAMP_Y;
        this.smooth.airMultH = NEUTRAL_AIR_MULT_H;
        this.smooth.airMultV = NEUTRAL_AIR_MULT_V;
        this.smooth.sideBoost = NEUTRAL_SIDE_BOOST;
        this.smooth.backBoost = NEUTRAL_BACK_BOOST;

        FileConfiguration cfg = plugin.getConfig();
        boolean diEnabled = cfg.getBoolean("directional-influence.enabled", true);
        this.baseInfluenceStrength = diEnabled ? cfg.getDouble("directional-influence.base-influence-strength", 0.15) : 0.0;
        this.maxIaAdjustment = cfg.getDouble("directional-influence.max-ia-adjustment", 0.10);
        this.smooth.influenceStrength = this.baseInfluenceStrength;
    }

    // Rhythm resonance helpers
    public long getLastHitTimestamp() { return lastHitTimestamp; }
    public void setLastHitTimestamp(long ts) { this.lastHitTimestamp = ts; }
    public void addHitInterval(long interval) {
        hitIntervals.add(interval);
        if (hitIntervals.size() > 10) {
            hitIntervals.remove(0);
        }
    }
    public double getPlayerTempo() { return playerTempo; }

    /**
     * Loads configuration parameters used to control the adaptive knockback engine.
     *
     * @param c configuration section from the plugin config
     */
    public void load(FileConfiguration c) {
        this.emaAlpha = c.getDouble("intelligent-kb.ema-alpha", 0.30);

        this.belowAirCheck = c.getInt("intelligent-kb.detector.below-air-check", 4);
        this.hitWeightNearVoid = c.getDouble("intelligent-kb.detector.hit-weight-near-void", 2.0);
        this.wallProximityWeight = c.getDouble("intelligent-kb.detector.wall-proximity-weight", 0.6);
        this.switchThreshold = c.getDouble("intelligent-kb.detector.switch-threshold", 8.0);
        this.decayPerSecond = c.getDouble("intelligent-kb.detector.decay-per-second", 1.0);

        boolean diEnabled = c.getBoolean("directional-influence.enabled", true);
        this.baseInfluenceStrength = diEnabled ? c.getDouble("directional-influence.base-influence-strength", 0.15) : 0.0;
        this.maxIaAdjustment = c.getDouble("directional-influence.max-ia-adjustment", 0.10);
        this.smooth.influenceStrength = this.baseInfluenceStrength;
    }

    /**
     * Sets the current PvP game mode for this player. The mode influences how
     * aggressively knockback values are scaled.
     *
     * @param mode name of the mode such as "FACTION" or "DUEL"
     */
    public void setCurrentGamemode(String mode) {
        if (mode == null || mode.isEmpty()) {
            this.currentGameMode = "DEFAULT";
        } else {
            this.currentGameMode = mode.toUpperCase(Locale.ROOT);
        }
    }

    private void applyEma(Profile target, double alpha) {
        if (smooth == null) smooth = new Profile();
        smooth.baseH    = smooth.baseH    + alpha * (target.baseH    - smooth.baseH);
        smooth.baseV    = smooth.baseV    + alpha * (target.baseV    - smooth.baseV);
        smooth.minY     = smooth.minY     + alpha * (target.minY     - smooth.minY);
        smooth.clampY   = smooth.clampY   + alpha * (target.clampY   - smooth.clampY);
        smooth.airMultH = smooth.airMultH + alpha * (target.airMultH - smooth.airMultH);
        smooth.airMultV = smooth.airMultV + alpha * (target.airMultV - smooth.airMultV);
        smooth.sideBoost= smooth.sideBoost+ alpha * (target.sideBoost- smooth.sideBoost);
        smooth.backBoost= smooth.backBoost+ alpha * (target.backBoost- smooth.backBoost);
        smooth.influenceStrength = smooth.influenceStrength + alpha * (target.influenceStrength - smooth.influenceStrength);
    }

    /**
     * Recalculates the target knockback values based on the player's current
     * accuracy, combo and selected game mode.
     */
    private void updateKnockbackProfile() {
        Profile target = new Profile();

        double scalingFactor = 1.0;
        switch (this.currentGameMode) {
            case "FACTION":
                scalingFactor = 0.25;
                break;
            case "HIKABRAIN":
                scalingFactor = 0.5;
                break;
            case "DUEL":
            case "TRAINING":
                scalingFactor = 1.2;
                break;
            default:
                break;
        }

        double acc = accuracyRatio / 100.0;
        // Reduce horizontal knockback when accuracy is high to keep pressure on the defender.
        double baseHModifier = 1.0 - ((acc - 0.5) * 0.1 * scalingFactor); // -5% to +5%
        target.baseH = NEUTRAL_BASE_H * baseHModifier;

        // Reward high combos with a slight boost to vertical knockback.
        double baseVModifier = 1.0 + (Math.min(currentCombo, 10) * 0.01 * scalingFactor); // +0% to +10%
        target.baseV = NEUTRAL_BASE_V * baseVModifier;

        target.minY = NEUTRAL_MIN_Y;
        target.clampY = NEUTRAL_CLAMP_Y;
        target.airMultH = NEUTRAL_AIR_MULT_H;
        target.airMultV = NEUTRAL_AIR_MULT_V;
        target.sideBoost = NEUTRAL_SIDE_BOOST;
        target.backBoost = NEUTRAL_BACK_BOOST;

        double baseStrength = this.baseInfluenceStrength;
        double maxAdjustment = this.maxIaAdjustment;
        double bonus = this.successfulInfluenceSaves * 0.005; // 0.5% per save
        if (bonus > maxAdjustment) {
            bonus = maxAdjustment;
        }
        target.influenceStrength = Math.max(0.0, baseStrength + bonus);

        applyEma(target, this.emaAlpha); // Smooth transition towards new target values.
    }

    /**
     * Applies decay to tracked metrics and refreshes the knockback profile at
     * a regular interval.
     */
    public void decay() {
        long now = System.currentTimeMillis();
        double secs = (now - lastTickDecay) / 1000.0;
        if (secs > 0.2) {
            lastTickDecay = now;
            updateMetrics(secs);
            updateKnockbackProfile();

            if (!hitIntervals.isEmpty()) {
                long total = 0;
                for (long interval : hitIntervals) {
                    total += interval;
                }
                playerTempo = total / (double) hitIntervals.size();
            }
        }
    }

    /**
     * Placeholder for future context-based adjustments each time an entity is hit.
     *
     * @param attacker entity that caused the hit
     * @param victim entity that was hit
     * @param dir direction vector of the hit
     */
    public void onHitContext(LivingEntity attacker, LivingEntity victim, Vector dir) {
        // future use for more advanced context-based adjustments
    }

    private void updateMetrics(double secs) {
        double factor = Math.pow(0.9, secs / 30.0); // 10% decay every 30s
        swingCount = (int) Math.round(swingCount * factor);
        hitCount = (int) Math.round(hitCount * factor);
        successfulInfluenceSaves = (int) Math.round(successfulInfluenceSaves * Math.pow(0.99, secs));
        updateAccuracy();
    }

    /**
     * Records a swing and updates accuracy metrics.
     */
    public void recordSwing() {
        decay();
        swingCount++;
        updateAccuracy();
    }

    /**
     * Records a successful hit, refreshing accuracy and combo counters.
     */
    public void recordHit() {
        decay();
        hitCount++;
        updateAccuracy();
        updateCombo();
    }

    public void recordInfluenceSave() {
        decay();
        successfulInfluenceSaves++;
        updateKnockbackProfile();
    }

    private void updateAccuracy() {
        this.accuracyRatio = swingCount > 0 ? (hitCount * 100.0) / swingCount : 0.0;
    }

    private void updateCombo() {
        long now = System.currentTimeMillis();
        if (now - lastHitTime <= 3000) {
            currentCombo++;
        } else {
            currentCombo = 1;
        }
        if (currentCombo > highestCombo) highestCombo = currentCombo;
        lastHitTime = now;
    }

    /**
     * Builds a human-readable summary of the player's current PvP metrics.
     *
     * @param player player name
     * @return formatted summary string
     */
    public String settingsSummary(String player) {
        Profile p = active();
        String kb = "";
        if (p != null) {
            kb = "§fProfil KB: §abaseH=" + String.format(Locale.US, "%.3f", p.baseH) +
                 " §abaseV=" + String.format(Locale.US, "%.3f", p.baseV) +
                 " §aairH=" + String.format(Locale.US, "%.3f", p.airMultH) +
                 " §aairV=" + String.format(Locale.US, "%.3f", p.airMultV) +
                 " §ainfluence=" + String.format(Locale.US, "%.3f", p.influenceStrength) + "\n";
        }
        return "§e=== Stats PvP de " + player + " ===\n" +
                "§fPrécision: §a" + String.format(Locale.US, "%.1f", accuracyRatio) + "%\n" +
                "§fMeilleur Combo: §a" + highestCombo + "\n" +
                kb +
                "§f(Debug) Hits/Swings: §7" + hitCount + "/" + swingCount;
    }

    /**
     * Retrieves the smoothed knockback profile currently in effect for the player.
     *
     * @return active profile instance
     */
    public Profile active() { return smooth; }
}
