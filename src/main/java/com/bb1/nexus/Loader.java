package com.bb1.nexus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.event.FactionCreateEvent;
import com.massivecraft.factions.event.FactionDisbandEvent;
import com.massivecraft.factions.event.FactionDisbandEvent.PlayerDisbandReason;
import com.massivecraft.factions.event.FactionRenameEvent;
import com.massivecraft.factions.event.LandUnclaimAllEvent;
import com.massivecraft.factions.event.LandUnclaimEvent;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
/**
 * Copyright 2021 BradBot_1
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class Loader extends JavaPlugin implements Listener {
	
	private Map<Location, Faction> nexusMap = new HashMap<Location, Faction>();
	
	private Map<OfflinePlayer, Location> playersThatCanMakeFaction = new HashMap<OfflinePlayer, Location>();
	
	private static final String NEXUS_NAME = "§cNexus";
	private static final String DELETE_NAME = "deleteMe";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final boolean BLOCK_EXPLOSIONS = true;
	
	private ItemStack nexusItemStack() {
		ItemStack itemStack = new ItemStack(Material.BEACON);
		ItemMeta itemMeta = itemStack.getItemMeta();
		itemMeta.setDisplayName(NEXUS_NAME);
		itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		itemStack.setItemMeta(itemMeta);
		return itemStack;
	}
	
	private Recipe nexusRecipe() {
		ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "nexus"), nexusItemStack().clone());
		recipe.shape("ggg","gdg","ooo");
		recipe.setIngredient('g', Material.GLASS);
		recipe.setIngredient('d', Material.DIAMOND);
		recipe.setIngredient('o', Material.OBSIDIAN);
		return recipe;
	}
	
	@Override
	public void onEnable() {
		new File(getDataFolder().getAbsolutePath()).mkdirs();
		Bukkit.getPluginManager().registerEvents(this, this);
		Bukkit.getServer().addRecipe(nexusRecipe());
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

			@Override
			public void run() {
				load();
			}
			
		});
	}
	
	@Override
	public void onDisable() {
		save();
	}
	
	// Events
	
	@EventHandler
	public void FactionCreateEvent(FactionCreateEvent event) {
		if (!playersThatCanMakeFaction.containsKey(event.getFPlayer().getPlayer()) || event.getFactionTag().equals(DELETE_NAME)) {
			event.setCancelled(true);
			event.getFPlayer().getPlayer().sendMessage("§c[!]§r You need to have a nexus to do that!");// You need to have a nexus to do that!
		} else {
			final Location nexus = playersThatCanMakeFaction.get(event.getFPlayer().getPlayer()).clone();
			playersThatCanMakeFaction.remove(event.getFPlayer().getPlayer());
			Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

				@Override
				public void run() {
					FPlayer fPlayer = event.getFPlayer();
					Faction faction = event.getFPlayer().getFaction();
					nexusMap.put(nexus, faction);
					faction.setPowerBoost(faction.getPowerBoost()+1);
					fPlayer.alterPower(1);
					fPlayer.attemptClaim(faction, nexus, false);
					nexus.getWorld().playSound(nexus, Sound.BLOCK_BEACON_ACTIVATE, 500, 1);
				}
				
			}, 4l);
		}
	}
	
	@EventHandler
	public void LandUnclaimEvent(LandUnclaimEvent event) {
		Chunk chunk = event.getLocation().getChunk();
		nexusMap.forEach((k, v) -> {
			if (event.isCancelled()) return;
			Chunk chunk2 = k.getChunk();
			if (!chunk2.isLoaded()) {
				return;
			} else if (k.getChunk().equals(chunk)) {
				event.setCancelled(true);
				event.getfPlayer().getPlayer().sendMessage("§c[!]§r You cannot unclaim a chunk that contains a nexus");// You cannot unclaim a chunk that contains a nexus
			}
		});
	}
	
	@EventHandler
	public void LandUnclaimAllEvent(LandUnclaimAllEvent event) {
		event.setCancelled(true);// If they unclaimed all it would unclaim the nexus
		for (Entry<Location, Faction> entry : nexusMap.entrySet()) {
			if (entry.getValue().getId().equals(event.getFaction().getId())) {
				event.getfPlayer().attemptClaim(entry.getValue(), entry.getKey(), false);
				return;
			}
		}
	}
	
	@EventHandler
	public void FactionRenameEvent(FactionRenameEvent event) {
		for (Entry<Location, Faction> entry : nexusMap.entrySet()) {
			if (entry.getValue().getId().equals(event.getFaction().getId())) {
				if (event.getFactionTag().equals(DELETE_NAME)) {
					event.setCancelled(true);
				}
				return;
			}
		}
	}
	
	@EventHandler
	public void FactionDisbandEvent(FactionDisbandEvent event) {
		if (!(event.getFaction().getTag().equals(DELETE_NAME))) {
			event.setCancelled(true);
			event.getFPlayer().getPlayer().sendMessage("§c[!]§r To disband your faction you must break your nexus");// To disband your faction you must break your nexus
		}
	}
	
	@EventHandler
	public void BlockPlaceEvent(BlockPlaceEvent event) {
		ItemStack itemStack = event.getItemInHand();
		if (itemStack.hasItemMeta() && itemStack.getItemMeta().getDisplayName().equals(NEXUS_NAME) && itemStack.getType().equals(Material.BEACON)) { // Is nexus
			if (FPlayers.getInstance().getByPlayer(event.getPlayer()).hasFaction()) {
				event.setCancelled(true);
				event.getPlayer().sendMessage("§c[!]§r You cannot place a nexus while in a faction!");// You cannot place a nexus while in a faction!
			} else {
				playersThatCanMakeFaction.put(event.getPlayer(), event.getBlock().getLocation());
			}
		}
	}
	
	@EventHandler
	public void BlockBreakEvent(BlockBreakEvent event) {
		if (event.getBlock()!=null && event.getBlock().getType().equals(Material.BEACON)) {
			if (nexusMap.containsKey(event.getBlock().getLocation())) {
				Faction faction = nexusMap.get(event.getBlock().getLocation());
				nexusMap.remove(event.getBlock().getLocation());
				Bukkit.getOnlinePlayers().forEach((p)->p.sendMessage("§lThe faction §r"+faction.getTag()+" §lhas been defeated"));
				faction.setTag(DELETE_NAME);
				faction.disband(faction.getFPlayerLeader().getPlayer(), PlayerDisbandReason.PLUGIN);
				faction.remove();
				event.setDropItems(false);
			} else if (playersThatCanMakeFaction.containsValue(event.getBlock().getLocation())) {
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void EntityExplodeEvent(EntityExplodeEvent event) {
		if (!BLOCK_EXPLOSIONS) return;
		List<Block> removedBlocks = new ArrayList<Block>();
		event.blockList().forEach((b)->{
			Location loc = b.getLocation().clone();
			if (playersThatCanMakeFaction.containsValue(loc) || nexusMap.containsKey(loc)) {
				removedBlocks.add(b);
			}
		});
		event.blockList().removeAll(removedBlocks);
	}
	
	@EventHandler
	public void BlockExplodeEvent(BlockExplodeEvent event) {
		if (!BLOCK_EXPLOSIONS) return;
		List<Block> removedBlocks = new ArrayList<Block>();
		event.blockList().forEach((b)->{
			Location loc = b.getLocation().clone();
			if (playersThatCanMakeFaction.containsValue(loc) || nexusMap.containsKey(loc)) {
				removedBlocks.add(b);
			}
		});
		event.blockList().removeAll(removedBlocks);
	}
	
	@EventHandler
	public void onAnvil(InventoryClickEvent event) {
		if (event.getWhoClicked()==null || !(event.getWhoClicked() instanceof Player) ||event.getCurrentItem()==null || event.getCurrentItem().getType()==Material.AIR || event.getInventory().getType()!=InventoryType.ANVIL) {
			return;
		}
		if(event.getSlotType() == InventoryType.SlotType.RESULT && event.getCurrentItem().getType().equals(Material.BEACON) && event.getCurrentItem().getItemMeta().getItemFlags().contains(ItemFlag.HIDE_ATTRIBUTES)) {
			event.setCancelled(true);
			event.getWhoClicked().sendMessage("§c[!]§r You cannot rename the nexus"); // You cannot rename the nexus
		}
	}
	
	@EventHandler
	public void PlayerMoveEvent(PlayerMoveEvent event) {
		if (playersThatCanMakeFaction.containsKey(event.getPlayer())) {
			event.setCancelled(true);
			event.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§cCreate your faction by running /factions create <name>")); // Create your faction by running /f create <name>
		}
	}
	
	// TODO: Handling saving and loading
	
	private void save() {
		try {
			System.out.println("Saving Nexus");
			File file = new File(getDataFolder().getAbsolutePath()+"\\nexus.json");
			file.createNewFile();
			JsonObject jsonObject = new JsonObject();
			for (Entry<Location, Faction> entry : nexusMap.entrySet()) {
				jsonObject.addProperty(entry.getValue().getId(), loc(entry.getKey()));
			}
			BufferedWriter b = new BufferedWriter(new PrintWriter(file));
			b.write(GSON.toJson(jsonObject));
			b.flush();
			b.close();
			// File 2
			File file2 = new File(getDataFolder().getAbsolutePath()+"\\extra.json");
			file2.createNewFile();
			JsonObject jsonObject2 = new JsonObject();
			for (Entry<OfflinePlayer, Location> entry : playersThatCanMakeFaction.entrySet()) {
				jsonObject2.addProperty(entry.getKey().getUniqueId().toString(), loc(entry.getValue()));
			}
			BufferedWriter b2 = new BufferedWriter(new PrintWriter(file2));
			b2.write(GSON.toJson(jsonObject2));
			b2.flush();
			b2.close();
		} catch (Exception e) {
			System.err.println("Failed to save");
			e.printStackTrace();
		}
	}
	
	private void load() {
		try {
			System.out.println("Loading Nexus");
			File file = new File(getDataFolder().getAbsolutePath()+"\\nexus.json");
			Scanner s = new Scanner(file);
			ArrayList<String> r = new ArrayList<>();
			while (s.hasNext()) {
		    	r.add(s.nextLine());
			}
			s.close();
			JsonObject jsonObject = new JsonParser().parse(String.join("", r)).getAsJsonObject();
			Factions factions = Factions.getInstance();
			for (Entry<String, JsonElement> entry : jsonObject.entrySet()) {
				nexusMap.put(loc(entry.getValue().getAsString()), factions.getFactionById(entry.getKey()));
			}
			// repeat for second file
			File file2 = new File(getDataFolder().getAbsolutePath()+"\\extra.json");
			Scanner s2 = new Scanner(file2);
			ArrayList<String> r2 = new ArrayList<>();
			while (s2.hasNext()) {
		    	r2.add(s2.nextLine());
			}
			s2.close();
			JsonObject jsonObject2 = new JsonParser().parse(String.join("", r2)).getAsJsonObject();
			for (Entry<String, JsonElement> entry : jsonObject2.entrySet()) {
				playersThatCanMakeFaction.put(Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey())), loc(entry.getValue().getAsString()));
			}
		} catch (Exception e) {
			System.err.println("Failed to load");
			e.printStackTrace();
		}
	}
	
	public String loc(Location location) {
		String str = new String();
		str = str + (location.getWorld().getUID().toString() + ";");
		str = str + (location.getBlockX() + ";");
		str = str + (location.getBlockY() + ";");
		str = str + (location.getBlockZ());
		return str;
	}
	
	public Location loc(String str) {
		String[] s = str.split(";");
		return new Location(Bukkit.getWorld(UUID.fromString(s[0])), Integer.parseInt(s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]));
	}
	
}
