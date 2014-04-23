package net.chaoticprison.cratekeys;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CrateKeys extends JavaPlugin implements Listener, CommandExecutor {

    public Map<String, Tier> tierMap = new TreeMap<String, Tier>(String.CASE_INSENSITIVE_ORDER);
    public Map<Location, Tier> crateMap = new HashMap<Location, Tier>();

    public Map<UUID, Tier> addingCrate = new HashMap<UUID, Tier>();

    public class Tier {

        private String tierID;
        private String broadcastPrefix;
        private String keyName;
        private List<String> keyLore;
        private List<String> coloredLore;
        private String keyEnchantment;
        private Material keyMaterial;
        private Sound keySound;
        private List<PrizePackage> prizePackages;
        private List<Location> crateLocations;

        private RandomCollection<PrizePackage> randomCollection = new RandomCollection<PrizePackage>();

        public Tier(String tierID, String broadcastPrefix, String keyName, List<String> keyLore, String keyEnchantment, Material keyMaterial, String keySound, List<PrizePackage> prizePackages) {
            this.tierID = tierID;
            this.broadcastPrefix = broadcastPrefix;
            this.keyName = keyName;
            this.keyLore = keyLore;
            this.keyEnchantment = keyEnchantment;
            this.keyMaterial = keyMaterial;
            if(keySound.equalsIgnoreCase("NONE")) this.keySound = null;
            else this.keySound = Sound.valueOf(keySound);
            this.prizePackages = prizePackages;
            this.crateLocations = new ArrayList<Location>();
        }

        public String getTierID() { return tierID; }

        public String getKeyName(boolean colored) {
            if(colored) return ChatColor.translateAlternateColorCodes('&', keyName);
            return keyName;
        }

        public List<String> getKeyLore(boolean colored) {
            if(colored) {
                if(coloredLore == null) {
                    coloredLore = new ArrayList<String>();
                    for(String loreLine : keyLore) coloredLore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
                }
                return coloredLore;
            }
            return keyLore;
        }

        public String getKeyEnchantment() {
            return keyEnchantment;
        }

        public Material getKeyMaterial() {
            return keyMaterial;
        }

        public Sound getKeySound() {
            return keySound;
        }

        public List<PrizePackage> getPrizePackages() {
            return prizePackages;
        }

        public void addPrizePackage(PrizePackage prizePackage) {
            prizePackages.add(prizePackage);
            randomCollection.add(prizePackage.getPercentageChance(), prizePackage);
        }

        public void executeRandomPrize(Player player) {
            PrizePackage prizePackage = randomCollection.next();
            if(prizePackage.getAvoidPermission() != null && !prizePackage.getAvoidPermission().equalsIgnoreCase("NONE") && player.hasPermission(prizePackage.getAvoidPermission()))
                while(player.hasPermission(prizePackage.getAvoidPermission())) prizePackage = randomCollection.next();
            if(prizePackage.getPlayerMessage(false) != null && !prizePackage.getPlayerMessage(false).equalsIgnoreCase("NONE")) player.sendMessage(prizePackage.getPlayerMessage(true).replace("%player%", player.getName()));
            if(prizePackage.getBroadcastMessage() != null && !prizePackage.getBroadcastMessage().isEmpty())
                for(String message : prizePackage.getBroadcastMessage()) player.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastPrefix + message.replace("%player%", player.getName())));
            if(prizePackage.getCommands() != null && !prizePackage.getCommands().isEmpty())
                for(String command : prizePackage.getCommands()) player.getServer().dispatchCommand(player.getServer().getConsoleSender(), command.replace("%player%", player.getName()));
            if(getKeySound() != null) player.playSound(player.getLocation(), keySound, 1, 1);
        }

        public List<Location> getCrateLocations() {
            return crateLocations;
        }

        public void addCrateLocation(Location crateLoc) {
            crateLocations.add(crateLoc);
            crateMap.put(crateLoc, this);
        }
    }

    public class PrizePackage {

        private Tier tier;
        private int packageID;
        private String avoidPermission;
        private int percentageChance;
        private String playerMessage;
        private List<String> broadcastMessage;
        private List<String> commands;

        public PrizePackage(Tier tier, int packageID, String avoidPermission, int percentageChance, String playerMessage, List<String> broadcastMessage, List<String> commands) {
            this.tier = tier;
            this.packageID = packageID;
            this.avoidPermission = avoidPermission;
            this.percentageChance = percentageChance;
            this.playerMessage = playerMessage;
            this.broadcastMessage = broadcastMessage;
            this.commands = commands;
        }

        public Tier getTier() {
            return tier;
        }

        public int getPackageID() {
            return packageID;
        }

        public String getAvoidPermission() { return avoidPermission; }

        public int getPercentageChance() {
            return percentageChance;
        }

        public String getPlayerMessage(boolean colored) {
            if(colored) return ChatColor.translateAlternateColorCodes('&', playerMessage);
            return playerMessage;
        }

        public List<String> getBroadcastMessage() {
            return broadcastMessage;
        }

        public List<String> getCommands() {
            return commands;
        }
    }

    public class RandomCollection<E> {
        private final NavigableMap<Double, E> map = new TreeMap<Double, E>();
        private final Random random;
        private double total = 0;

        public RandomCollection() {
            this(new Random());
        }

        public RandomCollection(Random random) {
            this.random = random;
        }

        public void add(double weight, E result) {
            if (weight <= 0) return;
            total += weight;
            map.put(total, result);
        }

        public E next() {
            double value = random.nextDouble() * total;
            return map.ceilingEntry(value).getValue();
        }
    }

    public void onEnable() {

        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("cratekey").setExecutor(this);

        /**Loading Tiers...**/
        for(String tierID : getConfig().getKeys(false)) {
            ConfigurationSection tierC = getConfig().getConfigurationSection(tierID);
            tierMap.put(tierID, new Tier(tierID, tierC.getString("broadcastPrefix"), tierC.getString("keyName"), tierC.getStringList("keyLore"), tierC.getString("keyEnchantment"),
                    Material.valueOf(tierC.getString("keyMaterial")), tierC.getString("keySound"), new ArrayList<PrizePackage>()));
            for(String crateLoc : tierC.getStringList("crateLocations")) tierMap.get(tierID).addCrateLocation(parseLocString(crateLoc));
            for(String packageID : tierC.getConfigurationSection("prizePackages").getKeys(false)) {
                ConfigurationSection packageC = tierC.getConfigurationSection("prizePackages." + packageID);
                tierMap.get(tierID).addPrizePackage(new PrizePackage(tierMap.get(tierID), Integer.parseInt(packageID), packageC.getString("avoidPermission"), packageC.getInt("percent"),
                        packageC.getString("playerMessage"), packageC.getStringList("broadcastMessage"), packageC.getStringList("commands")));
            }
        } /**Tiers and Prize Packages loaded!**/

        getLogger().info("has been enabled");
    }

    public void onDisable() {

        /**Saving Crate locations by Tier...**/
        for(Tier tier : tierMap.values()) {
            List<String> crateLocs = new ArrayList<String>();
            for(Location crateLoc : tier.getCrateLocations()) crateLocs.add(parseLoc(crateLoc));
            getConfig().set(tier.getTierID() + ".crateLocations", crateLocs);
        }/**Crate locations saved!**/

        saveConfig();
        getLogger().info("has been disabled");
    }

    public String parseLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + loc.getWorld().getName();
    }

    public Location parseLocString(String locString) {
        String[] args = locString.split(",");
        return new Location(getServer().getWorld(args[3]), Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if(e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock().getType() != Material.CHEST || e.isCancelled()) return;
        if(e.getPlayer().isOp() && addingCrate.containsKey(e.getPlayer().getUniqueId())) {
            addingCrate.get(e.getPlayer().getUniqueId()).addCrateLocation(e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(ChatColor.GREEN + "Crate added to tier " + addingCrate.get(e.getPlayer().getUniqueId()).getTierID());
            addingCrate.remove(e.getPlayer().getUniqueId());
            e.setCancelled(true);
        } else if(crateMap.containsKey(e.getClickedBlock().getLocation())) {
            Tier crateTier = crateMap.get(e.getClickedBlock().getLocation());
            if(e.getItem() != null && e.getItem().hasItemMeta() && e.getItem().getItemMeta().getDisplayName().equalsIgnoreCase(crateTier.getKeyName(true)))
                if(e.getItem().getType() == crateTier.getKeyMaterial() && e.getItem().getItemMeta().hasLore()) {
                    crateTier.executeRandomPrize(e.getPlayer());
                    if(e.getItem().getAmount() > 1) e.getItem().setAmount(e.getItem().getAmount() - 1);
                    else e.getPlayer().setItemInHand(null);
                    e.getPlayer().updateInventory();
                    e.setCancelled(true);
                }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(!sender.isOp()) sender.sendMessage(ChatColor.RED + "You do not have permission to use this command!");
        if(args.length == 0) return false;
        else if (args[0].equalsIgnoreCase("give")) {
            if(args.length != 4) sender.sendMessage(ChatColor.RED + "Syntax error! Usage: /cratekey give <player> <tierName> <amount>");
            else {
                if(getServer().getPlayer(args[1]) == null) sender.sendMessage(ChatColor.RED + "That player is not online!");
                else if(!tierMap.containsKey(args[2])) sender.sendMessage(ChatColor.RED + "That tier does not exist!");
                else try {
                        Tier tier = tierMap.get(args[2]);
                        ItemStack tierKey = new ItemStack(tier.getKeyMaterial(), Integer.parseInt(args[3]));
                        ItemMeta keyMeta = tierKey.getItemMeta();
                        if(tier.getKeyName(false) != null && !tier.getKeyName(false).equalsIgnoreCase("NONE")) keyMeta.setDisplayName(tier.getKeyName(true));
                        if(tier.getKeyLore(false) != null && !tier.getKeyLore(false).isEmpty()) keyMeta.setLore(tier.getKeyLore(true));
                        if(tier.getKeyEnchantment() != null && !tier.getKeyEnchantment().startsWith("NONE"))
                            keyMeta.addEnchant(Enchantment.getByName(tier.getKeyEnchantment().split(":")[0]), Integer.parseInt(tier.getKeyEnchantment().split(":")[1]), true);
                        tierKey.setItemMeta(keyMeta);
                        Bukkit.getPlayer(args[1]).getInventory().addItem(tierKey);
                        Bukkit.getPlayer(args[1]).updateInventory();
                        Bukkit.getPlayer(args[1]).sendMessage(ChatColor.GREEN + "Here are your crate keys!");
                    } catch(NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Error! Amount must be a number!");
                    }
            }
        } else if(args[0].equalsIgnoreCase("addcrate")) {
            if(!(sender instanceof Player)) sender.sendMessage(ChatColor.RED + "Error! This command cannot be run from the console!");
            else if(args.length != 2) sender.sendMessage(ChatColor.RED + "Syntax error! Usage: /cratekey addcrate <tierName>");
            else if(!tierMap.containsKey(args[1])) sender.sendMessage(ChatColor.RED + "That tier does not exist!");
            else {
                addingCrate.put(((Player) sender).getUniqueId(), tierMap.get(args[1]));
                sender.sendMessage(ChatColor.GREEN + "Click any chest to set that chest as a create for tier " + args[1]);
            }
        } else return false;
        return true;
    }
}
