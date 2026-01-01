package com.nonkungch.dualhandglow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DualHandGlow extends JavaPlugin implements Listener {

    private final Map<UUID, Location> playerLightBlock = new HashMap<>();
    private final Map<UUID, ArmorStand> fakeOffhands = new HashMap<>();
    private BukkitTask mainTask;
    private FileConfiguration langConfig;
    private FileConfiguration fallbackLangConfig;

    @Override
    public void onEnable() {
        // [1] โหลด Config และจัดการไฟล์ภาษา
        saveDefaultConfig();
        createLangFiles();
        loadLang();
        
        // [2] ลงทะเบียน Event และเริ่มระบบแสง
        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdater();
        
        getLogger().info("DualHandGlow v2.0 Fully Loaded!");
    }

    @Override
    public void onDisable() {
        if (mainTask != null) mainTask.cancel();
        cleanup();
    }

    private void cleanup() {
        for (Location loc : playerLightBlock.values()) {
            if (loc != null && loc.getBlock().getType() == Material.LIGHT) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        playerLightBlock.clear();
        for (ArmorStand stand : fakeOffhands.values()) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        fakeOffhands.clear();
    }

    // --- [ระบบสลับมือสำหรับมือถือ: แตะหน้าจอค้าง] ---
    @EventHandler
    public void onHandSwapInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack mainHand = p.getInventory().getItemInMainHand();

        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) 
            && isLightItem(mainHand.getType())) {
            
            if (p.isSneaking()) return; // ย่อตัว = วางบล็อกปกติ

            ItemStack offHand = p.getInventory().getItemInOffHand();
            p.getInventory().setItemInOffHand(mainHand);
            p.getInventory().setItemInMainHand(offHand);
            
            p.sendMessage(getMessage("command-swapped"));
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
            e.setCancelled(true);
        }
    }

    // --- [ระบบคำนวณระดับแสงตามประเภทบล็อก] ---
    private int getLightLevel(Material type) {
        switch (type) {
            case TORCH: case LANTERN: case CAMPFIRE:
            case GLOWSTONE: case SEA_LANTERN: case SHROOMLIGHT:
            case OCHRE_FROGLIGHT: case PEARLESCENT_FROGLIGHT: case VERDANT_FROGLIGHT:
            case CONDUIT: case BEACON: case LAVA_BUCKET: case SEA_PICKLE:
                return 15;
            case GLOW_BERRIES: case CANDLE: case JACK_O_LANTERN:
                return 14;
            case SOUL_TORCH: case SOUL_LANTERN: case SOUL_CAMPFIRE: case CRYING_OBSIDIAN:
                return 10;
            case MAGMA_BLOCK: case GLOW_LICHEN: case REDSTONE_TORCH:
                return 7;
            default:
                return 0;
        }
    }

    private boolean isLightItem(Material type) {
        return getLightLevel(type) > 0;
    }

    // --- [ระบบอัปเดตแสง Smooth Realistic] ---
    private void startUpdater() {
        mainTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack off = p.getInventory().getItemInOffHand();
                
                if (off != null && isLightItem(off.getType())) {
                    Location blockLoc = p.getLocation().clone().add(0, 1.2, 0); // ระดับอก
                    Block targetBlock = blockLoc.getBlock();

                    Location prev = playerLightBlock.get(p.getUniqueId());
                    if (prev == null || !isSameBlock(prev, blockLoc)) {
                        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                            prev.getBlock().setType(Material.AIR);
                        }
                        if (targetBlock.getType() == Material.AIR || targetBlock.getType() == Material.LIGHT) {
                            targetBlock.setType(Material.LIGHT);
                            int level = getLightLevel(off.getType());
                            targetBlock.setBlockData(Bukkit.createBlockData("minecraft:light[level=" + level + "]"));
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        }
                    }
                    updateFakeOffhand(p, off);
                } else {
                    Location prev = playerLightBlock.remove(p.getUniqueId());
                    if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                        prev.getBlock().setType(Material.AIR);
                    }
                    removeFakeOffhand(p);
                }
            }
        }, 0L, 2L);
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() 
               && a.getBlockZ() == b.getBlockZ() && a.getWorld().equals(b.getWorld());
    }

    // --- [ระบบไฟล์ภาษาและ Config] ---
    private void createLangFiles() {
        File folder = new File(getDataFolder(), "lang");
        if (!folder.exists()) folder.mkdirs();
        if (!new File(getDataFolder(), "lang/en.yml").exists()) saveResource("lang/en.yml", false);
        if (!new File(getDataFolder(), "lang/th.yml").exists()) saveResource("lang/th.yml", false);
    }

    private void loadLang() {
        File enFile = new File(getDataFolder(), "lang/en.yml");
        fallbackLangConfig = YamlConfiguration.loadConfiguration(enFile);
        
        String langKey = getConfig().getString("language", "en");
        File langFile = new File(getDataFolder(), "lang/" + langKey + ".yml");
        
        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        } else {
            langConfig = fallbackLangConfig;
        }
    }

    private String getMessage(String key) {
        String msg = langConfig.getString(key, fallbackLangConfig.getString(key, "&cMissing key: " + key));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    // --- [คำสั่ง /offhand] ---
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("command-player-only"));
            return true;
        }
        Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("offhand") || label.equalsIgnoreCase("ofh")) {
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();
            p.getInventory().setItemInOffHand(main);
            p.getInventory().setItemInMainHand(off);
            p.sendMessage(getMessage("command-swapped"));
            return true;
        }
        return false;
    }

    // --- [ระบบแสดงผลไอเทม ArmorStand] ---
    private void updateFakeOffhand(Player p, ItemStack offItem) {
        UUID id = p.getUniqueId();
        ArmorStand stand = fakeOffhands.get(id);
        if (stand == null || stand.isDead()) {
            ArmorStand s = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSmall(true);
            s.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
            fakeOffhands.put(id, s);
            return;
        }
        stand.teleport(handLocationFor(p));
        if (!stand.getEquipment().getItem(EquipmentSlot.HAND).isSimilar(offItem)) {
            stand.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
        }
    }

    private Location handLocationFor(Player p) {
        Location loc = p.getLocation().clone();
        loc.add(0.25 * Math.sin(Math.toRadians(p.getLocation().getYaw())), 1.45, -0.25 * Math.cos(Math.toRadians(p.getLocation().getYaw())));
        return loc;
    }

    private void removeFakeOffhand(Player p) {
        ArmorStand stand = fakeOffhands.remove(p.getUniqueId());
        if (stand != null) stand.remove();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Location prev = playerLightBlock.remove(id);
        if (prev != null && prev.getBlock().getType() == Material.LIGHT) prev.getBlock().setType(Material.AIR);
        removeFakeOffhand(e.getPlayer());
    }
}
