package com.infuse.spark.infuses;

import com.infuse.spark.EffectGroup;
import com.infuse.spark.InfuseItems.InfuseItem;

public abstract class BaseInfuse implements Infuse {
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
}
