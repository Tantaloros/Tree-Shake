package com.TreeShake;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
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
        if (now - lastShake < 60_000) {
            long remaining = (60_000 - (now - lastShake)) / 1000;
            player.sendMessage(ChatColor.YELLOW + "The tree needs " + remaining + " more seconds to recover.");
            return;
        }

        // ────────────────────────────────────────────────
        // All effects and drops happen here

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
            int bellChance = getConfig().getInt("bell-chance", 10);
            if (chance <= twigChance + bellChance) {
                int min = getConfig().getInt("bell-min", 20);
                int max = getConfig().getInt("bell-max", 150);
                double amount = ThreadLocalRandom.current().nextDouble(min, max + 1);

                if (econ != null) {
                    EconomyResponse response = econ.depositPlayer(player, amount);
                    if (response.transactionSuccess()) {
                        ItemStack visual = new ItemStack(Material.EMERALD);
                        ItemMeta meta = visual.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.GOLD.toString() + (int) amount + " Bells");
                            visual.setItemMeta(meta);
                        }
                        block.getWorld().dropItemNaturally(dropLoc, visual);
                        player.sendMessage(ChatColor.GOLD + "You obtained " + (int) amount + " bells!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to award bells.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Economy unavailable.");
                }
            } else {
                int furniChance = getConfig().getInt("furniture-chance", 5);
                if (chance <= twigChance + bellChance + furniChance) {
                    String furniStr = getConfig().getString("furniture-item", "OAK_STAIRS");
                    Material furniMat = Material.matchMaterial(furniStr.toUpperCase());
                    if (furniMat != null) {
                        block.getWorld().dropItemNaturally(dropLoc, new ItemStack(furniMat));
                        player.sendMessage(ChatColor.BLUE + "Furniture fell from the tree!");
                    } else {
                        player.sendMessage(ChatColor.RED + "Invalid furniture material configured.");
                    }
                } else {
                    // Bees
                    Location beeLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(3));
                    Bee bee = (Bee) block.getWorld().spawnEntity(beeLoc, EntityType.BEE);
                    bee.setTarget(player);
                    bee.setAnger(400); // 20 seconds
                    player.sendMessage(ChatColor.RED + "A swarm of bees has emerged!");
                    player.playSound(player.getLocation(), Sound.ENTITY_BEE_STING, 1.0f, 0.8f);
                }
            }
        }

        // Record the shake time
        playerCooldowns.put(player, now);
    }
}