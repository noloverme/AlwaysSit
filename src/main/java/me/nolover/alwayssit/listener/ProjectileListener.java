package me.nolover.alwayssit.listener;

import me.nolover.alwayssit.AlwaysSit;
import me.nolover.alwayssit.manager.PlayerSession;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles projectile launches, hits, and damage prevention.
 */
public class ProjectileListener implements Listener {

    private final AlwaysSit plugin;

    public ProjectileListener(AlwaysSit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player) {
            Player player = (Player) projectile.getShooter();
            plugin.getSittingManager().handleLaunchedEntity(player, projectile);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player)) return;

        Player shooter = (Player) projectile.getShooter();
        if (event.getHitEntity() != null && event.getHitEntity().equals(shooter)) {
            // Cancel self-hit so the projectile continues flying with the passenger
            if (plugin.isPreventSelfDamage()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSelfDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isPreventSelfDamage()) return;

        if (event.getDamager() instanceof Projectile && event.getEntity() instanceof Player) {
            Projectile projectile = (Projectile) event.getDamager();
            Player player = (Player) event.getEntity();
            if (player.equals(projectile.getShooter()) && plugin.getSittingManager().isTracking(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!plugin.isPreventFallDamage()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.getSittingManager().isTracking(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getSittingManager().handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        PlayerSession session = plugin.getSittingManager().getSession(event.getPlayer().getUniqueId());
        if (session != null) {
            session.clear();
            session.setReferenceLocation(event.getPlayer().getLocation());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        PlayerSession session = plugin.getSittingManager().getSession(event.getEntity().getUniqueId());
        if (session != null) {
            session.clear();
        }
    }
}
