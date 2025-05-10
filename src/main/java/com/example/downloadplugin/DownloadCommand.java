package com.example.downloadplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DownloadCommand extends Command {

    private final DownloadPlugin plugin;
    private final FileManager fileManager;

    public DownloadCommand(DownloadPlugin plugin, FileManager fileManager, String name, String description, String usageMessage, List<String> aliases) {
        super(name); // 명령어 이름
        this.setDescription(description);
        this.setUsage(usageMessage);
        this.setAliases(aliases);
        this.setPermission("download.command.download"); // 기본 명령어 권한 설정

        this.plugin = plugin;
        this.fileManager = fileManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!testPermission(sender)) { // Command 클래스의 testPermission 사용
            // testPermission이 false를 반환하면, plugin.yml에 정의된 permission-message가 전송되거나 기본 메시지가 전송됩니다.
            // sender.sendMessage(ChatColor.RED + "You do not have permission to use this command."); // 직접 메시지 전송도 가능
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check":
                if (!sender.hasPermission("download.command.check")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this subcommand.");
                    return true;
                }
                return handleCheckCommand(sender);
            case "at":
                if (!sender.hasPermission("download.command.at")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use this subcommand.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /download at <path> <url>");
                    return true;
                }
                String path = args[1];
                String urlString = args[2];
                return handleAtCommand(sender, path, urlString);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- Download Command Usage ---");
        sender.sendMessage(ChatColor.YELLOW + "/download check" + ChatColor.GRAY + " - Lists downloaded files.");
        sender.sendMessage(ChatColor.YELLOW + "/download at <path> <url>" + ChatColor.GRAY + " - Downloads a file.");
        sender.sendMessage(ChatColor.GRAY + "Aliases: " + String.join(", ", this.getAliases()));
    }

    private boolean handleCheckCommand(CommandSender sender) {
        List<String> downloadedFiles = fileManager.getDownloadedFiles();
        if (downloadedFiles.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No files have been downloaded yet.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + "--- Downloaded Files ---");
        for (String fileInfo : downloadedFiles) {
            sender.sendMessage(ChatColor.AQUA + "- " + fileInfo);
        }
        return true;
    }

    private boolean handleAtCommand(CommandSender sender, String relativePath, String urlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            sender.sendMessage(ChatColor.RED + "Invalid URL: " + e.getMessage());
            return true;
        }

        File targetFile = new File(plugin.getServer().getWorldContainer(), relativePath);
        File serverRoot = plugin.getServer().getWorldContainer().getAbsoluteFile();

        try {
            if (!targetFile.getCanonicalPath().startsWith(serverRoot.getCanonicalPath())) {
                sender.sendMessage(ChatColor.RED + "Error: Path traversal attempt detected. Please use a relative path within the server directory.");
                return true;
            }
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error validating path: " + e.getMessage());
            return true;
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                sender.sendMessage(ChatColor.RED + "Could not create directory: " + parentDir.getPath());
                return true;
            }
        }
        if (targetFile.exists()) {
            sender.sendMessage(ChatColor.YELLOW + "Warning: File already exists at " + targetFile.getPath() + ". It will be overwritten.");
        }

        sender.sendMessage(ChatColor.GREEN + "Starting download of " + urlString + " to " + targetFile.getPath() + "...");

        new BukkitRunnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setRequestProperty("User-Agent", "MinecraftDownloadPlugin/1.0");

                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                             FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {

                            byte[] dataBuffer = new byte[1024];
                            int bytesRead;
                            long totalBytesRead = 0;
                            long fileSize = connection.getContentLengthLong();
                            long lastLoggedProgress = 0;

                            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                                fileOutputStream.write(dataBuffer, 0, bytesRead);
                                totalBytesRead += bytesRead;
                                if (fileSize > 0) {
                                    long currentProgress = (totalBytesRead * 100) / fileSize;
                                    if (currentProgress > lastLoggedProgress + 9 || currentProgress == 100) {
                                        plugin.getLogger().info("Downloading " + targetFile.getName() + ": " + currentProgress + "%");
                                        lastLoggedProgress = currentProgress;
                                    }
                                }
                            }
                        }
                        sender.sendMessage(ChatColor.GREEN + "Successfully downloaded " + targetFile.getName() + " (" + formatFileSize(targetFile.length()) +") to " + targetFile.getPath());
                        fileManager.addDownloadedFile(targetFile.getPath(), urlString);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Download failed. Server responded with code: " + responseCode);
                        plugin.getLogger().warning("Download failed for " + urlString + ". Response code: " + responseCode + " " + connection.getResponseMessage());
                    }

                } catch (IOException e) {
                    sender.sendMessage(ChatColor.RED + "Download failed: " + e.getMessage());
                    plugin.getLogger().log(Level.SEVERE, "Error downloading file from " + urlString, e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        List<String> completions = new ArrayList<>();
        if (!testPermissionSilent(sender)) { // 권한이 없으면 자동완성 제공 안함
            return completions;
        }

        if (args.length == 1) {
            if (sender.hasPermission("download.command.check")) {
                completions.add("check");
            }
            if (sender.hasPermission("download.command.at")) {
                completions.add("at");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("at")) {
            if (sender.hasPermission("download.command.at")) {
                completions.add("<path>"); // 예시: plugins/MyPlugin/config.yml
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("at")) {
            if (sender.hasPermission("download.command.at")) {
                completions.add("<url>"); // 예시: https://example.com/file.zip
            }
        }

        // 현재 입력값과 일치하는 것만 필터링
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(currentArg))
                .collect(Collectors.toList());
    }
}