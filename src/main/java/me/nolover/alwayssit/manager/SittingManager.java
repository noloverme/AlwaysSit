package me.nolover.alwayssit.manager;

import me.nolover.alwayssit.AlwaysSit;
import me.nolover.alwayssit.util.RidingHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player sessions and the continuous seating on furthest projectiles.
 */
public class SittingManager {

    private final AlwaysSit plugin;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> managedEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private BukkitTask tickTask;

    public SittingManager(AlwaysSit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("settings.tick-interval", 1);
        if (interval < 1) interval = 1;

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, interval);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        // Dismount all players
        for (PlayerSession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player != null && player.isOnline()) {
                RidingHelper.dismount(player);
            }
            session.clear();
        }
        sessions.clear();
        managedEntities.clear();
    }

    public boolean isTracking(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public PlayerSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void startTracking(Player player, EntityType targetType) {
        PlayerSession session = sessions.computeIfAbsent(player.getUniqueId(), id -> new PlayerSession(id, targetType));
        session.setTargetType(targetType);
        session.setReferenceLocation(player.getLocation());

        // Fast scan nearby for any projectiles launched just before the command was issued
        if (player.getWorld() != null) {
            for (Entity nearby : player.getNearbyEntities(64.0, 64.0, 64.0)) {
                if (nearby instanceof Projectile) {
                    Projectile proj = (Projectile) nearby;
                    if (proj.isValid() && !proj.isDead() && player.equals(proj.getShooter())) {
                        if (session.isWildcard() || session.getTargetType() == proj.getType()) {
                            session.addProjectile(proj);
                            managedEntities.add(proj.getUniqueId());
                        }
                    }
                }
            }
        }
        updatePlayer(player, session);
    }

    public boolean stopTracking(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            RidingHelper.dismount(player);
            if (plugin.isPreventFallDamage()) {
                player.setFallDistance(0.0f);
            }
            session.clear();
            return true;
        }
        return false;
    }

    public void handlePlayerQuit(UUID uuid) {
        PlayerSession session = sessions.remove(uuid);
        if (session != null) {
            session.clear();
        }
    }

    public void handleLaunchedEntity(Player player, Entity entity) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        // Check if entity type matches target (or wildcard)
        if (session.isWildcard() || session.getTargetType() == entity.getType()) {
            managedEntities.add(entity.getUniqueId());
            session.addProjectile(entity);

            if (session.getReferenceLocation() == null || player.getVehicle() == null) {
                session.setReferenceLocation(player.getLocation());
            }

            // Run next tick so the entity is fully registered in the world
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    updatePlayer(player, session);
                }
            });
        }
    }

    public boolean isManagedEntity(Entity entity) {
        return entity != null && managedEntities.contains(entity.getUniqueId());
    }

    private void tick() {
        for (PlayerSession session : sessions.values()) {
            Player player = Bukkit.getPlayer(session.getPlayerUuid());
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            updatePlayer(player, session);
        }
    }

    public void updatePlayer(Player player, PlayerSession session) {
        // Efficient localized check for projectiles near the player that might have bypassed launch events
        if (player.getWorld() != null) {
            for (Entity nearby : player.getNearbyEntities(48.0, 48.0, 48.0)) {
                if (nearby instanceof Projectile) {
                    Projectile proj = (Projectile) nearby;
                    if (proj.isValid() && !proj.isDead() && player.equals(proj.getShooter())) {
                        if (session.isWildcard() || session.getTargetType() == proj.getType()) {
                            session.addProjectile(proj);
                            managedEntities.add(proj.getUniqueId());
                        }
                    }
                }
            }
        }

        // Clean up finished / landed / dead projectiles
        session.getActiveProjectiles().removeIf(entity -> {
            if (entity.isDead()) {
                managedEntities.remove(entity.getUniqueId());
                return true;
            }
            // Allow freshly spawned entities to initialize
            if (!entity.isValid() && entity.getTicksLived() > 2) {
                managedEntities.remove(entity.getUniqueId());
                return true;
            }
            if (entity instanceof AbstractArrow) {
                AbstractArrow arrow = (AbstractArrow) entity;
                if (arrow.isInBlock() || (arrow.isOnGround() && arrow.getTicksLived() > 5)) {
                    managedEntities.remove(arrow.getUniqueId());
                    return true;
                }
            }
            return false;
        });

        // Current vehicle check
        Entity currentVehicle = player.getVehicle();
        if (currentVehicle == null || !session.getActiveProjectiles().contains(currentVehicle)) {
            session.setCurrentVehicle(null);
            session.setReferenceLocation(player.getLocation());
        }

        // If there are active projectiles, find the furthest one from referenceLocation
        if (!session.getActiveProjectiles().isEmpty()) {
            Location refLoc = session.getReferenceLocation();
            if (refLoc == null || refLoc.getWorld() == null || !refLoc.getWorld().equals(player.getWorld())) {
                refLoc = player.getLocation();
                session.setReferenceLocation(refLoc);
            }

            Entity furthest = null;
            double maxDistanceSq = -1.0;

            for (Entity entity : session.getActiveProjectiles()) {
                if (entity.getWorld() == null || !entity.getWorld().equals(refLoc.getWorld())) continue;

                double distSq = entity.getLocation().distanceSquared(refLoc);
                if (distSq > maxDistanceSq) {
                    maxDistanceSq = distSq;
                    furthest = entity;
                }
            }

            if (furthest != null) {
                if (player.getVehicle() != furthest) {
                    RidingHelper.mount(player, furthest);
                    session.setCurrentVehicle(furthest);
                }

                if (plugin.isPreventFallDamage()) {
                    player.setFallDistance(0.0f);
                }
            }
        } else {
            // No active projectiles left
            if (currentVehicle != null && managedEntities.contains(currentVehicle.getUniqueId())) {
                RidingHelper.dismount(player);
                session.setCurrentVehicle(null);
                session.setReferenceLocation(player.getLocation());
                if (plugin.isPreventFallDamage()) {
                    player.setFallDistance(0.0f);
                }
            }
        }
    }
}
