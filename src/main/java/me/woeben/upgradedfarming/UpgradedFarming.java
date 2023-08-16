package me.woeben.upgradedfarming;

import com.badbones69.crazyenchantments.CrazyEnchantments;
import com.badbones69.crazyenchantments.api.CrazyManager;
import com.badbones69.crazyenchantments.api.objects.CEnchantment;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Crops;
import org.bukkit.material.NetherWarts;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpgradedFarming extends JavaPlugin implements Listener {

    Map<Material, Integer> toolRanges = new HashMap<>();
    Map<Material, Material> cropsMap = new HashMap<>();


    @Override
    public void onEnable() {

        // Populate settings
        toolRanges.put(Material.WOODEN_HOE, 1);
        toolRanges.put(Material.STONE_HOE, 1);
        toolRanges.put(Material.IRON_HOE, 1);
        toolRanges.put(Material.GOLDEN_HOE, 1);
        toolRanges.put(Material.DIAMOND_HOE, 1);
        toolRanges.put(Material.NETHERITE_HOE, 1);

        cropsMap.put(Material.WHEAT, Material.WHEAT_SEEDS);
        cropsMap.put(Material.CARROTS, Material.CARROT);
        cropsMap.put(Material.POTATOES, Material.POTATO);
        cropsMap.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        cropsMap.put(Material.NETHER_WART, Material.NETHER_WART);

        // Register the event listeners.
        this.getServer().getPluginManager().registerEvents(this, this);
        // Set listeners.
        
        Bukkit.getLogger().info("UpgradedFarming has been enabled.");
    }
    

    
    @EventHandler
    public void onHarvest(PlayerInteractEvent e) {
        var block = e.getPlayer().getTargetBlockExact(5);
        var player = e.getPlayer();
        var tool = e.getPlayer().getInventory().getItemInMainHand();
        
        if (block == null) return;
        if (!toolRanges.containsKey(tool.getType())) return;
        
        var range = toolRanges.get(tool.getType());
        if (!cropsMap.containsKey(block.getType())) return;
        
        // If crop is not full-grown, prevent harvest
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() != ageable.getMaximumAge()) {
                e.setCancelled(true);
                return;
            }
        }
        
        
        // Valid harvest.
        harvestSwingParticles(player);
        List<Block> near = getNearCrops(block, range);
        CompletableFuture<Boolean> seeds = null;
        
        for (Block crop : near) {
            seeds = replantCrop(player, crop.getLocation(), crop.getType());
            breakCrop(player, crop, tool);
        }
        
        seeds.thenAccept(success -> {
            if (success) {
            } else {
                player.sendMessage(ChatColor.RED + "Not enough seeds to auto replant!");
            }
        });
    }
    
    private void breakCrop(Player player, Block crop, ItemStack tool) {
        ItemMeta itemMeta = tool.getItemMeta();
        
        if (itemMeta != null && itemMeta.hasLore()) {
            for (String loreLine : itemMeta.getLore()) {
                if (loreLine.contains("Telepathy")) {
                    for (ItemStack drop : crop.getDrops(tool)) {
                        
                        player.getInventory().addItem(drop);
                    }
                    crop.setType(Material.AIR);
                    return;
                }
            }
        }
        crop.breakNaturally(tool, true);
    }
    
    public CompletableFuture<Boolean> replantCrop(Player player, Location loc, Material cropBlockType) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Check if player has seeds
            if (!consumeItem(player, 1, cropsMap.get(cropBlockType))) {
                result.complete(false);
                return;
            }
            
            Block block = loc.getBlock();
            block.setType(cropBlockType);
            loc.getWorld().spawnParticle(Particle.WATER_DROP, loc, 10, 0.5, 0.5, 0.5);
            result.complete(true);
        }, 5L);
        
        return result;
    }
    
    
    public static boolean consumeItem(Player player, int count, Material mat) {
        Map<Integer, ? extends ItemStack> seeds = player.getInventory().all(mat);
        
        int found = 0;
        for (ItemStack stack : seeds.values())
            found += stack.getAmount();
        if (count > found)
            return false;
        
        for (Integer index : seeds.keySet()) {
            ItemStack stack = seeds.get(index);
            
            int removed = Math.min(count, stack.getAmount());
            count -= removed;
            
            if (stack.getAmount() == removed)
                player.getInventory().setItem(index, null);
            else
                stack.setAmount(stack.getAmount() - removed);
            
            if (count <= 0)
                break;
        }
        
        player.updateInventory();
        return true;
    }
    
    
    
    private void harvestSwingParticles(Player player) {
        Vector direction = player.getLocation().getDirection();
        //direction.add(new Vector(0.0, -direction.getY(), 0.0)).multiply(1.0 / direction.length());
        Location location = player.getEyeLocation();
        location.add(direction.multiply(1.5));
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 1);
    }

    private List<Block> getNearCrops(Block crop, int range) {
        List<Block> crops = new ArrayList<>();
        Material type = crop.getType();
        var center = crop.getLocation();
        var centerX = center.getBlockX();
        var centerY = center.getBlockY();
        var centerZ = center.getBlockZ();

        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int y = centerY - range; y <= centerY + range; y++) {
                for (int z = centerZ - range; z <= centerZ + range; z++) {
                    var block = center.getWorld().getBlockAt(x, y, z);
                    if (block.getType() != type) continue;
                    if (block.getBlockData() instanceof Ageable ageable) {
                        if (ageable.getAge() != ageable.getMaximumAge()) continue;
                    }

                    crops.add(block);
                }
            }
        }
        return crops;
    }
}
