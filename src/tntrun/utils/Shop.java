/**
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package tntrun.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import tntrun.FormattingCodesParser;
import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.messages.Messages;

public class Shop implements Listener{

	private TNTRun plugin;
	private String invname;
	private int invsize;

	public Shop(TNTRun plugin){
		this.plugin = plugin;
		ShopFiles shopFiles = new ShopFiles(plugin);
		shopFiles.setShopItems();

		invsize = plugin.getConfig().getInt("shop.size");
		invname = FormattingCodesParser.parseFormattingCodes(plugin.getConfig().getString("shop.name"));
	}  

	private HashMap<Integer, Integer> itemSlot = new HashMap<Integer, Integer>();
	private HashMap<Player, ArrayList<ItemStack>> pitems = new HashMap<Player, ArrayList<ItemStack>>();
	private List<Player> bought = new ArrayList<Player>();
	private HashMap<Player, List<PotionEffect>> potionMap = new HashMap<Player, List<PotionEffect>>();

	private void giveItem(int slot, Player player, String title) {
		int kit = itemSlot.get(slot);		
		ArrayList<ItemStack> item = new ArrayList<ItemStack>();
		FileConfiguration cfg = ShopFiles.getShopConfiguration();
		List<PotionEffect> pelist = new ArrayList<PotionEffect>();

		for(String items : cfg.getConfigurationSection(kit + ".items").getKeys(false)) {
			try {				
				Material material = Material.getMaterial(cfg.getString(kit + ".items." + items + ".material"));
				int amount = Integer.valueOf(cfg.getInt(kit + ".items." + items + ".amount"));

				List<String> enchantments = cfg.getStringList(kit + ".items." + items + ".enchantments");

				if(!bought.contains(player)) {
					bought.add(player);
				}
				// if the item is a potion, store the potion effect and skip to next item
				if (material.toString().equalsIgnoreCase("POTION")) {
					if (enchantments != null && !enchantments.isEmpty()) {
						for (String peffects : enchantments) {
							PotionEffect effect = createPotionEffect(peffects);
							if (effect != null) {
								pelist.add(effect);
							}
						}
					}
					player.updateInventory();
					player.closeInventory();
					continue;
				}

				String displayname = FormattingCodesParser.parseFormattingCodes(cfg.getString(kit + ".items." + items + ".displayname"));
				List<String> lore = cfg.getStringList(kit + ".items." + items + ".lore");

				if (material.toString().equalsIgnoreCase("SPLASH_POTION")) {
					item.add(getPotionItem(material, amount, displayname, lore, enchantments));
				} else {
					item.add(getItem(material, amount, displayname, lore, enchantments));
				}
				player.updateInventory();
				player.closeInventory();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		pitems.put(player, item);
		potionMap.put(player, pelist);
	}

	private void logPurchase(Player player, String item, int cost) {
		final ConsoleCommandSender console = plugin.getServer().getConsoleSender();
		if (plugin.getConfig().getBoolean("shop.logpurchases")) {
			console.sendMessage("[TNTRun_reloaded] " + ChatColor.AQUA + player.getName() + ChatColor.WHITE + " has bought a " + ChatColor.RED + item + ChatColor.WHITE + " for " + ChatColor.RED + cost + ChatColor.WHITE + " coins");
		}
	}
	
	private ItemStack getItem(Material material, int amount, String displayname, List<String> lore, List<String> enchantments){
		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(displayname);

		if (lore != null && !lore.isEmpty()) {
			meta.setLore(lore);
		}
		if (enchantments != null && !enchantments.isEmpty()) {
			for (String enchs : enchantments) {
				String[] array = enchs.split("#");
				String ench = array[0].toUpperCase();

				// get the enchantment level
				int level = 1;
				if (array.length > 1) {
					level = Integer.valueOf(array[1]).intValue();
				}
				Enchantment realEnch = getEnchantmentFromString(ench);
				if (realEnch != null) {
					meta.addEnchant(realEnch, level, true);
				}
			}
		}
		item.setItemMeta(meta);
		return item;
	}

	private ItemStack getPotionItem(Material material, int amount, String displayname, List<String> lore, List<String> enchantments) {
		ItemStack item = new ItemStack(material, amount);
		PotionMeta potionmeta = (PotionMeta) item.getItemMeta();

		potionmeta.setDisplayName(displayname);
		potionmeta.setColor(Color.RED);

		if (lore != null && !lore.isEmpty()) {
			potionmeta.setLore(lore);
		}
		if (enchantments != null && !enchantments.isEmpty()) {
			for (String peffects : enchantments) {
				PotionEffect effect = createPotionEffect(peffects);
				if (effect != null) {
					potionmeta.addCustomEffect(effect, true);
				}
			}
		}
		item.setItemMeta(potionmeta);
		return item;
	}

	private PotionEffect createPotionEffect(String effect) {
		String[] array = effect.split("#");
		String name = array[0].toUpperCase();
		int duration = 30;
		if (array.length > 1 && Utils.isNumber(array[1])) {
			duration = Integer.valueOf(array[1]).intValue();
		}
		int amplifier = 1;
		if (array.length > 2 && Utils.isNumber(array[2])) {
			amplifier = Integer.valueOf(array[2]).intValue();
		}
		PotionEffect peffect = new PotionEffect(PotionEffectType.getByName(name), duration * 20, amplifier);
		return peffect;
	}

	@EventHandler
	public void onClick(InventoryClickEvent e) {
		if (!e.getView().getTitle().equals(invname)) {
			return;
		}
		e.setCancelled(true);
		Player p = (Player)e.getWhoClicked();
		Arena arena = plugin.amanager.getPlayerArena(p.getName());
		if (e.getSlot() == e.getRawSlot() && e.getCurrentItem() != null) {
			ItemStack current = e.getCurrentItem();
			if (current.hasItemMeta() && current.getItemMeta().hasDisplayName()) {
				int kit = ((Integer)itemSlot.get(Integer.valueOf(e.getSlot()))).intValue();

				FileConfiguration cfg = ShopFiles.getShopConfiguration();
				String permission = cfg.getString(kit + ".permission");

				if (bought.contains(p)) {
					Messages.sendMessage(p, Messages.trprefix + Messages.alreadyboughtitem);
					plugin.sound.ITEM_SELECT(p);
					p.closeInventory();
					return;
				}
				if (!p.hasPermission(permission) && !p.hasPermission("tntrun.shop")) {
					p.closeInventory();
					Messages.sendMessage(p, Messages.trprefix + Messages.nopermission);
					plugin.sound.ITEM_SELECT(p);
					return;
				}
				String title = current.getItemMeta().getDisplayName();
				int cost = cfg.getInt(kit + ".cost");

				if (Material.getMaterial(cfg.getString(kit + ".material").toUpperCase()) == Material.FEATHER) {
					int maxjumps = plugin.getConfig().getInt("shop.doublejump.maxdoublejumps", 10);
					int quantity = cfg.getInt(kit + ".items." + kit + ".amount", 1);

					if (plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
						if (maxjumps <= plugin.getConfig().getInt("doublejumps." + p.getName()) || maxjumps < (plugin.getConfig().getInt("doublejumps." + p.getName()) + quantity)) {
							Messages.sendMessage(p, Messages.trprefix + Messages.maxdoublejumpsexceeded);
							plugin.sound.ITEM_SELECT(p);
							p.closeInventory();
							return;
						}
					} else {
						if (maxjumps <= arena.getPlayerHandler().getDoubleJumps(p) || maxjumps < (arena.getPlayerHandler().getDoubleJumps(p) + quantity)) {
							Messages.sendMessage(p, Messages.trprefix + Messages.maxdoublejumpsexceeded);
							plugin.sound.ITEM_SELECT(p);
							p.closeInventory();
							return;
						}
					}
				}
				if (hasMoney(cost, p)) {
					Messages.sendMessage(p, Messages.trprefix + Messages.playerboughtitem.replace("{ITEM}", title).replace("{MONEY}", cost + ""));
					logPurchase(p, title, cost);
					if (!plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
						Messages.sendMessage(p, Messages.trprefix + Messages.playerboughtwait);
					}
					plugin.sound.NOTE_PLING(p, 5, 10);
				} else {
					Messages.sendMessage(p, Messages.trprefix + Messages.notenoughtmoney.replace("{MONEY}", cost + ""));
					plugin.sound.ITEM_SELECT(p);
					return;
				}
				if (Material.getMaterial(cfg.getString(kit + ".material").toUpperCase()) == Material.FEATHER) {
					int quantity = cfg.getInt(kit + ".items." + kit + ".amount", 1);
					if (plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
						if(plugin.getConfig().get("doublejumps." + p.getName()) == null) {
							plugin.getConfig().set("doublejumps." + p.getName(), quantity);
						} else {
							plugin.getConfig().set("doublejumps." + p.getName(), plugin.getConfig().getInt("doublejumps." + p.getName()) + quantity);
						}
						plugin.saveConfig();
					} else {
						arena.getPlayerHandler().incrementDoubleJumps(p, quantity);
					}
					return;
				}
				giveItem(e.getSlot(), p, title);  
			}
		}
	}

	private boolean hasMoney(int moneyneed, Player player) {
		Economy econ = plugin.getVaultHandler().getEconomy();
		if(econ == null) {
			return false;
		}
		OfflinePlayer offplayer = player.getPlayer();
		double pmoney = econ.getBalance(offplayer);
		if(pmoney >= moneyneed) {
			econ.withdrawPlayer(offplayer, moneyneed);
			return true;
		}
		return false;
	}

	public void setItems(Inventory inventory){
		FileConfiguration cfg = ShopFiles.getShopConfiguration();
		int slot = 0;
		for (String kitCounter : cfg.getConfigurationSection("").getKeys(false)) {
			String title = FormattingCodesParser.parseFormattingCodes(cfg.getString(kitCounter + ".name"));
			List<String> lore = new ArrayList<String>();
			for (String loreLines : cfg.getStringList(kitCounter + ".lore")) {
				lore.add(FormattingCodesParser.parseFormattingCodes(loreLines));
			}
			Material material = Material.getMaterial(cfg.getString(kitCounter + ".material"));		      
			int amount = cfg.getInt(kitCounter + ".amount");

			if (material.toString().equalsIgnoreCase("POTION") || material.toString().equalsIgnoreCase("SPLASH_POTION")) {
				inventory.setItem(slot, getShopPotionItem(material, title, lore, amount));
			} else {
				inventory.setItem(slot, getShopItem(material, title, lore, amount));
			}
			itemSlot.put(Integer.valueOf(slot), Integer.valueOf(kitCounter));
			slot++;
		}
	}

	private ItemStack getShopItem(Material material, String title, List<String> lore, int amount){
		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();

		meta.setDisplayName(title);
		if ((lore != null) && (!lore.isEmpty())) {
			meta.setLore(lore);
		}
		item.setItemMeta(meta);
		return item;
	}

	private ItemStack getShopPotionItem(Material material, String title, List<String> lore, int amount) {
		ItemStack item = new ItemStack(material, amount);
		PotionMeta potionmeta = (PotionMeta) item.getItemMeta();

		potionmeta.setDisplayName(title);
		potionmeta.setColor(Color.BLUE);
		if (material.toString().equalsIgnoreCase("SPLASH_POTION")) {
			potionmeta.setColor(Color.RED);
		}
		potionmeta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
		if ((lore != null) && (!lore.isEmpty())) {
			potionmeta.setLore(lore);
		}
		item.setItemMeta(potionmeta);
		return item;
	}

	private Enchantment getEnchantmentFromString(String enchantment) {		
		return Enchantment.getByKey(NamespacedKey.minecraft(enchantment.toLowerCase()));
	}

	public List<PotionEffect> getPotionEffects(Player player) {
		return potionMap.get(player);
	}

	public void removePotionEffects(Player player) {
		potionMap.remove(player);
	}

	public String getInvname() {
		return invname;
	}

	public int getInvsize() {
		return invsize;
	}

	public HashMap<Player, ArrayList<ItemStack>> getPlayersItems() {
		return pitems;
	}

	public List<Player> getBuyers() {
		return bought;
	}

	public boolean hasDoubleJumps(Player player) {
		return plugin.getConfig().getInt("doublejumps." + player.getName(), 0) > 0;
	}
}
