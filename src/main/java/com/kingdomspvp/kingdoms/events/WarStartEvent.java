package com.kingdomspvp.kingdoms.events;


import com.kingdomspvp.kingdoms.model.War;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class WarStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final War war;
    public WarStartEvent(War w) { this.war = w; }
    public War getWar() { return war; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
