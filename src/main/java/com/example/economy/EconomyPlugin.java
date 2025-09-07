package com.example.RealEconomy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RealEconomy extends JavaPlugin {

    // --- プレイヤーデータ ---
    private Map<UUID, Integer> balances = new HashMap<>();
    private Map<UUID, Map<String, Integer>> playerStocks = new HashMap<>();
    
    // --- 株データ ---
    private Map<String, Double> stockPrices = new HashMap<>();
    private Random random = new Random();
    
    // --- 固定資産税 ---
    private int fixedTax = 100;

    @Override
    public void onEnable() {
        getLogger().info("RealEconomy 有効化！");

        // 株価を初期化
        stockPrices.put("TechCorp", 100.0);
        stockPrices.put("MineIndustries", 150.0);
        stockPrices.put("BuildCo", 50.0);

        // 株価のランダム変動（5分ごと）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String company : stockPrices.keySet()) {
                    double price = stockPrices.get(company);
                    double change = (Math.random() * 0.2 - 0.1) * price; // ±10%
                    stockPrices.put(company, Math.max(1, price + change));
                }
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "📈 株価が更新されました！");
            }
        }.runTaskTimer(this, 6000, 6000);

        // 固定資産税の自動徴収（10分ごと）
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : balances.keySet()) {
                    int tax = fixedTax;
                    int balance = balances.get(uuid);
                    balance -= tax;
                    balances.put(uuid, balance);
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "固定資産税 " + tax + " が徴収されました！");
                    }
                }
            }
        }.runTaskTimer(this, 12000, 12000);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        switch (cmd.getName().toLowerCase()) {

            // --- 残高確認 ---
            case "balance":
                int balance = balances.getOrDefault(uuid, 0);
                player.sendMessage(ChatColor.AQUA + "あなたの残高: " + balance);
                return true;

            // --- 送金 ---
            case "pay":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使い方: /pay <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "プレイヤーが見つかりません！");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[1]);
                    int bal = balances.getOrDefault(uuid, 0);
                    if (bal < amount) {
                        player.sendMessage(ChatColor.RED + "残高不足！");
                        return true;
                    }
                    balances.put(uuid, bal - amount);
                    balances.put(target.getUniqueId(), balances.getOrDefault(target.getUniqueId(), 0) + amount);
                    player.sendMessage(ChatColor.GREEN + target.getName() + " に " + amount + " コイン送金しました！");
                    target.sendMessage(ChatColor.GREEN + player.getName() + " から " + amount + " コイン受け取りました！");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "金額は数字で入力してください！");
                }
                return true;

            // --- 株 ---
            case "stock":
                if (args.length == 0) {
                    player.sendMessage(ChatColor.YELLOW + "使い方: /stock [buy|sell|info|portfolio]");
                    return true;
                }
                String action = args[0].toLowerCase();
                switch (action) {
                    case "info":
                        player.sendMessage(ChatColor.GOLD + "📊 株価一覧");
                        for (String company : stockPrices.keySet()) {
                            player.sendMessage(company + ": " + stockPrices.get(company));
                        }
                        break;
                    case "portfolio":
                        Map<String, Integer> stocks = playerStocks.getOrDefault(uuid, new HashMap<>());
                        player.sendMessage(ChatColor.GREEN + "あなたの株保有状況:");
                        for (String c : stocks.keySet()) {
                            player.sendMessage(c + ": " + stocks.get(c) + " 株");
                        }
                        break;
                    case "buy":
                    case "sell":
                        player.sendMessage(ChatColor.YELLOW + "株の売買はあとで追加できるよ");
                        break;
                }
                return true;

            // --- 税金 ---
            case "tax":
                if (args.length == 0) {
                    player.sendMessage(ChatColor.YELLOW + "使い方: /tax [info|pay]");
                    return true;
                }
                if (args[0].equalsIgnoreCase("info")) {
                    player.sendMessage(ChatColor.AQUA + "固定資産税: " + fixedTax);
                } else if (args[0].equalsIgnoreCase("pay")) {
                    int bal = balances.getOrDefault(uuid, 0);
                    if (bal >= fixedTax) {
                        balances.put(uuid, bal - fixedTax);
                        player.sendMessage(ChatColor.GREEN + "固定資産税 " + fixedTax + " を支払いました！");
                    } else {
                        player.sendMessage(ChatColor.RED + "残高不足で支払えません！");
                    }
                }
                return true;

            // --- 管理者用 ---
            case "economyadmin":
                if (!player.isOp()) {
                    player.sendMessage(ChatColor.RED + "あなたには権限がありません！");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "使い方: /economyadmin [stock|tax|balance] <設定>");
                    return true;
                }
                String targetType = args[0].toLowerCase();
                switch (targetType) {
                    case "stock":
                        if (args.length < 3) break;
                        String company = args[1];
                        try {
                            double price = Double.parseDouble(args[2]);
                            stockPrices.put(company, price);
                            player.sendMessage(ChatColor.GREEN + company + " の株価を " + price + " に設定しました！");
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "株価は数字で指定してください");
                        }
                        break;
                    case "tax":
                        try {
                            int tax = Integer.parseInt(args[1]);
                            fixedTax = tax;
                            player.sendMessage(ChatColor.GREEN + "固定資産税を " + tax + " に設定しました！");
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "税金は数字で指定してください");
                        }
                        break;
                    case "balance":
                        if (args.length < 3) break;
                        Player targetPlayer = Bukkit.getPlayer(args[1]);
                        if (targetPlayer == null) {
                            player.sendMessage(ChatColor.RED + "プレイヤーが見つかりません");
                            break;
                        }
                        try {
                            int amount = Integer.parseInt(args[2]);
                            balances.put(targetPlayer.getUniqueId(), amount);
                            player.sendMessage(ChatColor.GREEN + targetPlayer.getName() + " の残高を " + amount + " に設定しました！");
                        } catch (NumberFormatException e) {
                            player.sendMessage(ChatColor.RED + "金額は数字で指定してください");
                        }
                        break;
                }
                return true;
        }

        return false;
    }
}
