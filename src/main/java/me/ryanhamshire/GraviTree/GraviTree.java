package me.ryanhamshire.GraviTree;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

class GraviTree extends JavaPlugin implements Listener {
    static GraviTree instance;
    //for logging to the console and log file
    private static Logger log = Logger.getLogger("Minecraft");
    boolean config_chopModeOnByDefault;
    private boolean config_universalPermission;
    private String config_chopInfoMessage;
    private String config_chopOn;
    private String config_chopOff;
    private boolean config_canDisable;

    //adds a server log entry
    static void addLogEntry(String entry) {
        log.info("GraviTree: " + entry);
    }

    private static void markVisited(Block block) {
        block.setMetadata("gravitree.seen", new FixedMetadataValue(GraviTree.instance, Boolean.TRUE));
    }

    private static boolean haveVisited(Block block) {
        return block.hasMetadata("gravitree.seen");
    }

    private static boolean blockIsLog(Block block) {
        Material type = block.getType();
        return type.name().endsWith("LOG");
    }

    private static boolean blockIsPassthrough(Block block) {
        Material type = block.getType();
        return type.name().endsWith("LEAVES") || type.name().endsWith("LOG") || type == Material.SNOW;
    }

    private static boolean blockIsBreakable(Block block) {
        if (block.getY() < 0) return false;

        Material type = block.getType();
        return type.name().endsWith("LEAVES") || type == Material.AIR || type == Material.VINE || type == Material.COCOA || type == Material.TORCH || type == Material.SNOW;
    }

    private static boolean blockIsTreeAdjacent(Block block) {
        Material type = block.getType();
        return type.name().endsWith("LEAVES") || type == Material.AIR || type == Material.VINE || type == Material.COCOA || type == Material.TORCH || type == Material.SNOW || type == Material.GRASS || type == Material.DIRT || type == Material.STONE || type == Material.COBBLESTONE || type == Material.TALL_GRASS;
    }

