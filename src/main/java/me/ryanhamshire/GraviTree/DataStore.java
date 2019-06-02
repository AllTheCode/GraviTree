package me.ryanhamshire.GraviTree;

import java.io.File;


//TODO Everything the same as automaticinventory
class DataStore {
    private final static String dataLayerFolderPath = GraviTree.instance.getDataFolder().getPath();
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";

    public DataStore() {
        //ensure data folders exist
        File playerDataFolder = new File(playerDataFolderPath);
        if (!playerDataFolder.exists()) {
            if (!playerDataFolder.mkdirs()) throw new RuntimeException("Datafolder couldn't be created");
        }
    }
}
