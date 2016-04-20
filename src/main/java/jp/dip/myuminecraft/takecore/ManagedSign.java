package jp.dip.myuminecraft.takecore;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Sign;

public class ManagedSign {

    Location          location;
    SignTableListener owner;

    public ManagedSign(Location location, SignTableListener owner) {
        this.location = location;
        this.owner = owner;
    }

    public Location getLocation() {
        return location;
    }

    public SignTableListener getOwner() {
        return owner;
    }

    public static Block getAttachedBlock(Block signBlock) {
        Sign data = (Sign) signBlock.getState().getData();
        return signBlock.getRelative(data.getAttachedFace());
    }
    
    public static Location getAttachedLocation(Block signBlock) {
        Sign data = (Sign) signBlock.getState().getData();
        BlockFace face = data.getAttachedFace();
        return signBlock.getLocation().add(face.getModX(), face.getModY(), face.getModZ());        
    }

    public Block getAttachedBlock() {
        return getAttachedBlock(getLocation().getBlock());
    }

    public Location getAttachedLocation() {
        return getAttachedBlock().getLocation();
    }

}
