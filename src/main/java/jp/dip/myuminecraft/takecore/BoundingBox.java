package jp.dip.myuminecraft.takecore;

import java.util.List;

public class BoundingBox {
    private int minX, maxX;
    private int minY, maxY;
    private int minZ, maxZ;

    public BoundingBox() {
        clear();
    }

    public void clear() {
        minX = maxX = minY = maxY = minZ = maxZ = 0;
    }

    public void set(List<BlockCoordinates> coordsList) {
        if (coordsList.isEmpty()) {
            clear();
            return;
        }

        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (BlockCoordinates coords : coordsList) {
            add(coords.getX(), coords.getY(), coords.getZ());
        }
    }

    public void add(int x, int y, int z) {
        if (x < minX) {
            minX = x;
        }
        if (maxX <= x) {
            maxX = x + 1;
        }
        if (y < minY) {
            minY = y;
        }
        if (maxY <= y) {
            maxY = y + 1;
        }
        if (z < minZ) {
            minZ = z;
        }
        if (maxZ <= z) {
            maxZ = z + 1;
        }
    }

    public int getMinX() {
        return minX;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxZ() {
        return maxZ;
    }
}
