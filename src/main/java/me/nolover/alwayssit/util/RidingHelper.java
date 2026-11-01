package me.nolover.alwayssit.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Utility to reliably mount a player onto any entity (including projectiles like ender pearls/arrows)
 * across Minecraft Paper 1.16.5 through 1.21+ and beyond.
 *
 * Employs a tiered approach:
 * 1. Standard Bukkit {@code vehicle.addPassenger(player)} (Paper supports force startRiding)
 * 2. Cross-version NMS reflection (compatible with 1.16.5 through 1.21+, Java 11/17/21+)
 * 3. Minecraft 1.20+ vanilla {@code /ride} command (only executed when available, avoiding console errors on 1.16-1.19)
 */
public final class RidingHelper {

    private static Method getPlayerHandleMethod;
    private static Method getEntityHandleMethod;
    private static Method startRidingMethod;
    private static boolean nmsInitialized = false;
    private static Boolean rideCommandSupported = null;

    private RidingHelper() {
    }

    public static boolean isRideCommandSupported() {
        if (rideCommandSupported == null) {
            try {
                rideCommandSupported = Bukkit.getHelpMap().getHelpTopic("/ride") != null;
            } catch (Throwable t) {
                rideCommandSupported = false;
            }
        }
        return rideCommandSupported;
    }

    private static void initNMS(Player player, Entity entity) {
        if (nmsInitialized) return;
        nmsInitialized = true;
        try {
            getPlayerHandleMethod = player.getClass().getMethod("getHandle");
            getEntityHandleMethod = entity.getClass().getMethod("getHandle");

            Object playerHandle = getPlayerHandleMethod.invoke(player);
            Class<?> playerHandleClass = playerHandle.getClass();

            // First pass: look by known method names (startRiding or obfuscated a)
            for (Method m : playerHandleClass.getMethods()) {
                if (m.getParameterCount() == 2 && m.getParameterTypes()[1] == boolean.class) {
                    if (m.getName().equals("startRiding") || m.getName().equals("a")) {
                        trySetAccessible(m);
                        startRidingMethod = m;
                        return;
                    }
                }
            }

            // Second pass: fallback for any version where name is different, taking (Entity, boolean)
            for (Method m : playerHandleClass.getMethods()) {
                if (m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == boolean.class
                        && (m.getReturnType() == boolean.class || m.getReturnType() == void.class)) {
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (!p0.isPrimitive() && p0 != String.class) {
                        trySetAccessible(m);
                        startRidingMethod = m;
                        return;
                    }
                }
            }

            // Third pass: single parameter startRiding(Entity) fallback
            for (Method m : playerHandleClass.getMethods()) {
                if (m.getParameterCount() == 1
                        && (m.getName().equals("startRiding") || m.getName().equals("a"))
                        && (m.getReturnType() == boolean.class || m.getReturnType() == void.class)) {
                    Class<?> p0 = m.getParameterTypes()[0];
                    if (!p0.isPrimitive() && p0 != String.class) {
                        trySetAccessible(m);
                        startRidingMethod = m;
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            Bukkit.getLogger().log(Level.FINE, "NMS startRiding reflection could not be initialized", t);
        }
    }

    private static void trySetAccessible(Method m) {
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
            // Java 17+ module boundary protection may throw InaccessibleObjectException,
            // but public methods remain invokable without setAccessible
        }
    }

    /**
     * Mounts player on vehicle using all available methods across server versions.
     *
     * @param player  The player passenger
     * @param vehicle The vehicle entity (e.g. EnderPearl, Arrow, WindCharge)
     * @return true if player is now riding the vehicle
     */
    public static boolean mount(Player player, Entity vehicle) {
        if (player == null || vehicle == null || !vehicle.isValid() || vehicle.isDead()) {
            return false;
        }

        if (player.getVehicle() != null && player.getVehicle().equals(vehicle)) {
            return true;
        }

        // 1. Standard Bukkit addPassenger (direct, fast, supported on all versions)
        try {
            if (vehicle.addPassenger(player)) {
                if (player.getVehicle() != null && player.getVehicle().equals(vehicle)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Cross-version NMS startRiding with force = true
        try {
            initNMS(player, vehicle);
            if (startRidingMethod != null && getPlayerHandleMethod != null && getEntityHandleMethod != null) {
                Object playerHandle = getPlayerHandleMethod.invoke(player);
                Object vehicleHandle = getEntityHandleMethod.invoke(vehicle);
                Object res = startRidingMethod.getParameterCount() == 2
                        ? startRidingMethod.invoke(playerHandle, vehicleHandle, true)
                        : startRidingMethod.invoke(playerHandle, vehicleHandle);
                if (Boolean.TRUE.equals(res) || (player.getVehicle() != null && player.getVehicle().equals(vehicle))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Minecraft 1.20+ vanilla /ride command fallback (only if supported by the server)
        if (isRideCommandSupported()) {
            try {
                String uuidCmd = "ride " + player.getName() + " mount " + vehicle.getUniqueId();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), uuidCmd);
                if (player.getVehicle() != null && player.getVehicle().equals(vehicle)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }

            try {
                String typeKey = vehicle.getType().getKey().toString();
                String selectorCmd = "execute at " + player.getName() + " run ride " + player.getName()
                        + " mount @e[type=" + typeKey + ",limit=1,sort=furthest]";
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), selectorCmd);
            } catch (Throwable ignored) {
            }
        }

        return player.getVehicle() != null && player.getVehicle().equals(vehicle);
    }

    public static void dismount(Player player) {
        if (player == null) return;
        try {
            if (player.getVehicle() != null) {
                player.leaveVehicle();
            }
        } catch (Throwable ignored) {
        }

        if (isRideCommandSupported()) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ride " + player.getName() + " dismount");
            } catch (Throwable ignored) {
            }
        }
    }
}
