package com.example.downloadplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
// Objects 클래스는 더 이상 필요 없을 수 있습니다. (getCommand 호출이 사라짐)

public final class DownloadPlugin extends JavaPlugin {

    private FileManager fileManager;

    @Override
    public void onEnable() {
        getLogger().info("DownloadPlugin has been enabled!");

        saveDefaultConfig();
        fileManager = new FileManager(this);

        // 명령어 직접 등록
        DownloadCommand downloadCommand = new DownloadCommand(
                this,
                fileManager,
                "download",                                      // 명령어 이름
                "Downloads a file or lists downloaded files.", // 설명
                "/download <check|at <path> <url>>",           // 사용법
                Arrays.asList("dl", "filedownload")              // 별칭 목록
        );

        // CommandMap을 통해 명령어 등록
        try {
            final Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            commandMap.register(this.getDescription().getName().toLowerCase(), downloadCommand); // 플러그인 이름을 fallback prefix로 사용
            // 또는 commandMap.register("download", downloadCommand); // 명령어를 직접 prefix로 사용 (일반적)
        } catch (NoSuchFieldException | IllegalAccessException e) {
            getLogger().severe("Failed to register command: " + e.getMessage());
            e.printStackTrace();
        }
        // Paper 1.19.3+ 에서는 CommandManager 사용 가능 (더 권장됨)
        // getServer().getCommandManager().register(this, downloadCommand, "download", "dl", "filedownload");
        // 하지만 제공된 에러 로그에서는 CommandMap을 통한 접근이 더 일반적인 해결책이 될 수 있습니다.
        // Paper의 CommandManager API가 있다면 그것을 사용하는 것이 가장 좋습니다.
        // 현재는 CommandMap을 사용한 예시를 드립니다.
    }

    @Override
    public void onDisable() {
        if (fileManager != null) {
            fileManager.saveConfig();
        }
        getLogger().info("DownloadPlugin has been disabled!");
    }

    public FileManager getFileManager() {
        return fileManager;
    }
}