package jp.dip.myuminecraft.takecore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.Messages;

/**
 * SignTable knows all the non-blank wall signs and sign posts in the loaded
 * chunks.
 * 
 * @author Taketsuru
 *
 */
public class SignTable implements Listener {

    static class ChunkId {
        String worldName;
        int    x;
        int    z;

        public ChunkId(Chunk chunk) {
            worldName = chunk.getWorld().getName();
            x = chunk.getX();
            z = chunk.getZ();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof ChunkId)) {
                return false;
            }

            ChunkId chunk = (ChunkId) obj;
            return x == chunk.x && z == chunk.z
                    && worldName.equals(chunk.worldName);
        }

        @Override
        public int hashCode() {
            return worldName.hashCode() ^ x ^ z;
        }

        public Chunk getChunk() {
            return Bukkit.getWorld(worldName).getChunkAt(x, z);
        }
    }

    static class ChunkLoad {
        ChunkId chunkId;
        long    releaseTime;

        ChunkLoad(ChunkId chunkId, long releaseTime) {
            this.chunkId = chunkId;
            this.releaseTime = releaseTime;
        }
    };

    static final long               ticksPerSec     = 20;
    static final long               tickInMillis    = 1000 / ticksPerSec;
    static final long               chunkLoadDelay  = 1000;
    static final BlockFace[]        attachableFaces = { BlockFace.SOUTH,
    BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST, BlockFace.UP };

    JavaPlugin                      plugin;
    Logger                          logger;
    Messages                        messages;
    List<SignTableListener>         listeners;
    Map<Location, ManagedSign>      managedSigns;
    Map<ChunkId, List<ManagedSign>> managedSignsInChunk;
    Map<Location, Integer>          attachedSignsCount;
    BukkitRunnable                  nextLoadTask;
    Deque<ChunkLoad>                chunkLoadQueue;

    public SignTable(JavaPlugin plugin, Logger logger, Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        listeners = new LinkedList<SignTableListener>();
        managedSigns = new HashMap<Location, ManagedSign>();
        attachedSignsCount = new HashMap<Location, Integer>();
        managedSignsInChunk = new HashMap<ChunkId, List<ManagedSign>>();
        chunkLoadQueue = new ArrayDeque<ChunkLoad>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable() {
        for (ManagedSign sign : managedSigns.values()) {
            sign.getOwner().destroy(null, sign);
        }
        managedSigns.clear();
        managedSignsInChunk.clear();
        attachedSignsCount.clear();
        chunkLoadQueue.clear();
        if (nextLoadTask != null) {
            nextLoadTask.cancel();
            nextLoadTask = null;
        }
    }

    public void addListener(SignTableListener listener) {
        listeners.add(listener);
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                findAllSignsInChunk(new ChunkId(chunk));
            }
        }
    }

    public void removeListener(SignTableListener listener) {
        Iterator<ManagedSign> iter = managedSigns.values().iterator();
        while (iter.hasNext()) {
            ManagedSign sign = iter.next();
            if (sign.getOwner() == listener) {
                iter.remove();
                removeFromManagedSignsInChunk(sign);
                decrementAttachedSignsCount(sign);
            }
        }

        listeners.remove(listener);
    }

    public ManagedSign getManagedSign(Location location) {
        return managedSigns.get(location);
    }

    @EventHandler(ignoreCancelled = true)
    public void shouldSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        Location attachedLocation = ManagedSign.getAttachedLocation(block);
        String[] lines = event.getLines();
        if (!isBlank(lines) && !mayCreate(event.getPlayer(), location,
                attachedLocation, lines)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void didSignChange(SignChangeEvent event) {
        String[] lines = event.getLines();
        if (isBlank(lines)) {
            return;
        }

        Block block = event.getBlock();
        Location location = block.getLocation();

        // ChestShop breaks the sign in SignChangeEvent handler.
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        Location attachedLocation = ManagedSign.getAttachedLocation(block);
        ManagedSign sign = create(event.getPlayer(), location,
                attachedLocation, lines);
        if (sign == null) {
            return;
        }

        managedSigns.put(location, sign);

        ChunkId chunkId = new ChunkId(block.getChunk());
        List<ManagedSign> list = managedSignsInChunk.get(chunkId);
        if (list == null) {
            list = new ArrayList<ManagedSign>();
            managedSignsInChunk.put(chunkId, list);
        }
        list.add(sign);

        Integer currentCount = attachedSignsCount.get(attachedLocation);
        attachedSignsCount.put(attachedLocation,
                currentCount == null ? 1 : currentCount + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void didBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        ManagedSign sign = managedSigns.remove(location);
        if (sign != null) {
            sign.getOwner().destroy(event.getPlayer(), sign);
            removeFromManagedSignsInChunk(sign);
            decrementAttachedSignsCount(sign);
        }

        Location attachedLocation = block.getLocation();
        if (attachedSignsCount.remove(attachedLocation) != null) {
            for (BlockFace face : attachableFaces) {
                location = attachedLocation.clone();
                location.add(face.getModX(), face.getModY(), face.getModZ());
                sign = managedSigns.remove(location);
                if (sign != null) {
                    sign.getOwner().destroy(event.getPlayer(), sign);
                    removeFromManagedSignsInChunk(sign);
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoadEvent(ChunkLoadEvent event) {
        ChunkLoad chunkLoad = new ChunkLoad(new ChunkId(event.getChunk()),
                System.currentTimeMillis() + chunkLoadDelay);
        chunkLoadQueue.addLast(chunkLoad);
        if (chunkLoadQueue.size() == 1) {
            scheduleNextChunkLoad();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnloadEvent(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkId chunkId = new ChunkId(chunk);

        Iterator<ChunkLoad> iter = chunkLoadQueue.iterator();
        boolean isFirst = true;
        while (iter.hasNext()) {
            ChunkLoad chunkLoad = iter.next();
            if (chunkLoad.chunkId.equals(chunkId)) {
                iter.remove();
                if (isFirst) {
                    scheduleNextChunkLoad();
                }
                return;
            }
            isFirst = false;
        }

        List<ManagedSign> list = managedSignsInChunk.remove(chunkId);
        if (list == null) {
            return;
        }

        for (ManagedSign sign : list) {
            sign.getOwner().destroy(null, sign);
            managedSigns.remove(sign.getLocation());
            decrementAttachedSignsCount(sign);
        }
    }

    static boolean isBlank(String[] lines) {
        int n = lines.length;
        for (int i = 0; i < n; ++i) {
            if (lines[i] != null && lines[i].length() != 0) {
                return false;
            }
        }

        return true;
    }

    void removeFromManagedSignsInChunk(ManagedSign sign) {
        ChunkId chunkId = new ChunkId(sign.getLocation().getChunk());
        List<ManagedSign> list = managedSignsInChunk.get(chunkId);
        if (1 < list.size()) {
            list.remove(sign);
        } else {
            managedSignsInChunk.remove(chunkId);
        }
    }

    void decrementAttachedSignsCount(ManagedSign sign) {
        Location attachedLocation = sign.getAttachedLocation();
        int count = attachedSignsCount.get(attachedLocation);
        if (count == 1) {
            attachedSignsCount.remove(attachedLocation);
        } else {
            attachedSignsCount.put(attachedLocation, count - 1);
        }
    }

    void findAllSignsInChunk(ChunkId chunkId) {
        Chunk chunk = chunkId.getChunk();
        Set<Location> existing = new HashSet<Location>();
        List<ManagedSign> signs = managedSignsInChunk.get(chunkId);
        boolean noManagedSignsInChunk = signs == null;
        if (noManagedSignsInChunk) {
            signs = new ArrayList<ManagedSign>();
        } else {
            for (ManagedSign sign : signs) {
                existing.add(sign.getLocation());
            }
        }

        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Sign)) {
                continue;
            }

            String[] lines = ((Sign) state).getLines();
            if (isBlank(lines)) {
                continue;
            }

            Block block = state.getBlock();
            Location location = block.getLocation();
            if (existing.contains(location)) {
                continue;
            }

            Location attachedLocation = ManagedSign.getAttachedLocation(block);
            ManagedSign sign = create(null, location, attachedLocation, lines);
            if (sign != null) {
                state.update();
                signs.add(sign);
                managedSigns.put(location, sign);
                Integer currentCount = attachedSignsCount
                        .get(attachedLocation);
                attachedSignsCount.put(attachedLocation,
                        currentCount == null ? 1 : currentCount + 1);
            }
        }

        if (noManagedSignsInChunk && !signs.isEmpty()) {
            managedSignsInChunk.put(chunkId, signs);
        }
    }

    boolean mayCreate(Player player, Location location,
            Location attachedLocation, String[] signText) {
        for (SignTableListener listener : listeners) {
            if (!listener.mayCreate(player, location, attachedLocation,
                    signText)) {
                return false;
            }
        }
        return true;
    }

    ManagedSign create(Player player, Location location,
            Location attachedLocation, String[] lines) {
        for (SignTableListener listener : listeners) {
            ManagedSign instance = listener.create(player, location,
                    attachedLocation, lines);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    void scheduleNextChunkLoad() {
        if (nextLoadTask != null) {
            nextLoadTask.cancel();
            nextLoadTask = null;
        }

        if (chunkLoadQueue.isEmpty()) {
            return;
        }

        nextLoadTask = new BukkitRunnable() {
            @Override
            public void run() {
                nextLoadTask = null;
                while (!chunkLoadQueue.isEmpty()) {
                    ChunkLoad chunkLoad = chunkLoadQueue.getFirst();
                    long diff = chunkLoad.releaseTime
                            - System.currentTimeMillis();
                    if (tickInMillis < diff) {
                        scheduleNextChunkLoad();
                        return;
                    }
                    chunkLoadQueue.removeFirst();
                    findAllSignsInChunk(chunkLoad.chunkId);
                }
            }
        };

        nextLoadTask
                .runTaskLater(plugin,
                        Math.max(1,
                                (chunkLoadQueue.getFirst().releaseTime
                                        - System.currentTimeMillis())
                                        / tickInMillis));
    }

}
