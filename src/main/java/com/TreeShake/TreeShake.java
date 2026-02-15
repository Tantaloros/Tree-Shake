package com.TreeShake;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TreeShake extends JavaPlugin implements Listener {

    private Economy econ;
    private final NamespacedKey cooldownKey = new NamespacedKey(this, "tree_cooldown");
    private final Map<Player, Long> playerCooldowns = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        String typeName = block.getType().name();
        if (!typeName.endsWith("_LOG") && !typeName.endsWith("_STEM")) {
            return;
        }

        Player player = event.getPlayer();

        // Per-player cooldown (60 seconds = 60_000 ms)
        long now = System.currentTimeMillis();
        long lastShake = playerCooldowns.getOrDefault(player, 0L);
        if (now - lastShake < 10_000) {
            long remaining = (10_000 - (now - lastShake)) / 1000;
            player.sendMessage(ChatColor.YELLOW + "The tree needs " + remaining + " more seconds to recover.");
            return;
        }

        // ────────────────────────────────────────────────
        // Effects
        player.playSound(block.getLocation(), Sound.BLOCK_WOOD_HIT, 0.8f, 1.2f);

        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        player.getWorld().spawnParticle(Particle.ENCHANT, center, 25, 0.4, 0.4, 0.4, 0);

        for (int i = 1; i <= 5; i++) {
            player.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0, i * 0.5, 0), 8, 0.2, 0.2, 0.2, 0);
        }

        Location dropLoc = center.clone().add(0, 0.5, 0);

        // Random drop logic
        int chance = ThreadLocalRandom.current().nextInt(100) + 1;
        int twigChance = getConfig().getInt("twig-chance", 80);

        if (chance <= twigChance) {
            int amount = ThreadLocalRandom.current().nextInt(1, 5);
            block.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.STICK, amount));
            player.sendMessage(ChatColor.GREEN + "Twigs fell from the tree!");
        } else {
            // 50/50 split between more sticks or a bell
            if (ThreadLocalRandom.current().nextBoolean()) {
                int amount = ThreadLocalRandom.current().nextInt(1, 4);
                block.getWorld().dropItemNaturally(dropLoc, new ItemStack(Material.STICK, amount));
                player.sendMessage(ChatColor.GREEN + "More twigs fell from the tree!");
            } else {
                // Bell drop
                ItemStack bell = new ItemStack(Material.GOLD_NUGGET, 1);
                ItemMeta meta = bell.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + "Bell");
                    meta.setLore(java.util.List.of(
                            ChatColor.GRAY + "Island currency",
                            ChatColor.GRAY + "Right-click to deposit (coming soon)"
                    ));
                    meta.getPersistentDataContainer().set(
                            new NamespacedKey(this, "bell_value"),
                            PersistentDataType.INTEGER,
                            100
                    );
                    bell.setItemMeta(meta);
                }
                block.getWorld().dropItemNaturally(dropLoc, bell);
                player.sendMessage(ChatColor.GOLD + "A Bell fell from the tree!");
            }
        }

        // Record the shake time
        playerCooldowns.put(player, now);
    }
}