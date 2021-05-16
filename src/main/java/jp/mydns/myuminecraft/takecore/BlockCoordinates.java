package jp.mydns.myuminecraft.takecore;

public class BlockCoordinates implements Comparable<BlockCoordinates> {
    private int x;
    private int y;
    private int z;

    public BlockCoordinates(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BlockCoordinates)) {
            return false;
        }
        BlockCoordinates cobj = (BlockCoordinates) obj;
        return x == cobj.x && y == cobj.y && z == cobj.z;
    }

    @Override
    public int hashCode() {
        int result = 0;
        int accx = x;
        int accy = y;
        int accz = z;
        for (int i = 0; i < 3; ++i) {
            int acc = 0;
            for (int j = 0; j < 10; ++j) {
                acc = (acc << 3) | ((accx & 1) << 2) | ((accy & 1) << 1)
                        | (accz & 1);
                accx >>= 1;
                accy >>= 1;
                accz >>= 1;
            }

            acc = ((acc >> 16) ^ acc) * 0x45d9f3b;
            acc = ((acc >> 16) ^ acc) * 0x45d9f3b;
            acc = ((acc >> 16) ^ acc);
            result ^= acc;
        }

        return result;
    }

    @Override
    public int compareTo(BlockCoordinates obj) {
        int regionZ1 = z >> (4 + 5);
        int regionZ2 = obj.z >> (4 + 5);
        if (regionZ1 != regionZ2) {
            return regionZ1 - regionZ2;
        }

        int regionX1 = x >> (4 + 5);
        int regionX2 = obj.x >> (4 + 5);
        if (regionX1 != regionX2) {
            return regionX1 - regionX2;
        }

        int chunkZ1 = z >> 4;
        int chunkZ2 = obj.z >> 4;
        if (chunkZ1 != chunkZ2) {
            return chunkZ1 - chunkZ2;
        }

        int chunkX1 = x >> 4;
        int chunkX2 = obj.x >> 4;
        if (chunkX1 != chunkX2) {
            return chunkX1 - chunkX2;
        }

        if (y != obj.y) {
            return y - obj.y;
        }

        if (z != obj.z) {
            return z - obj.z;
        }

        return x - obj.x;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
