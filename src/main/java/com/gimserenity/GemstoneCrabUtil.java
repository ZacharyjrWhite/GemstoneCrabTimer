package com.gimserenity;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
public class GemstoneCrabUtil {
	@Inject
    private ConfigManager configManager;

    public int loadConfigValue(String configGroup, String key){
        String savedValue = configManager.getConfiguration(configGroup, key);
        int count = 0;
        if (savedValue != null)
        {
            try
            {
                count = Integer.parseInt(savedValue);
            }
            catch (NumberFormatException e)
            {
                log.warn("Failed to parse saved value with key {}", key);
            }
        }
        return count;
    }
}
