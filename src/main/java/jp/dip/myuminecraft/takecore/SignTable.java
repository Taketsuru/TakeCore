package jp.dip.myuminecraft.takecore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private class AttachedSignsIterator implements Iterator<ManagedSign> {

        ManagedSign nextSign;
        Block       attachedBlock;
        int         index;

        AttachedSignsIterator(Block attachedBlock, SignTableListener owner) {
            this.attachedBlock = attachedBlock;
            index = -1;
            find(owner);
        }

        @Override
        public boolean hasNext() {
            return nextSign != null;
        }

        @Override
        public ManagedSign next() {
            ManagedSign result = nextSign;
            ++index;
            find(result.getOwner());
            return result;
        }

        private void find(SignTableListener owner) {
            if (nextSign == null) {
                return;
            }

            for (int i = index + 1; i < attachableFaces.length; ++i) {
                Block signBlock = attachedBlock
                        .getRelative(attachableFaces[i]);
                Material material = signBlock.getType();
                if (material != Material.WALL_SIGN
                        && material != Material.SIGN_POST) {
                    continue;
                }

                ManagedSign sign = managedSigns.get(signBlock.getLocation());
                if (sign == null) {
                    continue;
                }

                if (sign.getOwner() != owner) {
                    continue;
                }

                nextSign = sign;
                index = i;
                return;
            }

            nextSign = null;
        }
    }

    static final BlockFace[]   attachableFaces = { BlockFace.SOUTH,
    BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST, BlockFace.UP };

    JavaPlugin                 plugin;
    Logger                     logger;
    Messages                   messages;
    List<SignTableListener>    listeners;
    Map<Location, ManagedSign> managedSigns;
    Map<Chunk, List<Location>> nonBlankSignLocations;
    Map<Location, Integer>     attachedSignsCount;

    public SignTable(JavaPlugin plugin, Logger logger, Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        listeners = new LinkedList<SignTableListener>();
        managedSigns = new HashMap<Location, ManagedSign>();
        nonBlankSignLocations = new HashMap<Chunk, List<Location>>();
        attachedSignsCount = new HashMap<Location, Integer>();

        int chunkCount = 0;
        int signCount = 0;
        for (World world : plugin.getServer().getWorlds()) {
            Chunk[] chunks = world.getLoadedChunks();
            chunkCount += chunks.length;
            for (int i = 0; i < chunks.length; ++i) {
                findAllSignsInChunk(chunks[i]);
                if (nonBlankSignLocations.containsKey(chunks[i])) {
                    signCount += nonBlankSignLocations.get(chunks[i]).size();
                }
            }
        }
        logger.info("Found %d non-blank signs in %d chunks.", signCount,
                chunkCount);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable() {
        for (ManagedSign sign : managedSigns.values()) {
            SignTableListener owner = sign.getOwner();
            if (owner != null) {
                owner.destroy(sign);
            }
        }
    }

    public void addListener(SignTableListener listener) {
        assert !listeners.contains(listener);

        listeners.add(listener);
        for (List<Location> list : nonBlankSignLocations.values()) {
            for (Location location : list) {
                if (managedSigns.containsKey(location)) {
                    continue;
                }

                Sign state = (Sign) location.getBlock().getState();
                String[] lines = state.getLines();
                ManagedSign instance = listener.create(location, lines);
                if (instance != null) {
                    managedSigns.put(location, instance);
                    state.update();
                }
            }
        }
    }

    public void removeListener(SignTableListener listener) {
        assert listeners.contains(listener);

        Iterator<ManagedSign> iter = managedSigns.values().iterator();
        while (iter.hasNext()) {
            if (iter.next().getOwner() == listener) {
                iter.remove();
            }
        }

        listeners.remove(listener);
    }

    public Iterator<ManagedSign> attachedSigns(SignTableListener owner,
            Block block) {
        if (!attachedSignsCount.containsKey(block.getLocation())) {
            return null;
        }

        return new AttachedSignsIterator(block, owner);
    }

    @EventHandler(ignoreCancelled = true)
    public void shouldSignChange(SignChangeEvent event) {
        String[] lines = event.getLines();
        if (!isBlank(lines) && !mayCreate(event.getPlayer(),
                event.getBlock().getLocation(), lines)) {
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
        Location attachedLocation = ManagedSign.getAttachedLocation(block);
        Integer currentCount = attachedSignsCount.get(attachedLocation);
        attachedSignsCount.put(attachedLocation,
                currentCount == null ? 1 : currentCount + 1);

        Location location = block.getLocation();
        Chunk chunk = block.getChunk();
        List<Location> list = nonBlankSignLocations.get(chunk);
        if (list == null) {
            list = new ArrayList<Location>();
            nonBlankSignLocations.put(chunk, list);
        }
        list.add(location);

        ManagedSign sign = create(location, lines);
        if (sign != null) {
            managedSigns.put(location, sign);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void didBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        switch (block.getType()) {
        case WALL_SIGN:
        case SIGN_POST:
            logger.info("onBlockBreak sign %s %s", event, block.getLocation());
            if (!isBlank(((Sign) block.getState()).getLines())) {
                unregisterSign(block);
            }
            return;
        default:
        }

        if (!attachedSignsCount.containsKey(block.getLocation())) {
            return;
        }

        for (int i = 0; i < attachableFaces.length; ++i) {
            Block signBlock = block.getRelative(attachableFaces[i]);
            switch (signBlock.getType()) {
            case WALL_SIGN:
            case SIGN_POST:
                break;
            default:
                continue;
            }

            logger.info("onBlockBreak attached %s %s", event,
                    block.getLocation());
            if (!isBlank(((Sign) signBlock.getState()).getLines())) {
                unregisterSign(signBlock);
            }
        }
    }

    @EventHandler
    public void onChunkLoadEvent(ChunkLoadEvent event) {
        findAllSignsInChunk(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkUnloadEvent(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();

        List<Location> list = nonBlankSignLocations.remove(chunk);
        if (list == null) {
            return;
        }

        for (Location location : list) {
            unregisterSign(location.getBlock());
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

    void unregisterSign(Block block) {
        Location location = block.getLocation();
        Chunk chunk = block.getChunk();
        List<Location> list = nonBlankSignLocations.get(chunk);
        if (list != null) {
            list.remove(location);
            if (list.isEmpty()) {
                nonBlankSignLocations.remove(chunk);
            }
        }

        Location attachedLocation = ManagedSign.getAttachedBlock(block)
                .getLocation();
        Integer count = attachedSignsCount.get(attachedLocation);
        if (1 < count) {
            attachedSignsCount.put(attachedLocation, count - 1);
        } else {
            attachedSignsCount.remove(attachedLocation);
        }

        ManagedSign sign = managedSigns.remove(location);
        if (sign != null) {
            sign.getOwner().destroy(sign);
        }
    }

    void findAllSignsInChunk(Chunk chunk) {
        List<Location> signs = new ArrayList<Location>();
        assert !nonBlankSignLocations.containsKey(chunk);

        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Sign)) {
                continue;
            }

            Block block = state.getBlock();
            Location location = block.getLocation();
            String[] lines = ((Sign) state).getLines();
            if (isBlank(lines)) {
                continue;
            }

            signs.add(location);

            Location attachedLocation = ManagedSign.getAttachedLocation(block);
            Integer currentCount = attachedSignsCount.get(attachedLocation);
            attachedSignsCount.put(attachedLocation,
                    currentCount == null ? 1 : currentCount + 1);

            ManagedSign sign = create(location, lines);
            if (sign != null) {
                managedSigns.put(location, sign);
                state.update();
            }
        }

        if (!signs.isEmpty()) {
            nonBlankSignLocations.put(chunk, signs);
        }
    }

    boolean mayCreate(Player player, Location location, String[] signText) {
        for (SignTableListener listener : listeners) {
            if (!listener.mayCreate(player, location, signText)) {
                return false;
            }
        }
        return true;
    }

    ManagedSign create(Location location, String[] lines) {
        for (SignTableListener listener : listeners) {
            ManagedSign instance = listener.create(location, lines);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

}
