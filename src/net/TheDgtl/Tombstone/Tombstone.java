package net.TheDgtl.Tombstone;

/**
 * Tombstone - A tombstone plugin for Bukkit
 * Copyright (C) 2011 Steven "Drakia" Scott <Drakia@Gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Giant;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.griefcraft.model.ProtectionTypes;
import com.nijikokun.bukkit.Permissions.Permissions;

public class Tombstone extends JavaPlugin {
    private final eListener entityListener = new eListener();
    private final bListener blockListener = new bListener();
    private final sListener serverListener = new sListener();
    private final pListener playerListener = new pListener();
    public static Logger log;
    PluginManager pm;
    
    private Permissions permissions = null;
    private LWCPlugin lwcPlugin = null;
    
    private ConcurrentLinkedQueue<TombBlock> tombList = new ConcurrentLinkedQueue<TombBlock>();
    private HashMap<Location, TombBlock> tombBlockList = new HashMap<Location, TombBlock>();
    private HashMap<String, ArrayList<TombBlock>> playerTombList = new HashMap<String, ArrayList<TombBlock>>();
    private HashMap<String, EntityDamageEvent> deathCause = new HashMap<String, EntityDamageEvent>();
    private Configuration config;
    private Tombstone plugin;
    
    /**
     * Configuration options - Defaults
     */
    private int lwcTime = 3600;
    private int removeTime = 18000;
    private boolean lwcEnable = true;
    private boolean lwcRemove = false;
    private boolean lwcPublic = false;
    private boolean tombRemove = false;
    private boolean tombSign = true;
    private boolean pMessage = true;
    private boolean saveTombList = true;
    private boolean destroyQuickLoot = false;
    private boolean noDestroy = false;
    private boolean noInterfere = true;
    private boolean logEvents = false;
    private boolean skipBuildCheck = false;
    Configuration signConfig = null;
    String signTemplate[] = new String[4];
    
    public void onEnable() {
        PluginDescriptionFile pdfFile = getDescription();
        log = Logger.getLogger("Minecraft");
        config = this.getConfiguration();
        
        // Create default config if needed
        File configFile = new File(this.getDataFolder(), "/config.yml");
        if (!configFile.exists()) defaultConfig();
        
        log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
        
        pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.PLAYER_INTERACT, playerListener, Priority.Highest, this);
        pm.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Priority.Monitor, this);
        
        permissions = (Permissions)checkPlugin("Permissions");
        lwcPlugin = (LWCPlugin)checkPlugin("LWC");
        plugin = this;
        
        reloadConfig();
        loadSignTemplate();
        for (World w : getServer().getWorlds())
            loadTombList(w.getName());
        
        // Start removal timer. Run every 5 seconds (20 ticks per second)
        if (lwcRemove || tombRemove)
            getServer().getScheduler().scheduleSyncRepeatingTask(this, new TombThread(), 0L, 100L);
    }
    
    public void reloadConfig() {
        config.load();
        lwcEnable = config.getBoolean("lwcEnable", lwcEnable);
        lwcTime = config.getInt("lwcTimeout", lwcTime);
        lwcRemove = config.getBoolean("lwcRemove", lwcRemove);
        lwcPublic = config.getBoolean("lwcPublic", lwcPublic);
        tombSign = config.getBoolean("tombSign", tombSign);
        removeTime = config.getInt("removeTime", removeTime);
        tombRemove = config.getBoolean("tombRemove", tombRemove);
        pMessage = config.getBoolean("playerMessage", pMessage);
        saveTombList = config.getBoolean("saveTombList", saveTombList);
        destroyQuickLoot = config.getBoolean("destroyQuickLoot", destroyQuickLoot);
        noDestroy = config.getBoolean("noDestroy", noDestroy);
        noInterfere = config.getBoolean("noInterfere", noInterfere);
        logEvents = config.getBoolean("logEvents", logEvents);
        skipBuildCheck = config.getBoolean("skipBuildCheck", skipBuildCheck);
    }
    
    public void defaultConfig() {
        config.setProperty("lwcEnable", lwcEnable);
        config.setProperty("lwcTimeout", lwcTime);
        config.setProperty("lwcRemove", lwcRemove);
        config.setProperty("lwcPublic", lwcPublic);
        config.setProperty("tombSign", tombSign);
        config.setProperty("removeTime", removeTime);
        config.setProperty("tombRemove", tombRemove);
        config.setProperty("playerMessage", pMessage);
        config.setProperty("saveTombList", saveTombList);
        config.setProperty("destroyQuickLoot", destroyQuickLoot);
        config.setProperty("noDestroy", noDestroy);
        config.setProperty("noInterfere", noInterfere);
        config.setProperty("logEvents", logEvents);
        config.setProperty("skipBuildCheck", skipBuildCheck);
        config.save();
    }

    public void loadSignTemplate() {
        signTemplate[0] = "{name}";
        signTemplate[1] = "Killed By";
        signTemplate[2] = "{cause}";
        signTemplate[3] = "{time}";
        try {
            File fh = new File(this.getDataFolder(), "sign.tpl");
            // Create default
            if (!fh.exists()) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(fh));
                for (int i = 0; i < 4; i++) {
                    bw.append(signTemplate[i]);
                    bw.newLine();
                }
                bw.close();
            // Load sign template
            } else {
                Scanner scanner = new Scanner(fh);
                for (int i = 0; i < 4; i++) {
                    signTemplate[i] = scanner.nextLine().trim();
                }
                scanner.close();
            }
        } catch (Exception e) {}
    }
    
    public void loadTombList(String world) {
        if (!saveTombList) return;
        try {
            File fh = new File(this.getDataFolder(), "tombList-" + world + ".db");
            if (!fh.exists()) return;
            Scanner scanner = new Scanner(fh);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                String[] split = line.split(":");
                //block:lblock:sign:time:name:lwc
                Block block = readBlock(split[0]);
                Block lBlock = readBlock(split[1]);
                Block sign = readBlock(split[2]);
                String owner = split[3];
                long time = Long.valueOf(split[4]);
                boolean lwc = Boolean.valueOf(split[5]);
                if (block == null || owner == null) {
                    log.info("[Tombstone] Invalid tombstone in database " + fh.getName());
                    continue;
                }
                TombBlock tBlock = new TombBlock(block, lBlock, sign, owner, time, lwc);
                tombList.offer(tBlock);
                // Used for quick tombStone lookup
                tombBlockList.put(block.getLocation(), tBlock);
                if (lBlock != null) tombBlockList.put(lBlock.getLocation(), tBlock);
                if (sign != null) tombBlockList.put(sign.getLocation(), tBlock);
                ArrayList<TombBlock> pList = playerTombList.get(owner);
                if (pList == null) {
                    pList = new ArrayList<TombBlock>();
                    playerTombList.put(owner, pList);
                }
                pList.add(tBlock);
            }
            scanner.close();
        } catch (IOException e) {
            Tombstone.log.info("[Tombstone] Error loading tombstone list: " + e);
        }
    }
    
    public void saveTombList(String world) {
        if (!saveTombList) return;
        try {
            File fh = new File(this.getDataFolder(), "tombList-" + world + ".db");
            BufferedWriter bw = new BufferedWriter(new FileWriter(fh));
            for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
                TombBlock tBlock = iter.next();
                // Skip not this world
                if (!tBlock.getBlock().getWorld().getName().equalsIgnoreCase(world)) continue;
                
                bw.append(printBlock(tBlock.getBlock()));
                bw.append(":");
                bw.append(printBlock(tBlock.getLBlock()));
                bw.append(":");
                bw.append(printBlock(tBlock.getSign()));
                bw.append(":");
                bw.append(tBlock.getOwner());
                bw.append(":");
                bw.append(String.valueOf(tBlock.getTime()));
                bw.append(":");
                bw.append(String.valueOf(tBlock.getLwcEnabled()));
                bw.newLine();
            }
            bw.close();
        } catch (IOException e) {
            Tombstone.log.info("[Tombstone] Error saving tombstone list: " + e);
        }
    }
    
    private String printBlock(Block b) {
        if (b == null) return "";
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }
    
    private Block readBlock(String b) {
        if (b.length() == 0) return null;
        String[] split = b.split(",");
        //world,x,y,z
        World world = getServer().getWorld(split[0]);
        if (world == null) return null;
        return world.getBlockAt(Integer.valueOf(split[1]), Integer.valueOf(split[2]), Integer.valueOf(split[3]));
    }
    
    public void onDisable() {
        for (World w : getServer().getWorlds())
            saveTombList(w.getName());
    }
    
    /*
     * Check if a plugin is loaded/enabled already. Returns the plugin if so, null otherwise
     */
    private Plugin checkPlugin(String p) {
        Plugin plugin = pm.getPlugin(p);
        return checkPlugin(plugin);
    }
    
    private Plugin checkPlugin(Plugin plugin) {
        if (plugin != null && plugin.isEnabled()) {
            log.info("[Tombstone] Using " + plugin.getDescription().getName() + " (v" + plugin.getDescription().getVersion() + ")");
            return plugin;
        }
        return null;
    }
    
    private Boolean activateLWC(Player player, TombBlock tBlock) {
        if (!lwcEnable) return false;
        if (lwcPlugin == null) return false;
        LWC lwc = lwcPlugin.getLWC();
        
        // Register the chest + sign as private
        Block block = tBlock.getBlock();
        Block sign = tBlock.getSign();
        lwc.getPhysicalDatabase().registerProtection(block.getTypeId(), ProtectionTypes.PRIVATE, block.getWorld().getName(), player.getName(), "", block.getX(), block.getY(), block.getZ());
        if (sign != null)
            lwc.getPhysicalDatabase().registerProtection(sign.getTypeId(), ProtectionTypes.PRIVATE, block.getWorld().getName(), player.getName(), "", sign.getX(), sign.getY(), sign.getZ());
        
        tBlock.setLwcEnabled(true);
        return true;
    }
    
    private void deactivateLWC(TombBlock tBlock, boolean force) {
        if (!lwcEnable) return;
        if (lwcPlugin == null) return;
        LWC lwc = lwcPlugin.getLWC();
        
        // Remove the protection on the chest
        Block _block = tBlock.getBlock();
        Protection protection = lwc.findProtection(_block);
        if (protection != null) {
            protection.remove();
            //Set to public instead of removing completely
            if (lwcPublic && !force)
                lwc.getPhysicalDatabase().registerProtection(_block.getTypeId(), ProtectionTypes.PUBLIC, _block.getWorld().getName(), tBlock.getOwner(), "", _block.getX(), _block.getY(), _block.getZ());
        }
        
        // Remove the protection on the sign
        _block = tBlock.getSign();
        if (_block != null) {
            protection = lwc.findProtection(_block);
            if (protection != null) {
                protection.remove();
                // Set to public instead of removing completely
                if (lwcPublic && !force)
                    lwc.getPhysicalDatabase().registerProtection(_block.getTypeId(), ProtectionTypes.PUBLIC, _block.getWorld().getName(), tBlock.getOwner(), "", _block.getX(), _block.getY(), _block.getZ());
            }
        }
        tBlock.setLwcEnabled(false);
    }
    
    private void removeTomb(TombBlock tBlock, boolean removeList) {
        if (tBlock == null) return;
        
        tombBlockList.remove(tBlock.getBlock().getLocation());
        if (tBlock.getLBlock() != null) tombBlockList.remove(tBlock.getLBlock().getLocation());
        if (tBlock.getSign() != null) tombBlockList.remove(tBlock.getSign().getLocation());
        
        // Remove just this tomb from tombList
        ArrayList<TombBlock> tList = playerTombList.get(tBlock.getOwner());
        if (tList != null) {
        	tList.remove(tBlock);
        	if (tList.size() == 0) {
        		playerTombList.remove(tBlock.getOwner());
        	}
        }

        if (removeList)
            tombList.remove(tBlock);
        
        if (tBlock.getBlock() != null)
            saveTombList(tBlock.getBlock().getWorld().getName());
    }
    
    /*
     * Check whether the player has the given permissions.
     */
    public boolean hasPerm(Player player, String perm) {
        if (permissions != null) {
            return permissions.getHandler().has(player, perm);
        } else {
            return player.hasPermission(perm);
        }
    }
    
    public void sendMessage(Player p, String msg) {
        if (!pMessage) return;
        p.sendMessage("[Tombstone] " + msg);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player)sender;
        String cmd = command.getName();
        if (cmd.equalsIgnoreCase("tomblist")) {
            if (!hasPerm(p, "tombstone.cmd.tomblist")) {
                sendMessage(p, "Permission Denied");
                return true;
            }
            ArrayList<TombBlock> pList = playerTombList.get(p.getName());
            if (pList == null) {
                sendMessage(p, "You have no tombstones.");
                return true;
            }
            sendMessage(p, "Tombstone List:");
            int i = 0;
            for (TombBlock tomb : pList) {
                i++;
                if (tomb.getBlock() == null) continue;
                int X = tomb.getBlock().getX();
                int Y = tomb.getBlock().getY();
                int Z = tomb.getBlock().getZ();
                sendMessage(p, "  " + i + " - World: " + tomb.getBlock().getWorld().getName() + " @(" + X + "," + Y + "," + Z + ")");
            }
            return true;
        } else if (cmd.equalsIgnoreCase("tombfind")) {
            if (!hasPerm(p, "tombstone.cmd.tombfind")) {
                sendMessage(p, "Permission Denied");
                return true;
            }
            if (args.length != 1) return false;
            ArrayList<TombBlock> pList = playerTombList.get(p.getName());
            if (pList == null) {
                sendMessage(p, "You have no tombstones.");
                return true;
            }
            int slot = 0;
            try {
                slot = Integer.parseInt(args[0]);
            } catch (Exception e) {
                sendMessage(p, "Invalid Tombstone");
                return true;
            }
            slot -= 1;
            if (slot < 0 || slot >= pList.size()) {
                sendMessage(p, "Invalid Tombstone");
                return true;
            }
            TombBlock tBlock = pList.get(slot);
            double degrees = (getYawTo(tBlock.getBlock().getLocation(), p.getLocation()) + 270) % 360;
            p.setCompassTarget(tBlock.getBlock().getLocation());
            sendMessage(p, "Your compass is pointing at your tombstone");
            sendMessage(p, "Your tombstone #" + args[0] + " is to the " + getDirection(degrees));
            return true;
        } else if (cmd.equalsIgnoreCase("tombreset")) {
            if (!hasPerm(p, "tombstone.cmd.tombreset")) {
                sendMessage(p, "Permission Denied");
                return true;
            }
            p.setCompassTarget(p.getWorld().getSpawnLocation());
            return true;
        }
        return false;
    }

    /**
     * Gets the Yaw from one location to another in relation to North.
     */
    public double getYawTo(Location from, Location to) {
            final int distX = to.getBlockX() - from.getBlockX();
            final int distZ = to.getBlockZ() - from.getBlockZ();
            double degrees = Math.toDegrees(Math.atan2(-distX, distZ));
            degrees += 180;
        return degrees;
    }
    
    /**
     * Converts a rotation to a cardinal direction name.
     * Author: sk89q - Original function from CommandBook plugin
     * @param rot
     * @return
     */
    private static String getDirection(double rot) {
        if (0 <= rot && rot < 22.5) {
            return "North";
        } else if (22.5 <= rot && rot < 67.5) {
            return "Northeast";
        } else if (67.5 <= rot && rot < 112.5) {
            return "East";
        } else if (112.5 <= rot && rot < 157.5) {
            return "Southeast";
        } else if (157.5 <= rot && rot < 202.5) {
            return "South";
        } else if (202.5 <= rot && rot < 247.5) {
            return "Southwest";
        } else if (247.5 <= rot && rot < 292.5) {
            return "West";
        } else if (292.5 <= rot && rot < 337.5) {
            return "Northwest";
        } else if (337.5 <= rot && rot < 360.0) {
            return "North";
        } else {
            return null;
        }
    }
    
    /**
     * 
     * Print a message to terminal if logEvents is enabled
     * @param msg
     * @return
     * 
     */
    private void logEvent(String msg) {
        if (!logEvents) return;
        log.info("[Tombstone] " + msg);
    }
    
    private class bListener extends BlockListener {
        @Override
        public void onBlockBreak(BlockBreakEvent event) {
            Block b = event.getBlock();
            Player p = event.getPlayer();
            if (b.getType() != Material.CHEST && b.getType() != Material.SIGN_POST) return;

            TombBlock tBlock = tombBlockList.get(b.getLocation());
            if (tBlock == null) return;
            
            if (noDestroy && !hasPerm(p, "tombstone.admin")) {
                logEvent(p.getName() + " tried to destroy tombstone at " + b.getLocation());
                sendMessage(p, "Tombstone unable to be destroyed");
                event.setCancelled(true);
                return;
            }

            if (lwcPlugin != null && lwcEnable && tBlock.getLwcEnabled()) {
                if (tBlock.getOwner().equals(p.getName()) || hasPerm(p, "tombstone.admin")) {
                    logEvent("Deactivating LWC");
                    deactivateLWC(tBlock, true);
                } else {
                    event.setCancelled(true);
                    return;
                }
            }
            logEvent(p.getName() + " destroyed tombstone at " + b.getLocation());
            removeTomb(tBlock, true);
        }
    }
    
    private class pListener extends PlayerListener {
        @Override
        public void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Block b = event.getClickedBlock();
            if (b.getType() != Material.SIGN_POST && b.getType() != Material.CHEST) return;
            // We'll do quickloot on rightclick of chest if we're going to destroy it anyways
            if (b.getType() == Material.CHEST && (!destroyQuickLoot || !noDestroy)) return;
            if (!hasPerm(event.getPlayer(), "tombstone.quickloot")) return;
            
            TombBlock tBlock = tombBlockList.get(b.getLocation());
            if (tBlock == null || !(tBlock.getBlock().getState() instanceof Chest)) return;

            if (!tBlock.getOwner().equals(event.getPlayer().getName())) return;
            
            Chest sChest = (Chest)tBlock.getBlock().getState();
            Chest lChest = (tBlock.getLBlock() != null) ? (Chest)tBlock.getLBlock().getState() : null;
            
            ItemStack[] items = sChest.getInventory().getContents();
            boolean overflow = false;
            for (int cSlot = 0; cSlot < items.length; cSlot++) {
                ItemStack item = items[cSlot];
                if (item == null) continue;
                if (item.getType() == Material.AIR) continue;
                int slot = event.getPlayer().getInventory().firstEmpty();
                if (slot == -1) {
                    overflow = true;
                    break;
                }
                event.getPlayer().getInventory().setItem(slot, item);
                sChest.getInventory().clear(cSlot);
            }
            if (lChest != null) {
                items = lChest.getInventory().getContents();
                for (int cSlot = 0; cSlot < items.length; cSlot++) {                    
                    ItemStack item = items[cSlot];
                    if (item == null) continue;
                    if (item.getType() == Material.AIR) continue;
                    int slot = event.getPlayer().getInventory().firstEmpty();
                    if (slot == -1) {
                        overflow = true;
                        break;
                    }
                    event.getPlayer().getInventory().setItem(slot, item);
                    lChest.getInventory().clear(cSlot);
                }
            }
            
            if (!overflow) {
                // We're quicklooting, so no need to resume this interaction
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                
                // Deactivate LWC
                deactivateLWC(tBlock, true);
                removeTomb(tBlock, true);
                
                if (destroyQuickLoot) {
                    if (tBlock.getSign() != null) tBlock.getSign().setType(Material.AIR);
                    tBlock.getBlock().setType(Material.AIR);
                    if (tBlock.getLBlock() != null) tBlock.getLBlock().setType(Material.AIR);
                }
            }
            
            // Manually update inventory for the time being.
            event.getPlayer().updateInventory();
            sendMessage(event.getPlayer(), "Tombstone quicklooted!");
            logEvent(event.getPlayer() + " quicklooted tombstone at " + tBlock.getBlock().getLocation());
        }
    }
    
    private class eListener extends EntityListener {
        @Override
        public void onEntityDamage(EntityDamageEvent event) {
            if (event.isCancelled()) return;
            if (!(event.getEntity() instanceof Player))return;
            
            Player player = (Player)event.getEntity();
            // Add them to the list if they're about to die
            if (player.getHealth() - event.getDamage() <= 0) {
                deathCause.put(player.getName(), event);
            }
        }
        @Override
        public void onEntityDeath(EntityDeathEvent event ) {
            if (!(event.getEntity() instanceof Player)) return;
            Player p = (Player)event.getEntity();
            String name = p.getName();
            
            if (!hasPerm(p, "tombstone.use")) return;
            
            logEvent(name + " died.");
            
            if (event.getDrops().size() == 0) {
                sendMessage(p, "Inventory Empty.");
                logEvent(name + " inventory empty.");
                return;
            }
            
            // Check if this is a void death
            EntityDamageEvent dmg = deathCause.get(name);
            if (dmg != null && dmg.getCause() == DamageCause.VOID) {
                sendMessage(p, "Your items were lost in the void.");
                logEvent(name + " died in the void.");
                return;
            }
            
            // Get the current player location.
            Location loc = p.getLocation();
            Block block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            
            // Check if we're allowed to build here. Will stop things like a tomb in spawn
            if (!skipBuildCheck && !canBuild(block, p)) {
                sendMessage(p, "Your tombstone can't spawn here. Inventory dropped.");
                logEvent(name + " Building disallowed at death location.");
                return;
            }
            
            // If we run into something we don't want to destroy, go one up.
            if (    block.getType() == Material.STEP || 
                    block.getType() == Material.TORCH ||
                    block.getType() == Material.REDSTONE_WIRE || 
                    block.getType() == Material.RAILS || 
                    block.getType() == Material.STONE_PLATE || 
                    block.getType() == Material.WOOD_PLATE ||
                    block.getType() == Material.REDSTONE_TORCH_ON ||
                    block.getType() == Material.REDSTONE_TORCH_OFF ||
                    block.getType() == Material.CAKE_BLOCK) {
                block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
            }

            // Check if the player has a chest.
            int pChestCount = 0;
            int pSignCount = 0;
            for (ItemStack item : event.getDrops()) {
                if (item == null) continue;
                if (item.getType() == Material.CHEST) pChestCount += item.getAmount();
                if (item.getType() == Material.SIGN) pSignCount += item.getAmount();
            }
            
            if (pChestCount == 0 && !hasPerm(p, "tombstone.freechest")) {
                sendMessage(p, "No chest found in inventory. Inventory dropped");
                logEvent(name + " No chest in inventory.");
                return;
            }
            
            // Check if we can replace the block.
            block = findPlace(block);
            if ( block == null ) {
                sendMessage(p, "Could not find room for chest. Inventory dropped");
                logEvent(name + " Could not find room for chest.");
                return;
            }
            
            // Check if there is a nearby chest
            if (noInterfere && checkChest(block)) {
                sendMessage(p, "There is a chest interfering with your tombstone. Inventory dropped");
                logEvent(name + " Chest interfered with tombstone creation.");
                return;
            }
            
            int removeChestCount = 1;
            int removeSign = 0;
            
            // Do the check for a large chest block here so we can check for interference
            Block lBlock = findLarge(block);
            
            // Set the current block to a chest, init some variables for later use.
            block.setType(Material.CHEST);
            // We're running into issues with 1.3 where we can't cast to a Chest :(
            BlockState state = block.getState();
            if (!(state instanceof Chest)) {
                sendMessage(p, "Could not access chest. Inventory dropped.");
                logEvent(name + " Could not access chest.");
                return;
            }
            Chest sChest = (Chest)state;
            Chest lChest = null;
            int slot = 0;
            int maxSlot = sChest.getInventory().getSize();

            // Check if they need a large chest.
            if (event.getDrops().size() > maxSlot) {
                // If they are allowed spawn a large chest to catch their entire inventory.
                if (lBlock != null && hasPerm(p, "tombstone.large")) {
                    removeChestCount = 2;
                    // Check if the player has enough chests
                    if (pChestCount >= removeChestCount || hasPerm(p, "tombstone.freechest")) {
                        lBlock.setType(Material.CHEST);
                        lChest = (Chest)lBlock.getState();
                        maxSlot = maxSlot * 2;
                    } else {
                        removeChestCount = 1;
                    }
                }
            }
            
            // Don't remove any chests if they get a free one.
            if (hasPerm(p, "tombstone.freechest"))
                removeChestCount = 0;

            // Check if we have signs enabled, if the player can use signs, and if the player has a sign or gets a free sign
            Block sBlock = null;
            if (tombSign && hasPerm(p, "tombstone.sign") && 
                (pSignCount > 0 || hasPerm(p, "tombstone.freesign"))) {
                // Find a place to put the sign, then place the sign.
                sBlock = sChest.getWorld().getBlockAt(sChest.getX(), sChest.getY() + 1, sChest.getZ());
                if (canReplace(sBlock.getType())) {
                    createSign(sBlock, p);
                    removeSign = 1;
                } else if (lChest != null) {
                    sBlock = lChest.getWorld().getBlockAt(lChest.getX(), lChest.getY() + 1, lChest.getZ());
                    if (canReplace(sBlock.getType())) {
                        createSign(sBlock, p);
                        removeSign = 1;
                    }
                }
            }
            // Don't remove a sign if they get a free one
            if (hasPerm(p, "tombstone.freesign"))
                removeSign = 0;
            
            // Create a TombBlock for this tombstone
            TombBlock tBlock = new TombBlock(sChest.getBlock(), (lChest != null) ? lChest.getBlock() : null, sBlock, p.getName(), (System.currentTimeMillis() / 1000));
            
            // Protect the chest/sign if LWC is installed.
            Boolean prot = false;
            if (hasPerm(p, "tombstone.lwc"))
                prot = activateLWC(p, tBlock);
            tBlock.setLwcEnabled(prot);
            
            // Add tombstone to list
            tombList.offer(tBlock);
            
            // Add tombstone blocks to tombBlockList
            tombBlockList.put(tBlock.getBlock().getLocation(), tBlock);
            if (tBlock.getLBlock() != null) tombBlockList.put(tBlock.getLBlock().getLocation(), tBlock);
            if (tBlock.getSign() != null) tombBlockList.put(tBlock.getSign().getLocation(), tBlock);
            
            // Add tombstone to player lookup list
            ArrayList<TombBlock> pList = playerTombList.get(name);
            if (pList == null) {
                pList = new ArrayList<TombBlock>();
                playerTombList.put(name, pList);
            }
            pList.add(tBlock);
            
            saveTombList(p.getWorld().getName());
            
            // Next get the players inventory using the getDrops() method.
            for (Iterator<ItemStack> iter = event.getDrops().listIterator(); iter.hasNext();) {
                ItemStack item = iter.next();
                if (item == null) continue;
                // Take the chest(s)
                if (removeChestCount > 0 && item.getType() == Material.CHEST) {
                    if (item.getAmount() >= removeChestCount) {
                        item.setAmount(item.getAmount() - removeChestCount);
                        removeChestCount = 0;
                    } else {
                        removeChestCount -= item.getAmount();
                        item.setAmount(0);
                    }
                    if (item.getAmount() == 0) {
                        iter.remove();
                        continue;
                    }
                }
                
                // Take a sign
                if (removeSign > 0 && item.getType() == Material.SIGN){
                    item.setAmount(item.getAmount() - 1);
                    removeSign = 0;
                    if (item.getAmount() == 0) {
                        iter.remove();
                        continue;
                    }
                }
                
                // Add items to chest if not full.
                if (slot < maxSlot) {
                    if (slot >= sChest.getInventory().getSize()) {
                        if (lChest == null) continue;
                        lChest.getInventory().setItem(slot % sChest.getInventory().getSize(), item);
                    } else {
                        sChest.getInventory().setItem(slot, item);
                    }
                    iter.remove();
                    slot++;
                } else if (removeChestCount == 0) break;
            }
            
            // Tell the player how many items went into chest.
            String msg = "Inventory stored in chest. ";
            if (event.getDrops().size() > 0)
                msg += event.getDrops().size() + " items wouldn't fit in chest.";
            sendMessage(p, msg);
            logEvent(name + " " + msg);
            if (prot) {
                sendMessage(p, "Chest protected with LWC. " + lwcTime + "s before chest is unprotected.");
                logEvent(name + " Chest protected with LWC. " + lwcTime + "s before chest is unprotected.");
            }
            if (tombRemove) {
                sendMessage(p, "Chest will be automatically removed in " + removeTime + "s");
                logEvent(name + " Chest will be automatically removed in " + removeTime + "s");
            }
        }
        
        private void createSign(Block signBlock, Player p) {
            // Variables used in template
            HashMap<String, String> vars = new HashMap<String, String>();
            vars.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
            vars.put("time", new SimpleDateFormat("hh:mm a").format(new Date()));
            // Name and cause have to be checked for length.
            String name = p.getName();
            String cause = "Unknown";
            EntityDamageEvent dmg = deathCause.get(name);
            if (dmg != null) {
                deathCause.remove(name);
                cause = getCause(dmg);
            }
            if (name.length() > 15) name = name.substring(0, 15);
            if (cause.length() > 15) cause = cause.substring(0, 15);
            vars.put("name", name);
            vars.put("cause", cause);
            
            signBlock.setType(Material.SIGN_POST);
            final Sign sign = (Sign)signBlock.getState();
            for (int i = 0; i < 4; i++) {
                sign.setLine(i, parseVars(signTemplate[i], vars));
            }
            getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                public void run() {
                    sign.update();
                }
            });
        }
        
        private String getCause(EntityDamageEvent dmg) {
            switch (dmg.getCause()) {
                case ENTITY_ATTACK:
                {
                    EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
                    Entity e = event.getDamager();
                    if (e == null) {
                        return "a Dispenser";
                    } else if (e instanceof Player) {
                        return ((Player) e).getDisplayName();
                    } else if (e instanceof PigZombie) {
                        return "a Pig Zombie";
                    } else if (e instanceof Giant) {
                        return "a Giant";
                    } else if (e instanceof Zombie) {
                        return "a Zombie";
                    } else if (e instanceof Skeleton) {
                        return "a Skeleton";
                    } else if (e instanceof Spider) {
                        return "a Spider";
                    } else if (e instanceof Creeper) {
                        return "a Creeper";
                    } else if (e instanceof Ghast) {
                        return "a Ghast";
                    } else if (e instanceof Slime) {
                        return "a Slime";
                    } else if (e instanceof Wolf) {
                        return "a Wolf";
                    } else {
                        return "a Monster";
                    }
                }
                case CONTACT:
                    return "a Cactus";
                case SUFFOCATION:
                    return "Suffocation";
                case FALL:
                    return "a Fall";
                case FIRE:
                    return "a Fire";
                case FIRE_TICK:
                    return "Burning";
                case LAVA:
                    return "Lava";
                case DROWNING:
                    return "Drowning";
                case BLOCK_EXPLOSION:
                    return "an Explosion";
                case ENTITY_EXPLOSION:
                {
                    try {
                        EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
                        Entity e = event.getDamager();
                        if (e instanceof TNTPrimed) return "a TNT Explosion";
                        else if (e instanceof Fireball) return "a Ghast";
                        else return "a Creeper";
                    } catch (Exception e) {
                        return "an Explosion";
                    }
                }
                case VOID:
                    return "the Void";
                case LIGHTNING:
                    return "Lightning";
                default:
                    return "Unknown";
            }
        }
        
        private String parseVars(String format, HashMap<String, String> vars) {
            Pattern pattern = Pattern.compile("\\{(.*?)\\}");
            Matcher matcher = pattern.matcher(format);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String var = vars.get(matcher.group(1));
                if (var == null) var = "";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(var));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }
        
        boolean canBuild(Block b, Player p) {
            // Check spawn distance
            int spawnSize = p.getServer().getSpawnRadius();
            Location spawn = p.getWorld().getSpawnLocation();
            if (spawnSize > 0) {
                int distanceFromSpawn = (int) Math.max(Math.abs(p.getLocation().getBlockX() - spawn.getBlockX()), Math.abs(p.getLocation().getBlockZ() - spawn.getBlockZ()));
                if (distanceFromSpawn <= spawnSize) return false;
            }
            // Check if another plugin stops us from building here
            BlockPlaceEvent event = new BlockPlaceEvent(b, b.getState(), b.getRelative(BlockFace.DOWN), p.getItemInHand(), p, true);
            pm.callEvent(event);
            if (event.isCancelled()) return false;
            return true;
        }
        
        /**
         * Find a block near the base block to place the tombstone
         * @param base
         * @return
         */
        Block findPlace(Block base) {
            if (canReplace(base.getType())) return base;
            int x = base.getX();
            int y = base.getY();
            int z = base.getZ();
            World w = base.getWorld();
            
            for (int i = x - 1; i < x + 1; i++) {
                for (int j = z - 1; j < z + 1; j++) {
                    Block b = w.getBlockAt(i, y, j);
                    if (canReplace(b.getType())) return b;
                }
            }
            
            return null;
        }
        
        Block findLarge(Block base) {
            // Check all 4 sides for air.
            Block exp;
            exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
            if (canReplace(exp.getType()) && (!noInterfere || !checkChest(exp))) return exp;
            exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
            if (canReplace(exp.getType()) && (!noInterfere || !checkChest(exp))) return exp;
            exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
            if (canReplace(exp.getType()) && (!noInterfere || !checkChest(exp))) return exp;
            exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
            if (canReplace(exp.getType()) && (!noInterfere || !checkChest(exp))) return exp;
            return null;
        }
        
        boolean checkChest(Block base) {
            // Check all 4 sides for a chest.
            Block exp;
            exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
            if (exp.getType() == Material.CHEST) return true;
            exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
            if (exp.getType() == Material.CHEST) return true;
            exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
            if (exp.getType() == Material.CHEST) return true;
            exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
            if (exp.getType() == Material.CHEST) return true;
            return false;
        }
        
        Boolean canReplace(Material mat) {
            return (mat == Material.AIR || 
                    mat == Material.SAPLING || 
                    mat == Material.WATER || 
                    mat == Material.STATIONARY_WATER || 
                    mat == Material.LAVA || 
                    mat == Material.STATIONARY_LAVA || 
                    mat == Material.YELLOW_FLOWER || 
                    mat == Material.RED_ROSE || 
                    mat == Material.BROWN_MUSHROOM || 
                    mat == Material.RED_MUSHROOM || 
                    mat == Material.FIRE || 
                    mat == Material.CROPS || 
                    mat == Material.SNOW || 
                    mat == Material.SUGAR_CANE);
        }
    }
    
    private class sListener extends ServerListener {
        @Override
        public void onPluginEnable(PluginEnableEvent event) {
            if (lwcPlugin == null) {
                if (event.getPlugin().getDescription().getName().equalsIgnoreCase("LWC")) {
                    lwcPlugin = (LWCPlugin)checkPlugin(event.getPlugin());
                }
            }
            if (permissions == null) {
                if (event.getPlugin().getDescription().getName().equalsIgnoreCase("Permissions")) {
                    permissions = (Permissions)checkPlugin(event.getPlugin());
                }
            }
        }
        
        @Override
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin() == lwcPlugin) {
                log.info("[Tombstone] LWC plugin lost.");
                lwcPlugin = null;
            }
            if (event.getPlugin() == permissions) {
                log.info("[Tombstone] Permissions plugin lost.");
                permissions = null;
            }
        }
    }
    
    private class TombThread extends Thread {
        public void run() {
            long cTime = System.currentTimeMillis() / 1000;
            for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
                TombBlock tBlock = iter.next();
                
                if (lwcRemove && tBlock.getLwcEnabled() && lwcPlugin != null) {
                    if (cTime > (tBlock.getTime() + lwcTime)) {
                        // Remove the protection on the block
                        deactivateLWC(tBlock, false);
                        tBlock.setLwcEnabled(false);
                        Player p = getServer().getPlayer(tBlock.getOwner());
                        if (p != null)
                            sendMessage(p, "LWC Protection disabled on your tombstone!");
                    }
                }
                
                // Remove block, drop items on ground (One last free-for-all)
                if (tombRemove && cTime > (tBlock.getTime() + removeTime)) {
                    tBlock.getBlock().getChunk().load();
                    if (tBlock.getLwcEnabled()) {
                        deactivateLWC(tBlock, true);
                    }
                    if (tBlock.getSign() != null)
                        tBlock.getSign().setType(Material.AIR);
                    tBlock.getBlock().setType(Material.AIR);
                    if (tBlock.getLBlock() != null)
                        tBlock.getLBlock().setType(Material.AIR);
                    
                    // Remove from tombList
                    iter.remove();
                    removeTomb(tBlock, false);

                    Player p = getServer().getPlayer(tBlock.getOwner());
                    if (p != null)
                        sendMessage(p, "Your tombstone has been destroyed!");
                    Block b = tBlock.getBlock();
                    logEvent("[Tombstone] Removed tombstone @ (" + b.getX() + ", " + b.getY() + ", " + b.getZ() + ")");
                }
            }
        }
    }
    
    private class TombBlock {
        private Block block;
        private Block lBlock;
        private Block sign;
        private long time;
        private String owner;
        private boolean lwcEnabled = false;
        TombBlock(Block block, Block lBlock, Block sign, String owner, long time) {
            this.block = block;
            this.lBlock = lBlock;
            this.sign = sign;
            this.owner = owner;
            this.time = time;
        }
        TombBlock(Block block, Block lBlock, Block sign, String owner, long time, boolean lwc) {
            this.block = block;
            this.lBlock = lBlock;
            this.sign = sign;
            this.owner = owner;
            this.time = time;
            this.lwcEnabled = lwc;
        }
        long getTime() {
            return time;
        }
        Block getBlock() {
            return block;
        }
        Block getLBlock() {
            return lBlock;
        }
        Block getSign() {
            return sign;
        }
        String getOwner() {
            return owner;
        }
        boolean getLwcEnabled() {
            return lwcEnabled;
        }
        void setLwcEnabled(boolean val) {
            lwcEnabled = val;
        }
    }
}
