package com.infuse.spark;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private int primary;
    private int support;
    private int controlSet;
    private boolean primaryActive;
    private boolean supportActive;
    private int primaryMinutes;
    private int primarySeconds;
    private int supportMinutes;
    private int supportSeconds;
    private boolean joined;
    private final Set<UUID> trusted = new HashSet<>();

    private String primaryShow = "";
    private String supportShow = "";
    private String primaryShowActive = "";
    private String supportShowActive = "";
    private String actionBarPrimary = "";
    private String actionBarSupport = "";
    private String primaryColorCode = "&f&l";

    private boolean strengthSparkActive;
    private boolean frostSparkActive;
    private boolean fireSparkActive;
    private boolean pigSparkPrimed;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getPrimary() {
        return primary;
    }

    public void setPrimary(int primary) {
        this.primary = primary;
    }

    public int getSupport() {
        return support;
    }

    public void setSupport(int support) {
        this.support = support;
    }

    public int getControlSet() {
        return controlSet;
    }

    public void setControlSet(int controlSet) {
        this.controlSet = controlSet;
    }

    public boolean isPrimaryActive() {
        return primaryActive;
    }

    public void setPrimaryActive(boolean primaryActive) {
        this.primaryActive = primaryActive;
    }

    public boolean isSupportActive() {
        return supportActive;
    }

    public void setSupportActive(boolean supportActive) {
        this.supportActive = supportActive;
    }

    public int getPrimaryMinutes() {
        return primaryMinutes;
    }

    public void setPrimaryMinutes(int primaryMinutes) {
        this.primaryMinutes = primaryMinutes;
    }

    public int getPrimarySeconds() {
        return primarySeconds;
    }

    public void setPrimarySeconds(int primarySeconds) {
        this.primarySeconds = primarySeconds;
    }

    public int getSupportMinutes() {
        return supportMinutes;
    }

    public void setSupportMinutes(int supportMinutes) {
        this.supportMinutes = supportMinutes;
    }

    public int getSupportSeconds() {
        return supportSeconds;
    }

    public void setSupportSeconds(int supportSeconds) {
        this.supportSeconds = supportSeconds;
    }

    public boolean isJoined() {
        return joined;
    }

    public void setJoined(boolean joined) {
        this.joined = joined;
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    public String getPrimaryShow() {
        return primaryShow;
    }

    public void setPrimaryShow(String primaryShow) {
        this.primaryShow = primaryShow;
    }

    public String getSupportShow() {
        return supportShow;
    }

    public void setSupportShow(String supportShow) {
        this.supportShow = supportShow;
    }

    public String getPrimaryShowActive() {
        return primaryShowActive;
    }

    public void setPrimaryShowActive(String primaryShowActive) {
        this.primaryShowActive = primaryShowActive;
    }

    public String getSupportShowActive() {
        return supportShowActive;
    }

    public void setSupportShowActive(String supportShowActive) {
        this.supportShowActive = supportShowActive;
    }

    public String getActionBarPrimary() {
        return actionBarPrimary;
    }

    public void setActionBarPrimary(String actionBarPrimary) {
        this.actionBarPrimary = actionBarPrimary;
    }

    public String getActionBarSupport() {
        return actionBarSupport;
    }

    public void setActionBarSupport(String actionBarSupport) {
        this.actionBarSupport = actionBarSupport;
    }

    public String getPrimaryColorCode() {
        return primaryColorCode;
    }

    public void setPrimaryColorCode(String primaryColorCode) {
        this.primaryColorCode = primaryColorCode;
    }

    public boolean isStrengthSparkActive() {
        return strengthSparkActive;
    }

    public void setStrengthSparkActive(boolean strengthSparkActive) {
        this.strengthSparkActive = strengthSparkActive;
    }

    public boolean isFrostSparkActive() {
        return frostSparkActive;
    }

    public void setFrostSparkActive(boolean frostSparkActive) {
        this.frostSparkActive = frostSparkActive;
    }

    public boolean isFireSparkActive() {
        return fireSparkActive;
    }

    public void setFireSparkActive(boolean fireSparkActive) {
        this.fireSparkActive = fireSparkActive;
    }

    public boolean isPigSparkPrimed() {
        return pigSparkPrimed;
    }

    public void setPigSparkPrimed(boolean pigSparkPrimed) {
        this.pigSparkPrimed = pigSparkPrimed;
    }
}
