package com.infuse.spark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class InfuseConfigManager {
    private final Logger logger;
    private final AtomicReference<ConfigSnapshot> snapshot = new AtomicReference<>(ConfigSnapshot.empty());

    public InfuseConfigManager(Logger logger) {
        this.logger = logger;
    }

    public void reload(FileConfiguration configuration) {
        snapshot.set(ConfigSnapshot.from(configuration, logger));
    }

    public int getInt(String infuseKey, String section, String key, int defaultValue) {
        return snapshot.get().getInt(infuseKey, section, key, defaultValue);
    }

    public double getDouble(String infuseKey, String section, String key, double defaultValue) {
        return snapshot.get().getDouble(infuseKey, section, key, defaultValue);
    }

    public boolean getBoolean(String infuseKey, String section, String key, boolean defaultValue) {
        return snapshot.get().getBoolean(infuseKey, section, key, defaultValue);
    }

    public String getString(String infuseKey, String section, String key, String defaultValue) {
        return snapshot.get().getString(infuseKey, section, key, defaultValue);
    }

    public List<String> getStringList(String infuseKey, String section, String key, List<String> defaultValue) {
        return snapshot.get().getStringList(infuseKey, section, key, defaultValue);
    }

    private static final class ConfigSnapshot {
        private final Map<String, Map<String, Object>> passiveValues;
        private final Map<String, Map<String, Object>> sparkValues;
        private final Logger logger;

        private ConfigSnapshot(Map<String, Map<String, Object>> passiveValues, Map<String, Map<String, Object>> sparkValues, Logger logger) {
            this.passiveValues = passiveValues;
            this.sparkValues = sparkValues;
            this.logger = logger;
        }

        private static ConfigSnapshot empty() {
            return new ConfigSnapshot(Map.of(), Map.of(), Logger.getLogger(InfuseConfigManager.class.getName()));
        }

        private static ConfigSnapshot from(FileConfiguration configuration, Logger logger) {
            Map<String, Map<String, Object>> passiveValues = new HashMap<>();
            Map<String, Map<String, Object>> sparkValues = new HashMap<>();
            ConfigurationSection infusesSection = configuration.getConfigurationSection("infuses");
            if (infusesSection != null) {
                for (String infuseKey : infusesSection.getKeys(false)) {
                    String normalized = infuseKey.toLowerCase(Locale.ROOT);
                    ConfigurationSection infuseSection = infusesSection.getConfigurationSection(infuseKey);
                    if (infuseSection == null) {
                        continue;
                    }
                    passiveValues.put(normalized, extractValues(infuseSection.getConfigurationSection("passive")));
                    sparkValues.put(normalized, extractValues(infuseSection.getConfigurationSection("spark")));
                }
            }
            return new ConfigSnapshot(passiveValues, sparkValues, logger);
        }

        private static Map<String, Object> extractValues(ConfigurationSection section) {
            if (section == null) {
                return Map.of();
            }
            return new HashMap<>(section.getValues(true));
        }

        private Map<String, Object> getSectionMap(String infuseKey, String section) {
            String normalized = infuseKey == null ? "" : infuseKey.toLowerCase(Locale.ROOT);
            return switch (section) {
                case "passive" -> passiveValues.getOrDefault(normalized, Map.of());
                case "spark" -> sparkValues.getOrDefault(normalized, Map.of());
                default -> Map.of();
            };
        }

        private Object getValue(String infuseKey, String section, String key) {
            Map<String, Object> values = getSectionMap(infuseKey, section);
            if (values.isEmpty()) {
                return null;
            }
            return values.get(key);
        }

        private String buildPath(String infuseKey, String section, String key) {
            return "infuses." + infuseKey + "." + section + "." + key;
        }

        private void warn(String infuseKey, String section, String key, Object value, String expected) {
            String valueText = value == null ? "null" : value.toString();
            logger.warning("Invalid config value at " + buildPath(infuseKey, section, key) + " (" + valueText + "), expected " + expected + ". Using default.");
        }

        private int getInt(String infuseKey, String section, String key, int defaultValue) {
            Object value = getValue(infuseKey, section, key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ex) {
                    warn(infuseKey, section, key, value, "integer");
                    return defaultValue;
                }
            }
            warn(infuseKey, section, key, value, "integer");
            return defaultValue;
        }

        private double getDouble(String infuseKey, String section, String key, double defaultValue) {
            Object value = getValue(infuseKey, section, key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text.trim());
                } catch (NumberFormatException ex) {
                    warn(infuseKey, section, key, value, "number");
                    return defaultValue;
                }
            }
            warn(infuseKey, section, key, value, "number");
            return defaultValue;
        }

        private boolean getBoolean(String infuseKey, String section, String key, boolean defaultValue) {
            Object value = getValue(infuseKey, section, key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof Boolean flag) {
                return flag;
            }
            if (value instanceof String text) {
                String normalized = text.trim().toLowerCase(Locale.ROOT);
                if ("true".equals(normalized) || "false".equals(normalized)) {
                    return Boolean.parseBoolean(normalized);
                }
            }
            warn(infuseKey, section, key, value, "boolean");
            return defaultValue;
        }

        private String getString(String infuseKey, String section, String key, String defaultValue) {
            Object value = getValue(infuseKey, section, key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof String text) {
                return text;
            }
            warn(infuseKey, section, key, value, "string");
            return defaultValue;
        }

        private List<String> getStringList(String infuseKey, String section, String key, List<String> defaultValue) {
            Object value = getValue(infuseKey, section, key);
            if (value == null) {
                return defaultValue;
            }
            if (value instanceof List<?> list) {
                List<String> result = new ArrayList<>();
                for (Object entry : list) {
                    if (entry == null) {
                        continue;
                    }
                    result.add(Objects.toString(entry));
                }
                return Collections.unmodifiableList(result);
            }
            if (value instanceof String text) {
                return List.of(text);
            }
            warn(infuseKey, section, key, value, "list");
            return defaultValue;
        }
    }
}
