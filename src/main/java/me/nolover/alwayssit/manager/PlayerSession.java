package me.nolover.alwayssit.manager;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks an active AlwaysSit session for a player.
 */
public class PlayerSession {
    private final UUID playerUuid;
    private EntityType targetType; // null indicates wildcard (all flying projectiles)
    private final Set<Entity> activeProjectiles = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Location referenceLocation;
    private Entity currentVehicle;

    public PlayerSession(UUID playerUuid, EntityType targetType) {
        this.playerUuid = playerUuid;
        this.targetType = targetType;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public EntityType getTargetType() {
        return targetType;
    }

    public void setTargetType(EntityType targetType) {
        this.targetType = targetType;
        this.activeProjectiles.clear();
    }

    public boolean isWildcard() {
        return targetType == null;
    }

    public Set<Entity> getActiveProjectiles() {
        return activeProjectiles;
    }

    public void addProjectile(Entity entity) {
        if (entity != null && entity.isValid()) {
            activeProjectiles.add(entity);
        }
    }

    public void removeProjectile(Entity entity) {
        activeProjectiles.remove(entity);
    }

    public Location getReferenceLocation() {
        return referenceLocation;
    }

    public void setReferenceLocation(Location referenceLocation) {
        this.referenceLocation = referenceLocation != null ? referenceLocation.clone() : null;
    }

    public Entity getCurrentVehicle() {
        return currentVehicle;
    }

    public void setCurrentVehicle(Entity currentVehicle) {
        this.currentVehicle = currentVehicle;
    }

    public void clear() {
        activeProjectiles.clear();
        currentVehicle = null;
        referenceLocation = null;
    }
}
