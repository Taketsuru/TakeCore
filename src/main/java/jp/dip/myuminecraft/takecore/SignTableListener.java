package jp.dip.myuminecraft.takecore;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SignTableListener {
    public boolean mayCreate(Player player, Location location,
            Location attachedLocation, String[] lines);

    /**
     * player is null when create is called because a sign is found in a loaded
     * chunk, not because a player creates a sign.
     */
    public ManagedSign create(Player player, Location location,
            Location attachedLocation, String[] lines);

    /**
     * player is null when create is called because a sign is unloaded, not
     * because a player breaks a sign or the attached block of a sign.
     */
    public void destroy(Player player, ManagedSign sign);
}
