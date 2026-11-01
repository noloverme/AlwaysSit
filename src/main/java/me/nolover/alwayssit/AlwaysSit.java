package me.nolover.alwayssit;

import me.nolover.alwayssit.command.AlwaysSitCommand;
import me.nolover.alwayssit.listener.ProjectileListener;
import me.nolover.alwayssit.manager.SittingManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class AlwaysSit extends JavaPlugin {

    private SittingManager sittingManager;
    private boolean preventSelfDamage;
    private boolean preventFallDamage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        sittingManager = new SittingManager(this);
        sittingManager.start();

        AlwaysSitCommand command = new AlwaysSitCommand(this);
        if (getCommand("asit") != null) {
            getCommand("asit").setExecutor(command);
            getCommand("asit").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new ProjectileListener(this), this);

        getLogger().info("AlwaysSit v" + getDescription().getVersion() + " успешно включен на " + getServer().getVersion() + "!");
    }

    @Override
    public void onDisable() {
        if (sittingManager != null) {
            sittingManager.stop();
        }
        getLogger().info("AlwaysSit выключен.");
    }

    public void loadSettings() {
        preventSelfDamage = getConfig().getBoolean("settings.prevent-self-damage", true);
        preventFallDamage = getConfig().getBoolean("settings.prevent-fall-damage", true);
    }

    public SittingManager getSittingManager() {
        return sittingManager;
    }

    public boolean isPreventSelfDamage() {
        return preventSelfDamage;
    }

    public boolean isPreventFallDamage() {
        return preventFallDamage;
    }

    public String getMessage(String path, String... replacements) {
        String prefix = getConfig().getString("messages.prefix", "&8[&6AlwaysSit&8] ");
        String msg = getConfig().getString("messages." + path, path);

        String full = prefix + msg;
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    full = full.replace(replacements[i], replacements[i + 1]);
                }
            }
        }

        return ChatColor.translateAlternateColorCodes('&', full);
    }
}
