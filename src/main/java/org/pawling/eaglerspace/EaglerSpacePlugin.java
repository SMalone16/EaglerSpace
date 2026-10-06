package org.pawling.eaglerspace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EaglerSpacePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, SpaceState> spaceStates = new HashMap<>();

    private BukkitTask secondTicker;

    private double boundaryY;
    private long personalMidnightTicks;
    private int maxOxygenSeconds;
    private int damageIntervalSeconds;
    private double damageHearts;
    private int particleIntervalSeconds;
    private int ambientSoundIntervalSeconds;
    private boolean showTitle;
    private boolean showParticles;
    private boolean playSounds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);

        secondTicker = getServer().getScheduler().runTaskTimer(
                this,
                this::tickAllPlayers,
                20L,
                20L
        );

        // Handles /reload or hot-enable while players are already online.
        for (Player player : getServer().getOnlinePlayers()) {
            evaluateBoundary(player);
        }

        getLogger().info(
                "EaglerSpace enabled. Space begins above Y=" + boundaryY
                        + " with " + maxOxygenSeconds + " seconds of oxygen."
        );
    }

    @Override
    public void onDisable() {
        if (secondTicker != null) {
            secondTicker.cancel();
            secondTicker = null;
        }

        for (Player player : getServer().getOnlinePlayers()) {
            if (spaceStates.containsKey(player.getUniqueId())) {
                restoreNormalPlayerState(player);
            }
        }

        spaceStates.clear();
    }

    private void loadSettings() {
        reloadConfig();

        boundaryY = getConfig().getDouble("space-boundary-y", 220.0D);
        personalMidnightTicks = getConfig().getLong("personal-midnight-ticks", 18000L);
        maxOxygenSeconds = Math.max(1, getConfig().getInt("oxygen-seconds", 10));
        damageIntervalSeconds = Math.max(1, getConfig().getInt("damage-interval-seconds", 2));
        damageHearts = Math.max(0.0D, getConfig().getDouble("damage-hearts", 1.0D));
        particleIntervalSeconds = Math.max(1, getConfig().getInt("particle-interval-seconds", 3));
        ambientSoundIntervalSeconds = Math.max(
                1,
                getConfig().getInt("ambient-sound-interval-seconds", 12)
        );

        showTitle = getConfig().getBoolean("show-title", true);
        showParticles = getConfig().getBoolean("show-particles", true);
        playSounds = getConfig().getBoolean("play-sounds", true);
    }

    private void tickAllPlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            SpaceState state = spaceStates.get(playerId);

            if (state == null) {
                if (canEnterSpace(player)) {
                    enterSpace(player);
                }
                continue;
            }

            if (mustExitSpace(player)) {
                exitSpace(player);
                continue;
            }

            tickSpacePlayer(player, state);
        }
    }

    private void tickSpacePlayer(Player player, SpaceState state) {
        state.secondsInSpace++;

        boolean suppliedByWaterBreathing =
                player.hasPotionEffect(PotionEffectType.WATER_BREATHING);

        if (suppliedByWaterBreathing) {
            state.oxygenSeconds = maxOxygenSeconds;
            state.secondsAtZero = 0;
        } else if (state.oxygenSeconds > 0) {
            // The player gets the full configured oxygen duration before damage starts.
            state.oxygenSeconds--;
            state.secondsAtZero = 0;
        } else {
            state.secondsAtZero++;

            if (state.secondsAtZero >= damageIntervalSeconds) {
                state.secondsAtZero = 0;

                if (damageHearts > 0.0D) {
                    player.damage(damageHearts * 2.0D);
                }

                if (playSounds) {
                    player.playSound(
                            player.getLocation(),
                            Sound.ENTITY_PLAYER_HURT_FREEZE,
                            SoundCategory.PLAYERS,
                            0.55F,
                            0.65F
                    );
                }
            }
        }

        sendOxygenDisplay(player, state.oxygenSeconds, suppliedByWaterBreathing);

        if (showParticles && state.secondsInSpace % particleIntervalSeconds == 0) {
            spawnSpaceParticles(player);
        }

        if (playSounds && state.secondsInSpace % ambientSoundIntervalSeconds == 0) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ENDERMAN_AMBIENT,
                    SoundCategory.AMBIENT,
                    0.30F,
                    0.55F
            );
        }
    }

    private boolean canEnterSpace(Player player) {
        return isNormalOverworld(player)
                && player.getLocation().getY() > boundaryY;
    }

    private boolean mustExitSpace(Player player) {
        return !isNormalOverworld(player)
                || player.getLocation().getY() < boundaryY;
    }

    private boolean isNormalOverworld(Player player) {
        return player.getWorld().getEnvironment() == World.Environment.NORMAL;
    }

    private void evaluateBoundary(Player player) {
        boolean inSpace = spaceStates.containsKey(player.getUniqueId());

        if (!inSpace && canEnterSpace(player)) {
            enterSpace(player);
            return;
        }

        if (inSpace && mustExitSpace(player)) {
            exitSpace(player);
        }
    }

    private void enterSpace(Player player) {
        UUID playerId = player.getUniqueId();

        if (spaceStates.containsKey(playerId)) {
            return;
        }

        SpaceState state = new SpaceState(maxOxygenSeconds);
        spaceStates.put(playerId, state);

        player.setPlayerTime(personalMidnightTicks, false);

        if (showTitle) {
            player.sendTitle(
                    "§b☄ ENTERING SPACE ☄",
                    "§7Oxygen systems online",
                    10,
                    50,
                    15
            );
        }

        if (playSounds) {
            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.AMBIENT,
                    0.45F,
                    0.70F
            );
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ENDERMAN_AMBIENT,
                    SoundCategory.AMBIENT,
                    0.30F,
                    0.50F
            );
        }

        if (showParticles) {
            spawnSpaceParticles(player);
        }

        sendOxygenDisplay(
                player,
                maxOxygenSeconds,
                player.hasPotionEffect(PotionEffectType.WATER_BREATHING)
        );
    }

    private void exitSpace(Player player) {
        if (spaceStates.remove(player.getUniqueId()) == null) {
            return;
        }

        restoreNormalPlayerState(player);

        if (showTitle) {
            player.sendTitle(
                    "§fRETURNING TO ATMOSPHERE",
                    "§7Space Mode disabled",
                    5,
                    30,
                    10
            );
        }
    }

    private void restoreNormalPlayerState(Player player) {
        player.resetPlayerTime();
        player.sendActionBar(Component.empty());

        if (playSounds) {
            player.stopSound(Sound.ENTITY_ENDERMAN_AMBIENT, SoundCategory.AMBIENT);
            player.stopSound(Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT);
        }
    }

    private void sendOxygenDisplay(
            Player player,
            int oxygenSeconds,
            boolean suppliedByWaterBreathing
    ) {
        int segments = 10;
        int filled = (int) Math.ceil(
                (oxygenSeconds / (double) maxOxygenSeconds) * segments
        );
        filled = Math.max(0, Math.min(segments, filled));

        String bar = "█".repeat(filled) + "░".repeat(segments - filled);

        if (suppliedByWaterBreathing) {
            player.sendActionBar(
                    Component.text(
                            "OXYGEN: " + bar + " " + maxOxygenSeconds + "s  [SUPPLIED]",
                            NamedTextColor.AQUA
                    )
            );
        } else if (oxygenSeconds <= 0) {
            player.sendActionBar(
                    Component.text(
                            "OXYGEN: " + bar + " 0s  - VACUUM",
                            NamedTextColor.RED
                    )
            );
        } else if (oxygenSeconds <= Math.max(2, maxOxygenSeconds / 3)) {
            player.sendActionBar(
                    Component.text(
                            "OXYGEN: " + bar + " " + oxygenSeconds + "s",
                            NamedTextColor.YELLOW
                    )
            );
        } else {
            player.sendActionBar(
                    Component.text(
                            "OXYGEN: " + bar + " " + oxygenSeconds + "s",
                            NamedTextColor.AQUA
                    )
            );
        }
    }

    private void spawnSpaceParticles(Player player) {
        Location center = player.getLocation().add(0.0D, 1.0D, 0.0D);

        player.spawnParticle(
                Particle.END_ROD,
                center,
                4,
                0.8D,
                0.7D,
                0.8D,
                0.01D
        );

        player.spawnParticle(
                Particle.REVERSE_PORTAL,
                center,
                6,
                1.0D,
                0.8D,
                1.0D,
                0.01D
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        if (Math.abs(event.getFrom().getY() - event.getTo().getY()) < 0.0001D) {
            return;
        }

        evaluateBoundary(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTask(
                this,
                () -> evaluateBoundary(event.getPlayer())
        );
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        getServer().getScheduler().runTask(
                this,
                () -> evaluateBoundary(event.getPlayer())
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        spaceStates.remove(event.getPlayer().getUniqueId());
    }

    private static final class SpaceState {
        private int oxygenSeconds;
        private int secondsAtZero;
        private int secondsInSpace;

        private SpaceState(int oxygenSeconds) {
            this.oxygenSeconds = oxygenSeconds;
            this.secondsAtZero = 0;
            this.secondsInSpace = 0;
        }
    }
}
