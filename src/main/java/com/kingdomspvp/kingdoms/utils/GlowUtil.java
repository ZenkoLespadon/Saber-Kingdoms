package com.kingdomspvp.kingdoms.utils;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import org.bukkit.entity.Player;

public final class GlowUtil {

    private static final ProtocolManager PM = ProtocolLibrary.getProtocolManager();

    private GlowUtil() {}

    public static void setGlowing(Player viewer, Player target, boolean glowing) {
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) return;

        try {
            PacketContainer packet = PM.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, target.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher();
            WrappedDataWatcherObject obj =
                    new WrappedDataWatcherObject(0, WrappedDataWatcher.Registry.get(Byte.class));

            byte mask = 0;
            if (glowing) mask |= 0x40; // bit glow

            watcher.setObject(obj, mask);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());

            PM.sendServerPacket(viewer, packet);
        } catch (Exception ignored) {}
    }
}
