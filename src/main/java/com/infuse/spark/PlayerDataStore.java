package com.infuse.spark;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlayerDataStore {
    private final File dataFile;
    private final YamlConfiguration configuration;

    public PlayerDataStore(File dataFile) {
        this.dataFile = dataFile;
        this.configuration = YamlConfiguration.loadConfiguration(dataFile);
    }

    public PlayerData load(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        ConfigurationSection section = configuration.getConfigurationSection("players." + uuid);
        if (section == null) {
            data.setPrimary(0);
            data.setSupport(0);
            data.setControlSet(0);
            data.setPrimaryMinutes(0);
            data.setPrimarySeconds(0);
            data.setSupportMinutes(0);
            data.setSupportSeconds(0);
            data.setJoined(false);
            return data;
        }
        data.setPrimary(section.getInt("primary", 0));
        data.setSupport(section.getInt("support", 0));
        data.setControlSet(section.getInt("controlSet", 0));
        data.setPrimaryMinutes(section.getInt("primaryMinutes", 0));
        data.setPrimarySeconds(section.getInt("primarySeconds", 0));
        data.setSupportMinutes(section.getInt("supportMinutes", 0));
        data.setSupportSeconds(section.getInt("supportSeconds", 0));
        data.setJoined(section.getBoolean("joined", false));
        Set<UUID> trusted = section.getStringList("trusted").stream()
            .map(UUID::fromString)
            .collect(Collectors.toSet());
        data.getTrusted().addAll(trusted);
        return data;
    }

    public void save(PlayerData data) {
        String path = "players." + data.getUuid();
        configuration.set(path + ".primary", data.getPrimary());
        configuration.set(path + ".support", data.getSupport());
        configuration.set(path + ".controlSet", data.getControlSet());
        configuration.set(path + ".primaryMinutes", data.getPrimaryMinutes());
        configuration.set(path + ".primarySeconds", data.getPrimarySeconds());
        configuration.set(path + ".supportMinutes", data.getSupportMinutes());
        configuration.set(path + ".supportSeconds", data.getSupportSeconds());
        configuration.set(path + ".joined", data.isJoined());
        configuration.set(path + ".trusted", data.getTrusted().stream().map(UUID::toString).collect(Collectors.toList()));
    }

    public void saveToDisk() throws IOException {
        configuration.save(dataFile);
    }
}
