package net.TheDgtl.Tombstone;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockRightClickEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
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
	public static Logger log;
	private Permissions Permissions = null;
	private Plugin lwcPlugin = null;
	private ConcurrentLinkedQueue<TombBlock> tombList = new ConcurrentLinkedQueue<TombBlock>();
	private Configuration config;
	
	/**
	 * Configuration options - Defaults
	 */
	private int lwcTime = 3600;
	private int removeTime = 18000;
	private boolean lwcEnable = true;
	private boolean lwcRemove = false;
	private boolean tombRemove = false;
	private Boolean tombSign = true;
	
	public void onEnable() {
		PluginDescriptionFile pdfFile = getDescription();
    	log = Logger.getLogger("Minecraft");
    	config = this.getConfiguration();
    	
        log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Normal, this);
        pm.registerEvent(Event.Type.BLOCK_RIGHTCLICKED, blockListener, Priority.Normal, this);
        
        setupPermissions();
        reloadConfig();
        checkLWC();
        // Start removal timer. Run every 30 seconds (20 ticks per second)
        if (lwcRemove || tombRemove)
        	getServer().getScheduler().scheduleSyncRepeatingTask(this, new TombThread(), 0L, 600L);
	}
	
	public void reloadConfig() {
    	config.load();
    	lwcEnable = config.getBoolean("lwcEnable", lwcEnable);
        lwcTime = config.getInt("lwcTimeout", lwcTime);
        lwcRemove = config.getBoolean("lwcRemove", lwcRemove);
        tombSign = config.getBoolean("tombSign", tombSign);
        removeTime = config.getInt("removeTime", removeTime);
        tombRemove = config.getBoolean("tombRemove", tombRemove);
        saveConfig();
    }
	
	public void saveConfig() {
		config.setProperty("lwcEnable", lwcEnable);
		config.setProperty("lwcTimeout", lwcTime);
        config.setProperty("lwcRemove", lwcRemove);
        config.setProperty("tombSign", tombSign);
        config.setProperty("removeTime", removeTime);
        config.setProperty("tombRemove", tombRemove);
        config.save();
	}
	
	public void onDisable() {
		// Clear the LWC protection from any tombstones.
		if (lwcPlugin != null) {
			while (!tombList.isEmpty()) {
				TombBlock block = tombList.poll();
				if (block == null) break;
				
				// Remove the protection on the block.
				if (!block.getLwcEnabled()) continue;
				deactivateLWC(block);
			}
		}
	}
	
	private void checkLWC() {
		if (!lwcEnable) return;
		if (lwcPlugin != null) return;
		lwcPlugin = getServer().getPluginManager().getPlugin("LWC");
		if (lwcPlugin != null) {
			log.info("[Tombstone] LWC Found, enabling chest protection.");
		}
	}
	
	private Boolean activateLWC(Player player, TombBlock tBlock) {
		if (lwcPlugin == null) return false;
		LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
		
		// Register the chest + sign as private
		Block block = tBlock.getBlock();
		Block sign = tBlock.getSign();
		lwc.getPhysicalDatabase().registerProtectedEntity(block.getTypeId(), ProtectionTypes.PRIVATE, player.getName(), "", block.getX(), block.getY(), block.getZ());
		if (sign != null)
			lwc.getPhysicalDatabase().registerProtectedEntity(sign.getTypeId(), ProtectionTypes.PRIVATE, player.getName(), "", sign.getX(), sign.getY(), sign.getZ());
		return true;
	}
	
	private void deactivateLWC(TombBlock tBlock) {
		if (lwcPlugin == null) return;
		LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
		
		// Remove the protection on the chest
		Block _block = tBlock.getBlock();
		Protection protection = lwc.getPhysicalDatabase().loadProtectedEntity(_block.getX(), _block.getY(), _block.getZ());
		if (protection != null) {
			lwc.getPhysicalDatabase().unregisterProtectedEntity(protection.getX(), protection.getY(), protection.getZ());
			lwc.getPhysicalDatabase().unregisterProtectionRights(protection.getId());
		}
		
		// Remove the protection on the sign
		_block = tBlock.getSign();
		if (_block != null) {
			protection = lwc.getPhysicalDatabase().loadProtectedEntity(_block.getX(), _block.getY(), _block.getZ());
			if (protection != null) {
				lwc.getPhysicalDatabase().unregisterProtectedEntity(protection.getX(), protection.getY(), protection.getZ());
				lwc.getPhysicalDatabase().unregisterProtectionRights(protection.getId());
			}
		}
	}
	
    private void setupPermissions() {
    	Plugin perm = getServer().getPluginManager().getPlugin("Permissions");

	    if(perm != null) {
	    	Permissions = (Permissions)perm;
	    } else {
	    	log.info("[" + getDescription().getName() + "] Permission system not enabled. Enabling basic usage.");
	    }
    }
    
    @SuppressWarnings("static-access")
	public Boolean hasPerm(Player player, String perm, Boolean def) {
    	if (Permissions != null)
    		return Permissions.Security.has(player, perm);
    	return def;
    }
    
    private class bListener extends BlockListener {
    	@Override
    	public void onBlockBreak(BlockBreakEvent event) {
    		Block b = event.getBlock();
    		if (b.getType() != Material.CHEST && b.getType() != Material.SIGN_POST) return;
    		
    		// Loop through tombstones looking to see if this is part of one.
			for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
				TombBlock tBlock = iter.next();
				if (tBlock.getBlock() != null && b.getLocation().equals(tBlock.getBlock().getLocation())) {
					if (tBlock.getLwcEnabled())
						deactivateLWC(tBlock);
					iter.remove();
					break;
				}
				if (tBlock.getLBlock() != null && b.getLocation().equals(tBlock.getLBlock().getLocation())) {
					if (tBlock.getLwcEnabled())
						deactivateLWC(tBlock);
					iter.remove();
					break;
				}
				if (tBlock.getSign() != null && b.getLocation().equals(tBlock.getSign().getLocation())) {
					if (tBlock.getLwcEnabled())
						deactivateLWC(tBlock);
					iter.remove();
					break;
				}
			}
    	}
    	
    	@Override
    	public void onBlockRightClick(BlockRightClickEvent event) {
    		Block b = event.getBlock();
    		if (b.getType() != Material.SIGN_POST) return;
    		if (!hasPerm(event.getPlayer(), "tombstone.quickloot", true)) return;
    		
    		// Loop through tombstones looking to see if this is part of one.
			for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
				TombBlock tBlock = iter.next();
				// Check owner
				if (!tBlock.getOwner().equals(event.getPlayer())) continue;
				// Check location
				if (b.getLocation().equals(tBlock.getSign().getLocation())) {
					Chest sChest = (Chest)tBlock.getBlock().getState();
					Chest lChest = (tBlock.getLBlock() != null) ? (Chest)tBlock.getLBlock().getState() : null;
					
					ItemStack[] items = sChest.getInventory().getContents();
					for (int cSlot = 0; cSlot < items.length; cSlot++) {
						ItemStack item = items[cSlot];
						if (item.getType() == Material.AIR) continue;
						int slot = event.getPlayer().getInventory().firstEmpty();
						if (slot == -1) break;
						event.getPlayer().getInventory().setItem(slot, item);
						sChest.getInventory().clear(cSlot);
					}
					if (lChest != null) {
						items = lChest.getInventory().getContents();
						for (int cSlot = 0; cSlot < items.length; cSlot++) {
							ItemStack item = items[cSlot];
							if (item.getType() == Material.AIR) continue;
							int slot = event.getPlayer().getInventory().firstEmpty();
							if (slot == -1) break;
							event.getPlayer().getInventory().setItem(slot, item);
							lChest.getInventory().clear(cSlot);
						}
					}
					deactivateLWC(tBlock);
					iter.remove();
					// Manually update inventory for the time being.
					event.getPlayer().updateInventory();
					event.getPlayer().sendMessage("[Tombstone] Tombstone quicklooted!");
					break;
				}
			}
    	}
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

			// Check if they need a large chest.
			if (event.getDrops().size() > maxSlot) {
				// If they are allowed spawn a large chest to catch their entire inventory.
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
				if (hasPerm(p, "tombstone.freechest", false) && hasPerm(p, "tombstone.large", false)) {
					Block lBlock = findLarge(block);
					if (lBlock != null) {
						lBlock.setType(Material.CHEST);
						lChest = (Chest)lBlock.getState();
						maxSlot = maxSlot * 2;
					}
				}
			}
			// Don't remove any chests if they get a free one.
			if (hasPerm(p, "tombstone.freechest", false))
				removeChestCount = 0;

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
			// Don't remove a sign if they get a free one
			if (hasPerm(p, "tombstone.freesign", false))
				removeSign = 0;
			
			// Create a TombBlock for this tombstone
			TombBlock tBlock = new TombBlock(sChest.getBlock(), (lChest != null) ? lChest.getBlock() : null, sBlock, p, (System.currentTimeMillis() / 1000));
			
			// Protect the chest/sign if LWC is installed.
			Boolean prot = false;
			if (hasPerm(p, "tombstone.lwc", true))
				prot = activateLWC(p, tBlock);
			tBlock.setLwcEnabled(prot);
			
			// Add tombstone to list
			tombList.offer(tBlock);
			
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
				
				// Take the chest(s)
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
						if (lChest == null) continue;
						lChest.getInventory().setItem(slot % sChest.getInventory().getSize(), i);
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
				p.sendMessage("[Tombstone] Chest protected with LWC. " + lwcTime + "s before chest is unprotected.");
			if (tombRemove)
				p.sendMessage("[Tombstone] Chest will be automatically removed in " + removeTime + "s");
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
        	// Manual sign update since update() doesn't work properly
        	((CraftWorld)signBlock.getWorld()).getHandle().g(signBlock.getX(), signBlock.getY(), signBlock.getZ());
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
			long cTime = System.currentTimeMillis() / 1000;
			for (Iterator<TombBlock> iter = tombList.iterator(); iter.hasNext();) {
				TombBlock tBlock = iter.next();
				
				if (lwcRemove && tBlock.getLwcEnabled() && lwcPlugin != null) {
					if (cTime > (tBlock.getTime() + lwcTime)) {
						// Remove the protection on the block
						deactivateLWC(tBlock);
						tBlock.setLwcEnabled(false);
						tBlock.getOwner().sendMessage("[Tombstone] LWC Protection disabled on your tombstone!");
					}
				}
				
				// Remove block, drop items on ground (One last free-for-all)
				if (tombRemove && cTime > (tBlock.getTime() + removeTime)) {
					tBlock.getBlock().getWorld().loadChunk(tBlock.getBlock().getChunk());
					tBlock.getBlock().setType(Material.AIR);
					if (tBlock.getLBlock() != null)
						tBlock.getLBlock().setType(Material.AIR);
					iter.remove();
					tBlock.getOwner().sendMessage("[Tombstone] Your tombstone has been destroyed!");
				}
			}
		}
	}
	
	private class TombBlock {
		private Block block;
		private Block lBlock;
		private Block sign;
		private long time;
		private Player owner;
		private boolean lwcEnabled = false;
		TombBlock(Block block, Block lBlock, Block sign, Player owner, long time) {
			this.block = block;
			this.lBlock = lBlock;
			this.sign = sign;
			this.owner = owner;
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
		Player getOwner() {
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
