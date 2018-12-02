package me.ryanhamshire.GraviTree;

import java.io.File;

public class DataStore
{
    private final static String dataLayerFolderPath = GraviTree.instance.getDataFolder().getPath();
    final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
    
    public DataStore()
	{
        //ensure data folders exist
        File playerDataFolder = new File(playerDataFolderPath);
        if(!playerDataFolder.exists())
        {
            playerDataFolder.mkdirs();
        }
    }
}
