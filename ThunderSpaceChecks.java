package com.destroyer.thunderspacechecks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public final class ThunderSpaceChecks extends JavaPlugin implements Listener {

    private final HashMap<UUID, BossBar> checkedPlayers = new HashMap<>();
    private final HashMap<UUID, Long> checkStartTimes = new HashMap<>();
    private final HashMap<UUID, UUID> moderatorCheck = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        config = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ThunderSpaceChecks is enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ThunderSpaceChecks is disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("check")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }
            Player moderator = (Player) sender;
            if (!moderator.hasPermission("thunderspacechecks.check")) {
                moderator.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (args.length != 1) {
                moderator.sendMessage(ChatColor.RED + "Usage: /check <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                moderator.sendMessage(ChatColor.RED + "Player not found!");
                return true;
            }

            if (moderator.equals(target)) {
                moderator.sendMessage(ChatColor.RED + "You cannot check yourself!");
                return true;
            }

            if (checkedPlayers.containsKey(target.getUniqueId())) {
                moderator.sendMessage(ChatColor.RED + "This player is already being checked!");
                return true;
            }

            if (moderatorCheck.containsKey(moderator.getUniqueId())) {
                moderator.sendMessage(ChatColor.RED + "You are already checking another player!");
                return true;
            }

            startCheck(moderator, target);
            return true;
        }

        if (label.equalsIgnoreCase("checkdone")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this command!");
                return true;
            }
            Player moderator = (Player) sender;
            if (!moderator.hasPermission("thunderspacechecks.check")) {
                moderator.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
                return true;
            }

            if (!moderatorCheck.containsKey(moderator.getUniqueId())) {
                moderator.sendMessage(ChatColor.RED + "You are not checking anyone!");
                return true;
            }

            Player target = Bukkit.getPlayer(moderatorCheck.get(moderator.getUniqueId()));
            if (target == null) {
                moderator.sendMessage(ChatColor.RED + "The player has left the game!");
                return true;
            }

            endCheck(moderator, target);
            return true;
        }

        return false;
    }

    private void startCheck(Player moderator, Player target) {
        BossBar bossBar = Bukkit.createBossBar(
                ChatColor.translateAlternateColorCodes('&', config.getString("bossbar.title", "&cTime left: 5:00")),
                BarColor.RED,
                BarStyle.SOLID
        );
        bossBar.addPlayer(target);
        bossBar.setProgress(1.0);
        checkedPlayers.put(target.getUniqueId(), bossBar);
        checkStartTimes.put(target.getUniqueId(), System.currentTimeMillis());
        moderatorCheck.put(moderator.getUniqueId(), target.getUniqueId());

        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 300, 1));
        target.setGameMode(GameMode.ADVENTURE);
        target.sendTitle(
                ChatColor.translateAlternateColorCodes('&', config.getString("screen.title", "&cCHECK")),
                ChatColor.translateAlternateColorCodes('&', config.getString("screen.subtitle", "&6Check the instructions in the chat.")),
                10, 100, 10
        );
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("chat.message", "&cCHECK\n&6You have been called for a cheat check...")));

        Bukkit.broadcast(
                ChatColor.translateAlternateColorCodes('&', config.getString("moderator.startMessage", "&e[MOD] Player &c%player% has been started to be checked.").replace("%player%", target.getName())),
                "thunderspacechecks.check"
        );

        new Thread(() -> {
            try {
                Thread.sleep(300000); // Wait 5 minutes
                if (checkedPlayers.containsKey(target.getUniqueId())) {
                    Bukkit.getScheduler().runTask(this, () -> autoBan(target));
                }
            } catch (InterruptedException ignored) {
            }
        }).start();
    }

    private void endCheck(Player moderator, Player target) {
        BossBar bossBar = checkedPlayers.remove(target.getUniqueId());
        if (bossBar != null) bossBar.removeAll();
        checkStartTimes.remove(target.getUniqueId());
        moderatorCheck.remove(moderator.getUniqueId());

        target.removePotionEffect(PotionEffectType.BLINDNESS);
        target.setGameMode(GameMode.SURVIVAL);
        target.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("chat.finishMessage", "&aYou have passed the check!")));

        moderator.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("moderator.finishMessage", "&aYou have finished checking &c%player%.").replace("%player%", target.getName())));
    }

    private void autoBan(Player target) {
        checkedPlayers.remove(target.getUniqueId());
        checkStartTimes.remove(target.getUniqueId());
        target.kickPlayer(ChatColor.translateAlternateColorCodes('&', config.getString("ban.message", "&cYou did not respond to the check. You are banned for 30 days.")));
        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                config.getString("ban.command", "ban %player% 30d Did not provide Discord.").replace("%player%", target.getName())
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (checkedPlayers.containsKey(player.getUniqueId())) {
            autoBan(player);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (checkedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (checkedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (checkedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Player under check
        if (checkedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);

            UUID moderatorId = null;
            for (UUID modId : moderatorCheck.keySet()) {
                if (moderatorCheck.get(modId).equals(player.getUniqueId())) {
                    moderatorId = modId;
                    break;
                }
            }
            if (moderatorId == null) return;

            Player moderator = Bukkit.getPlayer(moderatorId);
            if (moderator != null) {
                String message = ChatColor.RED + "CHECK | " + ChatColor.AQUA + "(" + player.getName() + "): " + ChatColor.RESET + event.getMessage();
                player.sendMessage(message);
                moderator.sendMessage(message);
            }
        }

        // Moderator
        if (moderatorCheck.containsKey(player.getUniqueId())) {
            event.setCancelled(true);

            UUID targetId = moderatorCheck.get(player.getUniqueId());
            Player target = Bukkit.getPlayer(targetId);
            if (target != null) {
                String message = ChatColor.RED + "CHECK | " + ChatColor.GOLD + "(" + player.getName() + "): " + ChatColor.RESET + event.getMessage();
                player.sendMessage(message);
                target.sendMessage(message);
            }
        }
    }
}