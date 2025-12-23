package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;

public abstract class BaseInfuse implements Infuse {
    protected static final String PASSIVE_SECTION = "passive";
    protected static final String SPARK_SECTION = "spark";

    private final EffectGroup group;
    private final int effectId;
    private final String key;
    private final InfuseItem item;

    protected BaseInfuse(EffectGroup group, int effectId, String key, InfuseItem item) {
        this.group = group;
        this.effectId = effectId;
        this.key = key;
        this.item = item;
    }

    @Override
    public EffectGroup getGroup() {
        return group;
    }

    @Override
    public int getEffectId() {
        return effectId;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public InfuseItem getItem() {
        return item;
    }

    protected int getInt(InfuseContext context, String section, String key, int defaultValue) {
        return context.getConfigManager().getInt(this.key, section, key, defaultValue);
    }

    protected double getDouble(InfuseContext context, String section, String key, double defaultValue) {
        return context.getConfigManager().getDouble(this.key, section, key, defaultValue);
    }

    protected boolean getBoolean(InfuseContext context, String section, String key, boolean defaultValue) {
        return context.getConfigManager().getBoolean(this.key, section, key, defaultValue);
    }

    protected String getString(InfuseContext context, String section, String key, String defaultValue) {
        return context.getConfigManager().getString(this.key, section, key, defaultValue);
    }

    protected java.util.List<String> getStringList(InfuseContext context, String section, String key, java.util.List<String> defaultValue) {
        return context.getConfigManager().getStringList(this.key, section, key, defaultValue);
    }
}
