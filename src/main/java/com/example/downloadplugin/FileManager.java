package com.example.downloadplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class FileManager {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration config;

    private static final String DOWNLOADED_FILES_PATH = "downloaded-files";

    public FileManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        configFile = new File(plugin.getDataFolder(), "download_history.yml");
        if (!configFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create download_history.yml!", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public List<String> getDownloadedFiles() {
        return config.getStringList(DOWNLOADED_FILES_PATH);
    }

    public void addDownloadedFile(String filePath, String url) {
        List<String> files = getDownloadedFiles();
        if (files == null) {
            files = new ArrayList<>();
        }
        files.add("Path: " + filePath + ", URL: " + url);
        config.set(DOWNLOADED_FILES_PATH, files);
        saveConfig();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save download_history.yml!", e);
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
}