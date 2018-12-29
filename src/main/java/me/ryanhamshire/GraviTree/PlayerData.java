package me.ryanhamshire.GraviTree;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

class PlayerData {
    private final static String METADATA_TAG = "GT_PlayerData";
    private Thread loadingThread;
    private Thread savingThread;
    private String playerName;
    private boolean gotChopInfo = false;
    private boolean chopEnabled = GraviTree.instance.config_chopModeOnByDefault;
    private UUID playerID;
    private boolean isDirty = false;

    private PlayerData(Player player) {
        this.playerName = player.getName();
        this.playerID = player.getUniqueId();
        this.loadingThread = new Thread(new DataLoader());
        this.loadingThread.start();
        player.setMetadata(METADATA_TAG, new FixedMetadataValue(GraviTree.instance, this));
    }

    static void Preload(Player player) {
        new PlayerData(player);
    }

    static PlayerData FromPlayer(Player player) {
        List<MetadataValue> data = player.getMetadata(METADATA_TAG);
        if (data == null || data.isEmpty()) {
            return new PlayerData(player);
        } else {
            try {
                return (PlayerData) (data.get(0).value());
            } catch (Exception e) {
                return new PlayerData(player);
            }
        }
    }

    boolean isGotChopInfo() {
        this.waitForLoadComplete();
        return gotChopInfo;
    }

    void setGotChopInfo(boolean gotChopInfo) {
        this.gotChopInfo = gotChopInfo;
        this.isDirty = true;
    }

    boolean isChopEnabled() {
        this.waitForLoadComplete();
        return chopEnabled;
    }

    void setChopEnabled(boolean chopEnabled) {
        this.chopEnabled = chopEnabled;
        this.isDirty = true;
    }

    void saveChanges() {
        if (!this.isDirty) return;

        this.waitForLoadComplete();
        this.savingThread = new Thread(new DataSaver());
        this.savingThread.start();
    }

    private void waitForLoadComplete() {
        if (this.loadingThread != null) {
            try {
                this.loadingThread.join();
            } catch (InterruptedException e) {
            }
            this.loadingThread = null;
        }
    }

    void waitForSaveComplete() {
        if (this.savingThread != null) {
            try {
                this.savingThread.join();
            } catch (InterruptedException e) {
            }
        }
    }

    private void writeDataToFile() {
        try {
            FileConfiguration config = new YamlConfiguration();
            config.set("Player Name", this.playerName);
            config.set("Chop Enabled", this.chopEnabled);
            config.set("Received Messages.Chop", this.gotChopInfo);
            File playerFile = new File(DataStore.playerDataFolderPath + File.separator + this.playerID.toString());
            config.save(playerFile);
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            GraviTree.AddLogEntry("Failed to save player data for " + playerID + " " + errors.toString());
        }

        this.savingThread = null;
        this.isDirty = false;
    }

    private void readDataFromFile() {
        File playerFile = new File(DataStore.playerDataFolderPath + File.separator + this.playerID.toString());

        //if it exists as a file, read the file
        if (playerFile.exists()) {
            boolean needRetry;
            int retriesRemaining = 5;
            Exception latestException = null;
            do {
                try {
                    needRetry = false;
                    FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
                    this.chopEnabled = config.getBoolean("Chop Enabled", true);
                    this.gotChopInfo = config.getBoolean("Received Messages.Chop", false);
                }

                //if there's any problem with the file's content, retry up to 5 times with 5 milliseconds between
                catch (Exception e) {
                    latestException = e;
                    needRetry = true;
                    retriesRemaining--;
                }

                try {
                    if (needRetry) Thread.sleep(5);
                } catch (InterruptedException exception) {
                }

            } while (needRetry && retriesRemaining >= 0);

            //if last attempt failed, log information about the problem
            if (needRetry) {
                StringWriter errors = new StringWriter();
                latestException.printStackTrace(new PrintWriter(errors));
                GraviTree.AddLogEntry("Failed to load data for " + playerID + " " + errors.toString());
            }
        }
    }

    private class DataSaver implements Runnable {
        @Override
        public void run() {
            writeDataToFile();
        }
    }

    private class DataLoader implements Runnable {
        @Override
        public void run() {
            readDataFromFile();
        }
    }
}