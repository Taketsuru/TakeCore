package jp.dip.myuminecraft.takecore;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface SignTableListener {
    public boolean mayCreate(Player player, Location location,
            String[] signText);

    public ManagedSign create(Location location, String[] signText);

    public void destroy(ManagedSign sign);
}
