package jp.mydns.myuminecraft.takecore;

public class BlockLocation implements Comparable<BlockLocation> {

    private String           world;
    private BlockCoordinates coordinates;

    public BlockLocation(String world, int x, int y, int z) {
        this.world = world;
        coordinates = new BlockCoordinates(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockLocation)) {
            return false;
        }
        BlockLocation cobj = (BlockLocation) obj;
        return coordinates.equals(cobj.coordinates) && world.equals(cobj.world);
    }

    @Override
    public int hashCode() {
        return world.hashCode() ^ coordinates.hashCode();
    }

    @Override
    public int compareTo(BlockLocation obj) {
        int wcmp = world.compareTo(obj.getWorld());
        if (wcmp != 0) {
            return wcmp;
        }
        
        return coordinates.compareTo(obj.coordinates);
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return coordinates.getX();
    }

    public int getY() {
        return coordinates.getY();
    }

    public int getZ() {
        return coordinates.getZ();
    }
}
