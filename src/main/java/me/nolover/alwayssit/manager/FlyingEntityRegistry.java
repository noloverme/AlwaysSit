package me.nolover.alwayssit.manager;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Flying;
import org.bukkit.entity.Projectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of all flying / projectile entities across Minecraft Paper versions (1.16.5 - 1.21+).
 */
public final class FlyingEntityRegistry {

    private static final Map<String, EntityType> ENTITY_MAP = new LinkedHashMap<>();
    private static final List<String> SUGGESTIONS = new ArrayList<>();

    static {
        // Projectiles supported in 1.16.5+
        register("arrow", EntityType.ARROW);
        register("spectral_arrow", EntityType.SPECTRAL_ARROW);
        register("trident", EntityType.TRIDENT);
        register("snowball", EntityType.SNOWBALL);
        register("egg", EntityType.EGG);
        register("ender_pearl", EntityType.ENDER_PEARL);
        register("splash_potion", EntityType.SPLASH_POTION);
        register("experience_bottle", EntityType.THROWN_EXP_BOTTLE);
        register("firework_rocket", EntityType.FIREWORK);
        register("fireball", EntityType.FIREBALL);
        register("small_fireball", EntityType.SMALL_FIREBALL);
        register("dragon_fireball", EntityType.DRAGON_FIREBALL);
        register("wither_skull", EntityType.WITHER_SKULL);
        register("shulker_bullet", EntityType.SHULKER_BULLET);
        register("llama_spit", EntityType.LLAMA_SPIT);
        register("fishing_bobber", EntityType.FISHING_HOOK);

        // Flying mobs in 1.16.5+
        register("bat", EntityType.BAT);
        register("blaze", EntityType.BLAZE);
        register("ghast", EntityType.GHAST);
        register("phantom", EntityType.PHANTOM);
        register("bee", EntityType.BEE);
        register("parrot", EntityType.PARROT);
        register("vex", EntityType.VEX);
        register("ender_dragon", EntityType.ENDER_DRAGON);
        register("wither", EntityType.WITHER);

        // Entities introduced in Minecraft 1.19+ and 1.21+ (registered dynamically if present)
        registerSafe("allay", "ALLAY");
        registerSafe("breeze", "BREEZE");
        registerSafe("wind_charge", "WIND_CHARGE");
        registerSafe("breeze_wind_charge", "BREEZE_WIND_CHARGE");

        // Convenient aliases
        addAlias("pearl", EntityType.ENDER_PEARL);
        addAlias("enderpearl", EntityType.ENDER_PEARL);
        addAlias("potion", EntityType.SPLASH_POTION);
        addAlias("exp_bottle", EntityType.THROWN_EXP_BOTTLE);
        addAlias("exp", EntityType.THROWN_EXP_BOTTLE);
        addAlias("experience", EntityType.THROWN_EXP_BOTTLE);
        addAlias("firework", EntityType.FIREWORK);
        addAlias("rocket", EntityType.FIREWORK);
        addAlias("fishing_hook", EntityType.FISHING_HOOK);
        addAlias("bobber", EntityType.FISHING_HOOK);
        addAlias("hook", EntityType.FISHING_HOOK);
        addAlias("dragon", EntityType.ENDER_DRAGON);

        addAliasSafe("windcharge", "WIND_CHARGE");
        addAliasSafe("wind", "WIND_CHARGE");

        // Automatically discover any projectile or flying entities present in future/modded versions
        discoverAvailableEntities();

        // Wildcard suggestion (any / all flying entities)
        if (!SUGGESTIONS.contains("all")) {
            SUGGESTIONS.add(0, "all");
        }
    }

    private FlyingEntityRegistry() {
    }

    private static void register(String key, EntityType type) {
        if (type == null) return;
        ENTITY_MAP.put(key, type);
        if (!SUGGESTIONS.contains(key)) {
            SUGGESTIONS.add(key);
        }
    }

    private static void addAlias(String alias, EntityType type) {
        if (type != null) {
            ENTITY_MAP.put(alias, type);
        }
    }

    private static void registerSafe(String key, String entityTypeName) {
        try {
            EntityType type = EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT));
            register(key, type);
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            // EntityType not present in this server version
        }
    }

    private static void addAliasSafe(String alias, String entityTypeName) {
        try {
            EntityType type = EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT));
            addAlias(alias, type);
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            // EntityType not present in this server version
        }
    }

    /**
     * Inspects runtime EntityType to automatically detect any newer Projectile or Flying entities.
     */
    private static void discoverAvailableEntities() {
        for (EntityType type : EntityType.values()) {
            if (type == null) continue;
            try {
                Class<?> clazz = type.getEntityClass();
                if (clazz != null && (Projectile.class.isAssignableFrom(clazz) || Flying.class.isAssignableFrom(clazz))) {
                    String key = type.name().toLowerCase(Locale.ROOT);
                    if (!ENTITY_MAP.containsValue(type)) {
                        ENTITY_MAP.put(key, type);
                        if (!SUGGESTIONS.contains(key)) {
                            SUGGESTIONS.add(key);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean isWildcard(String name) {
        if (name == null) return false;
        String clean = name.toLowerCase(Locale.ROOT).trim();
        return clean.equals("all") || clean.equals("any") || clean.equals("*");
    }

    public static EntityType parse(String name) {
        if (name == null) return null;
        String clean = name.toLowerCase(Locale.ROOT).trim();
        if (clean.startsWith("minecraft:")) {
            clean = clean.substring("minecraft:".length());
        }

        if (ENTITY_MAP.containsKey(clean)) {
            return ENTITY_MAP.get(clean);
        }

        String withoutUnderscores = clean.replace("_", "");
        for (Map.Entry<String, EntityType> entry : ENTITY_MAP.entrySet()) {
            if (entry.getKey().replace("_", "").equalsIgnoreCase(withoutUnderscores)) {
                return entry.getValue();
            }
        }

        try {
            return EntityType.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String getDisplayName(EntityType type) {
        if (type == null) {
            return "all (любой запущенный объект)";
        }
        for (Map.Entry<String, EntityType> entry : ENTITY_MAP.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return type.name().toLowerCase(Locale.ROOT);
    }

    public static List<String> getSuggestions() {
        return Collections.unmodifiableList(SUGGESTIONS);
    }
}
