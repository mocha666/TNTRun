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

import tntrun.TNTRun;
import tntrun.arena.Arena;
import tntrun.messages.Messages;

public class Shop implements Listener{

	private TNTRun plugin;
	private String invname;
	private int invsize;
	private int knockback;

	public Shop(TNTRun plugin){
		this.plugin = plugin;
		ShopFiles shopFiles = new ShopFiles(plugin);
		shopFiles.setShopItems();

		invsize = plugin.getConfig().getInt("shop.size");
		invname = FormattingCodesParser.parseFormattingCodes(plugin.getConfig().getString("shop.name"));
	}  

	private HashMap<Integer, Integer> itemSlot = new HashMap<Integer, Integer>();
	private HashMap<String, ArrayList<ItemStack>> pitems = new HashMap<String, ArrayList<ItemStack>>(); // player-name -> items
	private List<String> buyers = new ArrayList<String>();
	private HashMap<String, List<PotionEffect>> potionMap = new HashMap<String, List<PotionEffect>>();  // player-name -> effects

	private void giveItem(int slot, Player player, String title) {
		int kit = itemSlot.get(slot);		
		ArrayList<ItemStack> item = new ArrayList<ItemStack>();
		FileConfiguration cfg = ShopFiles.getShopConfiguration();
		List<PotionEffect> pelist = new ArrayList<PotionEffect>();

		if(!buyers.contains(player.getName())) {
			buyers.add(player.getName());
		}
		for(String items : cfg.getConfigurationSection(kit + ".items").getKeys(false)) {
			// if the item is double jumps, store them and skip to next item
			if (Material.getMaterial(cfg.getString(kit + ".material").toUpperCase()) == Material.FEATHER) {
				int quantity = cfg.getInt(kit + ".items." + kit + ".amount", 1);
				giveDoubleJumps(player, quantity);
				continue;
			}
			try {				
				Material material = Material.getMaterial(cfg.getString(kit + ".items." + items + ".material"));
				int amount = cfg.getInt(kit + ".items." + items + ".amount");
				List<String> enchantments = cfg.getStringList(kit + ".items." + items + ".enchantments");

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
					continue;
				}
				String displayname = FormattingCodesParser.parseFormattingCodes(cfg.getString(kit + ".items." + items + ".displayname"));
				List<String> lore = cfg.getStringList(kit + ".items." + items + ".lore");

				if (material.toString().equalsIgnoreCase("SPLASH_POTION")) {
					item.add(getPotionItem(material, amount, displayname, lore, enchantments));
				} else {
					item.add(getItem(material, amount, displayname, lore, enchantments));
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		player.updateInventory();
		player.closeInventory();
		pitems.put(player.getName(), item);
		potionMap.put(player.getName(), pelist);
	}

	private void giveDoubleJumps(Player player, int quantity) {
		if (plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
			if(plugin.getConfig().get("doublejumps." + player.getName()) == null) {
				plugin.getConfig().set("doublejumps." + player.getName(), quantity);
			} else {
				plugin.getConfig().set("doublejumps." + player.getName(), plugin.getConfig().getInt("doublejumps." + player.getName()) + quantity);
			}
			plugin.saveConfig();
		} else {
			Arena arena = plugin.amanager.getPlayerArena(player.getName());
			arena.getPlayerHandler().incrementDoubleJumps(player, quantity);
		}
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
				if (array.length > 1 && Utils.isNumber(array[1])) {
					level = Integer.valueOf(array[1]).intValue();
				}
				Enchantment realEnch = getEnchantmentFromString(ench);
				if (realEnch != null) {
					meta.addEnchant(realEnch, level, true);
				}
				if (material == Material.SNOWBALL && ench.equalsIgnoreCase("knockback")) {
					knockback = level;
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
		if (e.getRawSlot() == getInvsize() -1) {
			return;
		}
		Player p = (Player)e.getWhoClicked();
		if (e.getSlot() == e.getRawSlot() && e.getCurrentItem() != null) {
			ItemStack current = e.getCurrentItem();
			if (current.hasItemMeta() && current.getItemMeta().hasDisplayName()) {
				int kit = itemSlot.get(e.getSlot());

				FileConfiguration cfg = ShopFiles.getShopConfiguration();
				String permission = cfg.getString(kit + ".permission");

				if (buyers.contains(p.getName())) {
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

				if (!canBuyDoubleJumps(cfg, p, kit)) {
					Messages.sendMessage(p, Messages.trprefix + Messages.maxdoublejumpsexceeded);
					plugin.sound.ITEM_SELECT(p);
					p.closeInventory();
					return;
				}
				if (hasMoney(cost, p)) {
					Messages.sendMessage(p, Messages.trprefix + Messages.playerboughtitem.replace("{ITEM}", title).replace("{MONEY}", cost + ""));
					logPurchase(p, title, cost);
					// purchased double jumps are not given until free ones are disabled
					if (!plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
						Messages.sendMessage(p, Messages.trprefix + Messages.playerboughtwait);
					}
					plugin.sound.NOTE_PLING(p, 5, 10);
				} else {
					Messages.sendMessage(p, Messages.trprefix + Messages.notenoughtmoney.replace("{MONEY}", cost + ""));
					plugin.sound.ITEM_SELECT(p);
					return;
				}
				giveItem(e.getSlot(), p, title);  
			}
		}
	}

	private boolean canBuyDoubleJumps(FileConfiguration cfg, Player p, int kit) {
		Arena arena = plugin.amanager.getPlayerArena(p.getName());
		int maxjumps = plugin.getConfig().getInt("shop.doublejump.maxdoublejumps", 10);
		int quantity = cfg.getInt(kit + ".items." + kit + ".amount", 1);

		if (plugin.getConfig().getBoolean("freedoublejumps.enabled")) {
			if (maxjumps <= plugin.getConfig().getInt("doublejumps." + p.getName()) || maxjumps < (plugin.getConfig().getInt("doublejumps." + p.getName()) + quantity)) {
				return false;
			}
		} else if (maxjumps <= arena.getPlayerHandler().getDoubleJumps(p) || maxjumps < (arena.getPlayerHandler().getDoubleJumps(p) + quantity)) {
			return false;
		}
		return true;
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

	private double getPlayerBalance(Player player) {
		Economy econ = plugin.getVaultHandler().getEconomy();
		if(econ == null) {
			return 0.0;
		}
		OfflinePlayer offplayer = player.getPlayer();
		return econ.getBalance(offplayer);
	}

	public void setItems(Inventory inventory, Player player){
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
		inventory.setItem(getInvsize() -1, setMoneyItem(inventory, player));
	}

	private ItemStack setMoneyItem(Inventory inv, Player player) {
		Material material = Material.getMaterial(plugin.getConfig().getString("shop.showmoneyitem", "GOLD_INGOT"));
		String title = FormattingCodesParser.parseFormattingCodes(Messages.shopmoneyheader);
		List<String> lore = new ArrayList<String>();
		lore.add(FormattingCodesParser.parseFormattingCodes(Messages.shopmoneybalance).replace("{BAL}", String.valueOf(getPlayerBalance(player))));
		return getShopItem(material, title, lore, 1);
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
		return potionMap.get(player.getName());
	}

	public void removePotionEffects(Player player) {
		potionMap.remove(player.getName());
	}

	public String getInvname() {
		return invname;
	}

	public int getInvsize() {
		return invsize;
	}

	public HashMap<String, ArrayList<ItemStack>> getPlayersItems() {
		return pitems;
	}

	public List<String> getBuyers() {
		return buyers;
	}

	public boolean hasDoubleJumps(Player player) {
		return plugin.getConfig().getInt("doublejumps." + player.getName(), 0) > 0;
	}

	public double getKnockback() {
		return Math.min(Math.max(knockback, 0), 5) * 0.4;
	}
}
