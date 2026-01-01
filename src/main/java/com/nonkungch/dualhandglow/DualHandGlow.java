package com.nonkungch.dualhandglow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration; // <-- เพิ่ม
import org.bukkit.configuration.file.YamlConfiguration; // <-- เพิ่ม
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File; // <-- เพิ่ม
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DualHandGlow extends JavaPlugin implements Listener {

    private final Map<UUID, Location> playerLightBlock = new HashMap<>();
    private final Map<UUID, ArmorStand> fakeOffhands = new HashMap<>();
    private BukkitTask mainTask;

    // --- ส่วนของไฟล์ภาษา ---
    private FileConfiguration langConfig;
    private FileConfiguration fallbackLangConfig;
    // ----------------------

    @Override
    public void onEnable() {
        // 1. สร้างและโหลด config.yml หลัก
        saveDefaultConfig();
        
        // 2. สร้างไฟล์ภาษาเริ่มต้น (lang/en.yml, lang/th.yml)
        createLangFiles();

        // 3. โหลดไฟล์ภาษาที่ผู้ใช้เลือก (หรือ en.yml ถ้าหาไม่เจอ)
        loadLang();

        getLogger().info("✅ DualHandGlow (Block Light Edition) Enabled!");
        Bukkit.getPluginManager().registerEvents(this, this);
        startUpdater();
    }

    @Override
    public void onDisable() {
        if (mainTask != null) mainTask.cancel();

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

        getLogger().info("✅ DualHandGlow Disabled - cleaned up all lights and entities.");
    }

    /**
     * สร้างไฟล์ภาษาเริ่มต้นในโฟลเดอร์ /lang/ หากยังไม่มี
     */
    private void createLangFiles() {
        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        // บันทึกไฟล์ภาษาจากใน JAR ไปยังโฟลเดอร์ปลั๊กอิน (จะไม่เขียนทับถ้าไฟล์มีอยู่แล้ว)
        saveResource("lang/en.yml", false);
        saveResource("lang/th.yml", false);
    }

    /**
     * โหลดไฟล์ภาษาที่เลือกใน config.yml
     */
    private void loadLang() {
        // โหลด en.yml เป็นไฟล์สำรองเสมอ
        File enFile = new File(getDataFolder(), "lang/en.yml");
        fallbackLangConfig = YamlConfiguration.loadConfiguration(enFile);

        // หาว่าผู้ใช้เลือกภาษาอะไร
        String langKey = getConfig().getString("language", "en");
        
        // ถ้าเลือก en อยู่แล้ว ก็ใช้ไฟล์สำรองไปเลย
        if (langKey.equalsIgnoreCase("en")) {
            langConfig = fallbackLangConfig;
            return;
        }

        // ถ้าเลือกภาษาอื่น ให้ลองโหลดไฟล์นั้นๆ
        File langFile = new File(getDataFolder(), "lang/" + langKey + ".yml");
        if (!langFile.exists()) {
            // ถ้าไฟล์ภาษาที่เลือกไม่มีอยู่จริง
            getLogger().warning("Language file 'lang/" + langKey + ".yml' not found. Using 'en.yml' as fallback.");
            langConfig = fallbackLangConfig;
        } else {
            // ถ้าไฟล์มีอยู่จริง โหลดและตั้งค่าให้ en.yml เป็น default
            // (หาก key ไหนไม่มีในภาษานั้น จะไปดึงจาก en.yml มาแทน)
            langConfig = YamlConfiguration.loadConfiguration(langFile);
            langConfig.setDefaults(fallbackLangConfig);
        }
    }

    /**
     * ดึงข้อความจากไฟล์ภาษาที่โหลดมา
     */
    private String getMessage(String key) {
        // ดึงข้อความจาก langConfig (ซึ่งจะดึงจาก fallbackLangConfig (en.yml) ให้อัตโนมัติถ้าไม่มี)
        String message = langConfig.getString(key);

        if (message == null) {
            // ถ้า key ไม่มีอยู่จริง แม้แต่ใน en.yml
            return ChatColor.RED + "Error: Missing lang key '" + key + "'";
        }
        
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    // ... (เมธอดอื่นๆ ของปลั๊กอินที่ไม่เปลี่ยนแปลง) ...
    // startUpdater(), isSameBlock(), isLightItem(), updateFakeOffhand(),
    // handLocationFor(), removeFakeOffhand(), onQuit()
    // ... (คัดลอกส่วนที่เหลือของไฟล์ .java เดิมของคุณมาวางที่นี่ได้เลย) ...

    private void startUpdater() {
        mainTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack off = p.getInventory().getItemInOffHand();
                boolean hasOff = off != null && off.getType() != Material.AIR;

                if (hasOff && isLightItem(off.getType())) {
                    
                    Location playerLoc = p.getLocation();
                    Location blockLoc = playerLoc.clone();
                    
                    blockLoc.setX(Math.floor(blockLoc.getX()) + 0.5); 
                    blockLoc.setY(Math.floor(blockLoc.getY()) + 1); 
                    blockLoc.setZ(Math.floor(blockLoc.getZ()) + 0.5);
                    Block targetBlock = blockLoc.getBlock();

                    Location prev = playerLightBlock.get(p.getUniqueId());
                    if (prev == null || !isSameBlock(prev, blockLoc)) {
                        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
                            prev.getBlock().setType(Material.AIR);
                        }
                        if (targetBlock.getType() == Material.AIR) {
                            targetBlock.setType(Material.LIGHT);
                            targetBlock.setBlockData(Bukkit.createBlockData("minecraft:light[level=15]"));
                            playerLightBlock.put(p.getUniqueId(), targetBlock.getLocation());
                        } else {
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
        }, 0L, 10L);
    }

    private boolean isSameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld().equals(b.getWorld());
    }

    private boolean isLightItem(Material type) {
        return type == Material.TORCH ||
               type == Material.LANTERN ||
               type == Material.SOUL_TORCH ||
               type == Material.SOUL_LANTERN ||
               type == Material.GLOWSTONE ||
               type == Material.SEA_LANTERN ||
               type == Material.SHROOMLIGHT ||
               type == Material.JACK_O_LANTERN ||
               type == Material.REDSTONE_TORCH;
    }

    private void updateFakeOffhand(Player p, ItemStack offItem) {
        UUID id = p.getUniqueId();
        ArmorStand stand = fakeOffhands.get(id);

        if (stand == null || stand.isDead()) {
            ArmorStand s = p.getWorld().spawn(p.getLocation(), ArmorStand.class);
            s.setInvisible(true);
            s.setMarker(true); 
            s.setGravity(false);
            s.setSmall(true);
            s.setSilent(true);
            s.teleport(handLocationFor(p));
            s.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
            fakeOffhands.put(id, s);
            return;
        }

        Location standLoc = stand.getLocation();
        Location needed = handLocationFor(p);

        if (!isSameBlock(standLoc, needed)) {
            stand.teleport(needed);
        }

        ItemStack cur = stand.getEquipment().getItem(EquipmentSlot.HAND);
        if (cur == null || !cur.isSimilar(offItem)) {
            stand.getEquipment().setItem(EquipmentSlot.HAND, offItem.clone());
        }
    }

    private Location handLocationFor(Player p) {
        Location loc = p.getLocation().clone();
        loc.add(0.25 * Math.sin(Math.toRadians(p.getLocation().getYaw())), 1.45, -0.25 * Math.cos(Math.toRadians(p.getLocation().getYaw())));
        loc.setPitch(0);
        return loc;
    }

    private void removeFakeOffhand(Player p) {
        ArmorStand stand = fakeOffhands.remove(p.getUniqueId());
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Location prev = playerLightBlock.remove(id);
        if (prev != null && prev.getBlock().getType() == Material.LIGHT) {
            prev.getBlock().setType(Material.AIR);
        }
        ArmorStand s = fakeOffhands.remove(id);
        if (s != null && !s.isDead()) s.remove();
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            // ส่วนนี้จะดึงข้อความจาก getMessage() เหมือนเดิม
            sender.sendMessage(getMessage("command-player-only"));
            return true;
        }
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("offhand") || label.equalsIgnoreCase("ofh")) {
            
            ItemStack main = p.getInventory().getItemInMainHand();
            ItemStack off = p.getInventory().getItemInOffHand();

            p.getInventory().setItemInMainHand(off);
            p.getInventory().setItemInOffHand(main);

            p.sendMessage(getMessage("command-swapped"));
            return true;
        }
        return false;
    }
}
