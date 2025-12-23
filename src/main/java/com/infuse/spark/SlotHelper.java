package com.infuse.spark;

public final class SlotHelper {
    private SlotHelper() {
    }

    public static int getSlotEffect(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimary() : data.getSupport();
    }

    public static EffectGroup getSlotGroup(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimaryGroup() : data.getSupportGroup();
    }

    public static boolean isSlotActive(PlayerData data, int slot) {
        return slot == 1 ? data.isPrimaryActive() : data.isSupportActive();
    }

    public static String getSlotShow(PlayerData data, int slot) {
        return slot == 1 ? data.getPrimaryShow() : data.getSupportShow();
    }

    public static void setSlotEffect(PlayerData data, int slot, EffectGroup group, int effectId) {
        if (slot == 1) {
            data.setPrimary(effectId);
            data.setPrimaryGroup(group);
        } else {
            data.setSupport(effectId);
            data.setSupportGroup(group);
        }
    }

    public static void setSlotActive(PlayerData data, int slot, boolean active) {
        if (slot == 1) {
            data.setPrimaryActive(active);
        } else {
            data.setSupportActive(active);
        }
    }

    public static void setSlotCooldown(PlayerData data, int slot, int minutes, int seconds) {
        if (slot == 1) {
            data.setPrimaryMinutes(minutes);
            data.setPrimarySeconds(seconds);
        } else {
            data.setSupportMinutes(minutes);
            data.setSupportSeconds(seconds);
        }
    }

    public static void setSlotSeconds(PlayerData data, int slot, int seconds) {
        if (slot == 1) {
            data.setPrimarySeconds(seconds);
        } else {
            data.setSupportSeconds(seconds);
        }
    }

    public static void setSlotMinutes(PlayerData data, int slot, int minutes) {
        if (slot == 1) {
            data.setPrimaryMinutes(minutes);
        } else {
            data.setSupportMinutes(minutes);
        }
    }

    public static boolean hasEffect(PlayerData data, EffectGroup group, int effectId) {
        return (data.getPrimaryGroup() == group && data.getPrimary() == effectId)
            || (data.getSupportGroup() == group && data.getSupport() == effectId);
    }

    public static boolean isEffectActive(PlayerData data, EffectGroup group, int effectId) {
        return (data.getPrimaryGroup() == group && data.getPrimary() == effectId && data.isPrimaryActive())
            || (data.getSupportGroup() == group && data.getSupport() == effectId && data.isSupportActive());
    }

    public static int getActiveSlotForEffect(PlayerData data, EffectGroup group, int effectId) {
        if (data.getPrimaryGroup() == group && data.getPrimary() == effectId && data.isPrimaryActive()) {
            return 1;
        }
        if (data.getSupportGroup() == group && data.getSupport() == effectId && data.isSupportActive()) {
            return 2;
        }
        return 0;
    }

    public static void setSlotActionBar(PlayerData data, int slot, String value) {
        if (slot == 1) {
            data.setActionBarPrimary(value);
        } else {
            data.setActionBarSupport(value);
        }
    }
}
