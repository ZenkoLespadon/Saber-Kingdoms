package com.kingdomspvp.kingdoms.events;

import com.kingdomspvp.kingdoms.model.War;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class WarEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final War war;

    public WarEndEvent(War war) {
        this.war = war;
    }

    /**
     * @return the War that just ended
     */
    public War getWar() {
        return war;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Required by Bukkit to fetch the handler list for this event.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
