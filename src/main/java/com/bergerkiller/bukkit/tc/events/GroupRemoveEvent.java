package com.bergerkiller.bukkit.tc.events;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import org.bukkit.event.HandlerList;

public class GroupRemoveEvent extends GroupEvent {
    private static final HandlerList handlers = new HandlerList();

    public GroupRemoveEvent(final MinecartGroup group) {
        super(group);
    }

    public static void call(final MinecartGroup group) {
        CommonUtil.callEvent(new GroupRemoveEvent(group));
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
}