    //initializes well...   everything
    public void onEnable() {
        GraviTree.instance = this;

        //read configuration settings (note defaults)
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) throw new RuntimeException();
            saveDefaultConfig();
            reloadConfig();
        }
        FileConfiguration config = getConfig();

        this.config_universalPermission = config.getBoolean("All Players Have Permission", true);
        this.config_chopModeOnByDefault = config.getBoolean("Chop Mode Defaults ON", true);
        this.config_chopInfoMessage = config.getString("Messages.Chop Toggle Info", "You can toggle falling tree blocks with /TreesFall.");
        this.config_chopOn = config.getString("Messages.Chop Toggle On", "Falling tree blocks enabled.");
        this.config_chopOff = config.getString("Messages.Chop Toggle Off", "Falling tree blocks disabled.");
        this.config_canDisable = config.getBoolean("Players Can Disable", true);

        //register for events
        this.getServer().getPluginManager().registerEvents(this, this);

        for (Player player : getServer().getOnlinePlayers()) {
            PlayerData.Preload(player);
        }
    }

    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerData data = PlayerData.FromPlayer(player);
            data.saveChanges();
            data.waitForSaveComplete();
        }

        addLogEntry("GraviTree disabled.");
    }

    //handles slash commands
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (cmd.getName().equalsIgnoreCase("treesfall") && player != null) {
            if (!this.config_canDisable) return true;

            PlayerData playerData = PlayerData.FromPlayer(player);
            playerData.setChopEnabled(!playerData.isChopEnabled());
            player.sendMessage(ChatColor.AQUA + (playerData.isChopEnabled() ? config_chopOn : config_chopOff));
            return true;
        }

        return false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        PlayerData.Preload(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event) {
        PlayerData.FromPlayer(event.getPlayer()).saveChanges();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        World world = brokenBlock.getWorld();

        if (world.getEnvironment() != Environment.NORMAL) return;
        if (!GraviTree.blockIsLog(brokenBlock)) return;

        Player player = event.getPlayer();
        if (!this.hasPermission(player)) return;

        PlayerData playerData = PlayerData.FromPlayer(player);

        if (!playerData.isGotChopInfo() && (!playerData.isChopEnabled() || this.config_canDisable)) {
            player.sendMessage(ChatColor.AQUA + GraviTree.instance.config_chopInfoMessage);
            playerData.setGotChopInfo(true);
        }

        if (!playerData.isChopEnabled()) return;

        Block bestUnderBlock = brokenBlock;

        do {
            bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
            while (GraviTree.blockIsLog(bestUnderBlock)) bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
            ConcurrentLinkedQueue<BlockFace> nearBelowFaces = new ConcurrentLinkedQueue<>();
            nearBelowFaces.add(BlockFace.EAST);
            nearBelowFaces.add(BlockFace.SOUTH);
            nearBelowFaces.add(BlockFace.NORTH);
            nearBelowFaces.add(BlockFace.WEST);
            nearBelowFaces.add(BlockFace.NORTH_EAST);
            nearBelowFaces.add(BlockFace.SOUTH_EAST);
            nearBelowFaces.add(BlockFace.NORTH_WEST);
            nearBelowFaces.add(BlockFace.SOUTH_WEST);

            do {
                Block nearBelowBlock = bestUnderBlock.getRelative(nearBelowFaces.poll());
                if (this.blockIsRootType(nearBelowBlock)) {
                    bestUnderBlock = nearBelowBlock;
                    break;
                }
                if (GraviTree.blockIsLog(nearBelowBlock)) {
                    bestUnderBlock = nearBelowBlock;
                    break;
                }
            } while (!nearBelowFaces.isEmpty());
        } while (GraviTree.blockIsLog(bestUnderBlock));


        if (!this.blockIsRootType(bestUnderBlock)) return;

        BlockFace[] adjacentFaces = new BlockFace[]{
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.SOUTH,
                BlockFace.NORTH,
                BlockFace.NORTH_EAST,
                BlockFace.NORTH_WEST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_WEST
        };

        for (BlockFace adjacentFace : adjacentFaces) {
            Block adjacentBlock = brokenBlock.getRelative(adjacentFace);
            if (GraviTree.blockIsLog(adjacentBlock)) return;
        }

        Block aboveBlock = brokenBlock.getRelative(BlockFace.UP);
        while (GraviTree.blockIsLog(aboveBlock)) {
            aboveBlock = aboveBlock.getRelative(BlockFace.UP);
        }

        if (!this.blockIsTreeTopper(aboveBlock)) return;

        int radius = 20;
        if (brokenBlock.getType() == Material.DARK_OAK_LOG) {
            radius = 2;
        }
        FallTask fallTask = new FallTask(brokenBlock, true, bestUnderBlock.getX() - radius, bestUnderBlock.getX() + radius, bestUnderBlock.getZ() - radius, bestUnderBlock.getZ() + radius, player);
        Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, 1L);
    }

    private boolean hasPermission(Player player) {
        if (GraviTree.instance.config_universalPermission) return true;
        return player.hasPermission("gravitree.chop");
    }

    private boolean blockIsRootType(Block block) {
        Material type = block.getType();
        return type == Material.DIRT || type == Material.GRASS || type == Material.STONE || type == Material.COBBLESTONE || type == Material.TERRACOTTA || type == Material.SAND;
    }

    private boolean blockIsTreeTopper(Block block) {
        Material type = block.getType();
        return type.name().endsWith("LEAVES") || type == Material.AIR || type == Material.SNOW;
    }

    class FallTask implements Runnable {
        private Block blockToDrop;
        private boolean breakUnderBlocks;
        private int max_x;
        private int max_z;
        private int min_x;
        private int min_z;
        private Player player;

        FallTask(Block blockToDrop, boolean breakUnderBlocks, int min_x, int max_x, int min_z, int max_z, Player player) {
            this.blockToDrop = blockToDrop;
            this.breakUnderBlocks = breakUnderBlocks;
            this.min_x = min_x;
            this.min_z = min_z;
            this.max_x = max_x;
            this.max_z = max_z;
            this.player = player;
        }

        @Override
        public void run() {
            if (GraviTree.blockIsLog(this.blockToDrop)) {
                FallingBlock fallingBlock = blockToDrop.getWorld().spawnFallingBlock(blockToDrop.getLocation().add(.5, 0, .5), blockToDrop.getState().getBlockData());
                fallingBlock.setDropItem(false);

                if (!GraviTree.blockIsLog(this.blockToDrop.getRelative(BlockFace.UP))) {
                    BlockBreakEvent event = new BlockBreakEvent(this.blockToDrop, this.player);
                    Bukkit.getPluginManager().callEvent(event);
                }

                blockToDrop.setType(Material.AIR);

                if (this.breakUnderBlocks) {
                    Block underBlock = blockToDrop.getRelative(BlockFace.DOWN);
                    while (GraviTree.blockIsBreakable(underBlock)) {
                        underBlock.breakNaturally();
                        underBlock = underBlock.getRelative(BlockFace.DOWN);
                    }
                }
            }

            ConcurrentLinkedQueue<Block> nearAboveBlocks = new ConcurrentLinkedQueue<>();
            Block aboveBlock = this.blockToDrop.getRelative(BlockFace.UP);
            nearAboveBlocks.add(aboveBlock);
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.WEST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH_EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH_EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH_WEST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH_WEST));

            boolean foundLogAbove = false;
            ConcurrentLinkedQueue<FallTask> newTasks = new ConcurrentLinkedQueue<>();
            for (Block block : nearAboveBlocks) {
                if (GraviTree.blockIsLog(block)) {
                    Block underBlock = block.getRelative(BlockFace.DOWN);
                    if (GraviTree.blockIsBreakable(underBlock)) {
                        FallTask fallTask = new FallTask(block, block != aboveBlock, this.min_x, this.max_x, this.min_z, this.max_z, this.player);
                        newTasks.add(fallTask);
                        foundLogAbove = true;
                    }
                } else if (!GraviTree.blockIsTreeAdjacent(block)) {
                    return;
                }
            }

            long i = 0;
            for (FallTask task : newTasks) {
                Bukkit.getScheduler().runTaskLater(GraviTree.instance, task, ++i);
            }

            if (!foundLogAbove && !this.breakUnderBlocks) {
                ConcurrentLinkedQueue<Block> blocksToFall = new ConcurrentLinkedQueue<>();

                GraviTree.markVisited(aboveBlock);
                ConcurrentLinkedQueue<Block> blocksToVisit = new ConcurrentLinkedQueue<>();
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.WEST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.NORTH));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_WEST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.NORTH_EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_WEST));
                for (Block block : blocksToVisit) {
                    GraviTree.markVisited(block);
                }

                Block nextBlock;
                while ((nextBlock = blocksToVisit.poll()) != null) {
                    if (GraviTree.blockIsLog(nextBlock)) {
                        Block underBlock = nextBlock.getRelative(BlockFace.DOWN);
                        if (blockIsBreakable(underBlock)) {
                            if (!blocksToFall.contains(nextBlock)) {
                                blocksToFall.add(nextBlock);
                            }
                        }
                    }

                    if (GraviTree.blockIsPassthrough(nextBlock)) {
                        Block[] nextBlocks = new Block[]{
                                nextBlock,
                                nextBlock.getRelative(BlockFace.UP),
                                nextBlock.getRelative(BlockFace.DOWN)
                        };

                        for (Block nextNextBlock : nextBlocks) {
                            for (BlockFace face : new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.SOUTH_EAST, BlockFace.NORTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_WEST, BlockFace.DOWN, BlockFace.UP}) {
                                Block adjacentBlock = nextNextBlock.getRelative(face);
                                if (adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock)) {
                                    blocksToVisit.add(adjacentBlock);
                                    GraviTree.markVisited(adjacentBlock);
                                }
                            }
                        }
                    }
                }

                Block blockToDrop;
                long delayInTicks = 1;
                while ((blockToDrop = blocksToFall.poll()) != null) {
                    FallTask fallTask = new FallTask(blockToDrop, true, this.min_x, this.max_x, this.min_z, this.max_z, this.player);
                    Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, delayInTicks++);
                }
            }
        }
    }
}