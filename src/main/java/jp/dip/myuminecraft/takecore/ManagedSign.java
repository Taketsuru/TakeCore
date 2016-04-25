package jp.dip.myuminecraft.takecore;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Sign;

public class ManagedSign {

    SignTableListener owner;
    Location          location;
    Location          attachedLocation;

    public ManagedSign(SignTableListener owner, Location location,
            Location attachedLocation) {
        this.owner = owner;
        this.location = location;
        this.attachedLocation = attachedLocation;
    }

    public SignTableListener getOwner() {
        return owner;
    }

    public Location getLocation() {
        return location;
    }

    public Location getAttachedLocation() {
        return attachedLocation;
    }

    public Block getAttachedBlock() {
        return getAttachedLocation().getBlock();
    }

    public static Block getAttachedBlock(Block signBlock) {
        Sign data = (Sign) signBlock.getState().getData();
        return signBlock.getRelative(data.getAttachedFace());
    }

    public static Location getAttachedLocation(Block signBlock) {
        Sign data = (Sign) signBlock.getState().getData();
        BlockFace face = data.getAttachedFace();
        return signBlock.getLocation().add(face.getModX(), face.getModY(),
                face.getModZ());
    }

}
