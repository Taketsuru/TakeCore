package jp.dip.myuminecraft.takecore;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SignTableListener {
    public boolean mayCreate(Player player, Location location,
            String[] lines);

    public ManagedSign create(Location location, Location attachedLocation,
            String[] lines);

    public void destroy(ManagedSign sign);
}
