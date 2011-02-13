package net.TheDgtl.Tombstone;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.griefcraft.model.ProtectionTypes;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class Tombstone extends JavaPlugin {
	public static Logger log;
	private final eListener entityListener = new eListener();
	private PermissionHandler Permissions = null;
	private Plugin lwcPlugin = null;
	private LinkedList<TombBlock> lwcQueue = new LinkedList<TombBlock>();
	private Boolean lwcEnable = true;
	private int lwcTime = 3600;
	private Boolean lwcRemove = false;
	private Boolean tombSign = true;
	private TombThread tombThread = null;
	private Configuration config;
	
	public Tombstone(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
    	super(pluginLoader, instance, desc, folder, plugin, cLoader);
    	log = Logger.getLogger("Minecraft");
    	config = this.getConfiguration();
	}
	
	public void onEnable() {
		PluginDescriptionFile pdfFile = getDescription();
        log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Priority.Normal, this);
        setupPermissions();
        reloadConfig();
        checkLWC();
	}
	
	public void reloadConfig() {
    	config.load();
    	lwcEnable = config.getBoolean("lwcEnable", lwcEnable);
        lwcTime = config.getInt("lwcTimeout", lwcTime);
        lwcRemove = config.getBoolean("lwcRemove", lwcRemove);
        tombSign = config.getBoolean("tombSign", tombSign);
        saveConfig();
    }
	
	public void saveConfig() {
		config.setProperty("lwcEnable", lwcEnable);
		config.setProperty("lwcTimeout", lwcTime);
        config.setProperty("lwcRemove", lwcRemove);
        config.setProperty("tombSign", tombSign);
        config.save();
	}
	
	public void onDisable() {
		// Stop the tomb thread
		if (tombThread != null && tombThread.isAlive()) {
			tombThread.interrupt();
			try {tombThread.join();} catch (InterruptedException e) {}
		}
		
		// Clear the lwcQueue/remove protection on exit
		checkLWC();
		if (lwcPlugin != null) {
			LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
			synchronized(lwcQueue) {
				while (!lwcQueue.isEmpty()) {
					TombBlock block = lwcQueue.removeFirst();
					// Remove the protection on the block, and remove block from queue.
					Block _block = block.getBlock();
					Protection protection = lwc.getPhysicalDatabase().loadProtectedEntity(_block.getX(), _block.getY(), _block.getZ());
					if (protection != null) {
						lwc.getPhysicalDatabase().unregisterProtectedEntity(protection.getX(), protection.getY(), protection.getZ());
						lwc.getPhysicalDatabase().unregisterProtectionRights(protection.getId());
					}
				}
			}
		}
	}
	
	private void checkLWC() {
		if (!lwcEnable) return;
		if (lwcPlugin != null) return;
		lwcPlugin = getServer().getPluginManager().getPlugin("LWC");
		if (lwcPlugin != null && lwcTime > 0) {
			log.info("[Tombstone] LWC Found, enabling chest protection. Enabling LWC disable thread.");
			tombThread = new TombThread();
			tombThread.start();
		}
	}
	
	private Boolean activateLWC(Player player, Block block, Block lBlock, Block sign) {
		checkLWC();
		if (lwcPlugin == null) return false;
		LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
		// Register the chest as private
		lwc.getPhysicalDatabase().registerProtectedEntity(block.getTypeId(), ProtectionTypes.PRIVATE, player.getName(), "", block.getX(), block.getY(), block.getZ()); 
		// Add this block to the lwcQueue to remove protection later.
		synchronized(lwcQueue) {
			lwcQueue.addLast(new TombBlock(block, lBlock, sign, System.currentTimeMillis()));
			lwcQueue.notifyAll();
		}
		return true;
	}
	
    private void setupPermissions() {
    	Plugin perm = getServer().getPluginManager().getPlugin("Permissions");

    	if(Permissions == null) {
    	    if(perm != null) {
    	    	Permissions = ((Permissions)perm).getHandler();
    	    } else {
    	    	log.info("[" + getDescription().getName() + "] Permission system not enabled. Enabling basic usage.");
    	    }
    	}
    }
    
    public Boolean hasPerm(Player player, String perm, Boolean def) {
    	if (Permissions != null)
    		return Permissions.has(player, perm);
    	return def;
    }
	
	private class eListener extends EntityListener {
        
        @Override
        public void onEntityDeath(EntityDeathEvent event ) {
        	if (!(event.getEntity() instanceof Player)) return;
        	Player p = (Player)event.getEntity();
        	if (!hasPerm(p, "tombstone.use", true)) return;
        	if (event.getDrops().size() == 0) {
        		p.sendMessage("[Tombstone] Inventory Empty.");
        		return;
        	}
        	
        	// Get the current player location.
        	Location loc = p.getLocation();
        	Block block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        	
        	// If we run into something we don't want to destroy, go one up.
        	if (	block.getType() == Material.STEP || 
        			block.getType() == Material.TORCH ||
        			block.getType() == Material.REDSTONE_WIRE || 
        			block.getType() == Material.RAILS || 
        			block.getType() == Material.STONE_PLATE || 
        			block.getType() == Material.WOOD_PLATE ||
        			block.getType() == Material.REDSTONE_TORCH_ON ||
        			block.getType() == Material.REDSTONE_TORCH_OFF ||
        			block.getType() == Material.CAKE_BLOCK) 
        		block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()); 

			// Check if the player has a chest.
			if (!p.getInventory().contains(Material.CHEST) && !hasPerm(p, "tombstone.freechest", false)) {
				p.sendMessage("[Tombstone] No chest found in inventory. Inventory dropped");
				return;
			}
        	
        	// Check if we can replace the block.
			if ( !canReplace(block.getType()) ) {
				p.sendMessage("[Tombstone] Could not find room for chest. Inventory dropped");
				return;
			}
        	
			int removeChestCount = 1;
			int removeSign = 0;
			
			// Set the current block to a chest, init some variables for later use.
			block.setType(Material.CHEST);
			Chest sChest = (Chest)block.getState();
			Chest lChest = null;
			int slot = 0;
			int maxSlot = sChest.getInventory().getSize();
			
			// If they are allowed, spawn a large chest to catch their entire inventory.
			if (hasPerm(p, "tombstone.large", false) && !hasPerm(p, "tombstone.freechest", false)) {
				removeChestCount = 2;
				// Check if the player has two chests. No easy way to do it.
				int chestCount = 0;
				for (ItemStack i : p.getInventory().getContents()) {
					if (i.getType() == Material.CHEST) chestCount += i.getAmount();
					if (chestCount >= removeChestCount) break;
				}
				if (chestCount >= removeChestCount) {
					Block lBlock = findLarge(block);
					if (lBlock != null) {
						lBlock.setType(Material.CHEST);
						lChest = (Chest)lBlock.getState();
						maxSlot = maxSlot * 2;
					} else {
						removeChestCount = 1;
					}
				} else {
					removeChestCount = 1;
				}
			}
			
			// tombstone.freechest + tombstone.large then just place a large chest.
			if (hasPerm(p, "tombstone.freechest", false)) {
				removeChestCount = 0;
				if (hasPerm(p, "tombstone.large", false)) {
					Block lBlock = findLarge(block);
					if (lBlock != null) {
						lBlock.setType(Material.CHEST);
						lChest = (Chest)lBlock.getState();
						maxSlot = maxSlot * 2;
					}
				}
			}

			// Check if we have signs enabled, if the player can use signs, and if the player has a sign or gets a free sign
			Block sBlock = null;
			if (tombSign && hasPerm(p, "tombstone.sign", true) && 
					(p.getInventory().contains(Material.SIGN) || hasPerm(p, "tombstone.freesign", false)) ) {
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
			if (hasPerm(p, "tombstone.freesign", false)) {
				removeSign = 0;
			}
			
			// Protect the chest if LWC is installed.
			Boolean prot = false;
			if (hasPerm(p, "tombstone.lwc", true))
				prot = activateLWC(p, sChest.getBlock(), (lChest != null ? lChest.getBlock() : null), sBlock);
			
			// First get players armor, tends to be more important.
			if (p.getInventory().getArmorContents().length > 0) {
				for (ItemStack i : p.getInventory().getArmorContents()) {
					for (ListIterator<ItemStack> iter = event.getDrops().listIterator(event.getDrops().size()); iter.hasPrevious();) {
						ItemStack j = iter.previous();
						if (j.equals(i)) {
							sChest.getInventory().setItem(slot, j);
							iter.remove();
							slot++;
							break;
						}
					}
				}
			}
			
			// Next get the players inventory using the getDrops() method.
			for (Iterator<ItemStack> iter = event.getDrops().listIterator(); iter.hasNext();) {
				ItemStack i = iter.next();
				
				// Take the chest
				if (removeChestCount > 0 && i.getType() == Material.CHEST) {
					if (i.getAmount() >= removeChestCount) {
						i.setAmount(i.getAmount() - removeChestCount);
						removeChestCount = 0;
					} else {
						removeChestCount -= i.getAmount();
						i.setAmount(0);
					}
					if (i.getAmount() == 0) {
						iter.remove();
						continue;
					}
				}
				
				// Take a sign
				if (removeSign > 0 && i.getType() == Material.SIGN){
					i.setAmount(i.getAmount() - 1);
					removeSign = 0;
					if (i.getAmount() == 0) { 
						iter.remove();
						continue;
					}
				}
				
				// Add items to chest if not full.
				if (slot < maxSlot) {
					if (slot >= sChest.getInventory().getSize()) {
						if (lChest == null) break;
						lChest.getInventory().setItem(slot % lChest.getInventory().getSize(), i);
					} else {
						sChest.getInventory().setItem(slot, i);
					}
					iter.remove();
					slot++;
				} else if (removeChestCount == 0) break;
			}
			
			// Tell the player how many items went into chest.
			String msg = "Inventory stored in chest.";
			if (event.getDrops().size() > 0)
				msg +=  event.getDrops().size() + " items wouldn't fit in chest.";
			p.sendMessage("[Tombstone] " + msg);
			if (prot)
				p.sendMessage("[Tombstone] Chest protected with LWC. " + lwcTime + "s before chest is " + ((lwcRemove) ? "removed" : "unprotected"));
        }
        
        private void createSign(Block signBlock, Player p) {
        	signBlock.setType(Material.SIGN_POST);
        	Sign sign = (Sign)signBlock.getState();
        	sign.setLine(0, p.getName());
        	sign.setLine(1, "RIP");
        	String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        	String time = new SimpleDateFormat("hh:mm a").format(new Date());
        	sign.setLine(2, date);
        	sign.setLine(3, time);
        	sign.update();
        	
        }
        
        Block findLarge(Block base) {
        	// Check all 4 sides for air.
        	Block exp;
        	exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
        	if (canReplace(exp.getType())) return exp;
        	return null;
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
	
	private class TombThread extends Thread {
		public void run() {
			checkLWC();
			if (lwcPlugin == null) {
				Tombstone.log.info("[Tombstone] LWC not running, stopping LWC disable thread");
				return;
			}
			LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
			
			while (!isInterrupted()) {
				synchronized(lwcQueue) {
					// Wait for a block
					while (lwcQueue.isEmpty()) {
						try {lwcQueue.wait();}
						catch (InterruptedException e){
							Tombstone.log.info("[Tombstone] Stopping LWC disable thread");
							return;
						}
					}
					// Check if the block should be removed
					TombBlock block = lwcQueue.getFirst();
					long time = System.currentTimeMillis() / 1000;
					if (time > ((block.getTime() / 1000) + lwcTime)) {
						// Remove the protection on the block, and remove block from queue.
						Block _block = block.getBlock();
						Protection protection = lwc.getPhysicalDatabase().loadProtectedEntity(_block.getX(), _block.getY(), _block.getZ());
						if (protection != null) {
							lwc.getPhysicalDatabase().unregisterProtectedEntity(protection.getX(), protection.getY(), protection.getZ());
							lwc.getPhysicalDatabase().unregisterProtectionRights(protection.getId());
							// Remove the chest if configured to.
							if (lwcRemove) {
								if (block.getSign() != null)
									block.getSign().setType(Material.AIR);
								_block.setType(Material.AIR);
								if (block.getLBlock() != null)
									block.getLBlock().setType(Material.AIR);
							}
						}
						lwcQueue.removeFirst();
					}
				}
				// Sleep another minute before checking for another chest to remove
				try {Thread.sleep(60 * 1000);}
				catch (InterruptedException e) {
					Tombstone.log.info("[Tombstone] Stopping LWC disable thread");
					return;
				}
			}
		}
	}
	
	private class TombBlock {
		private Block block;
		private Block lBlock = null;
		private Block sign = null;
		private long time;
		TombBlock(Block block, Block lBlock, Block sign, long time) {
			this.block = block;
			this.lBlock = lBlock;
			this.sign = sign;
			this.time = time;
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
	}
}
