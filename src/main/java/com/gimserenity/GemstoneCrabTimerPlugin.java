package com.gimserenity;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Gemstone Crab Timer",
	description = "Tracks Gemstone Crab boss HP and sends notifications at threshold for a better afk experience.",
	tags = {"boss", "hp", "notification", "gemstone", "crab"}
)
public class GemstoneCrabTimerPlugin extends Plugin
{
	// Gemstone Crab boss NPC ID
	private static final int GEMSTONE_CRAB_ID = 14779;
	
	// Tunnel game object ID
	private static final int TUNNEL_OBJECT_ID = 57631;
	
	// HP widget ID constants
	private static final int BOSS_HP_BAR_WIDGET_ID = 19857428;
	
	// Maximum distance to highlight tunnel (in tiles)
	private static final int MAX_TUNNEL_DISTANCE = 20;

	// Distance from the crab to considered in the area
	private static final int DISTANCE_THRESHOLD = 13; 

	// Minutes at the boss required to count as a kill
	// Also, used as a cooldown for mining so its not counted multiple times
	private static final int KILL_THRESHOLD_MINUTES = 5;

	// Location of each crab from its center
	private static final WorldPoint EAST_CRAB = new WorldPoint(1353, 3112, 0);
	private static final WorldPoint SOUTH_CRAB = new WorldPoint(1239,3043, 0);
	private static final WorldPoint NORTH_CRAB = new WorldPoint(1273,3173, 0);

	// Crab Chat messages
	private static final String GEMSTONE_CRAB_DEATH_MESSAGE = "The gemstone crab burrows away, leaving a piece of its shell behind.";
	private static final String GEMSTONE_CRAB_MINE_SUCCESS_MESSAGE = "You swing your pick at the crab shell.";
	private static final String GEMSTONE_CRAB_MINE_FAIL_MESSAGE = "Your understanding of the gemstone crab is not great enough to mine its shell.";
	
	// Configuration keys
	private static final String CONFIG_GROUP = "gemstonecrab";
    private static final String CONFIG_KEY_COUNT = "crabCount";
	private static final String CONFIG_KEY_MINING_ATTEMPTS = "miningAttemptsCount";
	private static final String CONFIG_KEY_MINED = "minedCount";
	private static final String CONFIG_KEY_FAILED = "failedMiningCount";
	
	@Inject
	private Client client;

	@Inject
	private GemstoneCrabTimerConfig config;
	
	@Inject
	private Notifier notifier;
	
	@Inject
    private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
    private GemstoneCrabUtil util;
	
	// Track if we've already sent a notification for this boss fight
	private boolean notificationSent = false;
	
	// Track if the boss is present
	private boolean bossPresent = false;
	
	// Track if we should highlight the tunnel
	private boolean shouldHighlightTunnel = false;
	
	// Track the nearest tunnel to highlight
	private GameObject nearestTunnel = null;
	
	// Track all tunnels in the scene
	private final Map<WorldPoint, GameObject> tunnels = new HashMap<>();
	
	// DPS tracking variables
	private int totalDamage = 0;
	private long fightStartTime = 0;
	private long fightDuration = 0;
	private double currentDps = 0;
	private boolean fightInProgress = false;
	
	// XP tracking for DPS calculation
	private int lastAttackXp = 0;
	private int lastStrengthXp = 0;
	private int lastDefenceXp = 0;
	private int lastRangedXp = 0;
	private int lastMagicXp = 0;
	private int lastHitpointsXp = 0; // Track Hitpoints XP for display only
	private int totalXpGained = 0; // Total XP gained during the fight
	private long lastXpGainTime = 0;
	private static final long XP_GAIN_TIMEOUT = 1000; // 1 second timeout for XP gains

	// Kill tracking variables
	private int crabCount;

	// Mining stats tracking variables
	private int minedCount;
	private int miningAttempts;
	private int miningFailedCount;

	// Overlay for highlighting tunnels
	@Inject
	private GemstoneCrabTimerOverlay overlay;
	
	@Inject
	private GemstoneCrabTimerDpsOverlay dpsOverlay;
	
	@Override
	protected void startUp() throws Exception
	{
		log.info("Gemstone Crab Timer started!");
		notificationSent = false;
		shouldHighlightTunnel = false;
		nearestTunnel = null;
		tunnels.clear();
		resetDpsTracking();
		loadSavedConfiguration();
		overlayManager.add(overlay);
		overlayManager.add(dpsOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Gemstone Crab Timer stopped!");
		notificationSent = false;
		bossPresent = false;
		shouldHighlightTunnel = false;
		nearestTunnel = null;
		tunnels.clear();
		resetDpsTracking();
		overlayManager.remove(overlay);
		overlayManager.remove(dpsOverlay);
	}
	
	// Getter methods for the overlay
	public GameObject getNearestTunnel()
	{
		return nearestTunnel;
	}
	
	public boolean shouldHighlightTunnel()
	{
		return shouldHighlightTunnel;
	}
	
	// DPS tracking getter methods
	public int getTotalDamage()
	{
		return totalDamage;
	}
	
	public double getCurrentDps()
	{
		return currentDps;
	}
	
	/**
	 * Get the total XP gained during the current fight
	 * @return Total XP gained
	 */
	public int getTotalXpGained()
	{
		return totalXpGained;
	}
	
	public long getFightDuration()
	{
		if (fightInProgress)
		{
			return (System.currentTimeMillis() - fightStartTime);
		}
		return fightDuration;
	}
	
	public boolean isFightInProgress()
	{
		return fightInProgress;
	}
	
	//Reset all DPS tracking variables
	private void resetDpsTracking()
	{
		totalDamage = 0;
		fightStartTime = 0;
		fightDuration = 0;
		currentDps = 0;
		fightInProgress = false;
		totalXpGained = 0; // Reset total XP gained
		
		// Initialize XP tracking with current XP values
		if (client != null)
		{
			lastAttackXp = client.getSkillExperience(Skill.ATTACK);
			lastStrengthXp = client.getSkillExperience(Skill.STRENGTH);
			lastDefenceXp = client.getSkillExperience(Skill.DEFENCE);
			lastRangedXp = client.getSkillExperience(Skill.RANGED);
			lastMagicXp = client.getSkillExperience(Skill.MAGIC);
			lastHitpointsXp = client.getSkillExperience(Skill.HITPOINTS);
		}
		else
		{
			// Fallback if client is not available
			lastAttackXp = 0;
			lastStrengthXp = 0;
			lastDefenceXp = 0;
			lastRangedXp = 0;
			lastMagicXp = 0;
			lastHitpointsXp = 0;
		}
		lastXpGainTime = 0;
	}
	
	private void loadSavedConfiguration(){
		// Load the saved counts from configuration
        crabCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_COUNT);
		miningAttempts = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINING_ATTEMPTS);
		minedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_MINED);
		miningFailedCount = util.loadConfigValue(CONFIG_GROUP, CONFIG_KEY_FAILED);
	}

	
	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		// Only process if boss is present and we're near it
		if (!bossPresent)
		{
			return;
		}
		
		// Get the skill that changed
		Skill skill = statChanged.getSkill();
		int xp = statChanged.getXp();
		int estimatedDamage = 0;
		boolean isRelevantXp = false;
		int xpGained = 0;
		
		// Track XP gains and calculate damage for DPS
		switch (skill)
		{
			case ATTACK:
				if (xp > lastAttackXp)
				{
					xpGained = xp - lastAttackXp;
					estimatedDamage = estimateDamageFromXp(xpGained);
					totalXpGained += xpGained; // Add to total XP counter
					lastAttackXp = xp;
					isRelevantXp = true;
				}
				break;
			case STRENGTH:
				if (xp > lastStrengthXp)
				{
					xpGained = xp - lastStrengthXp;
					estimatedDamage = estimateDamageFromXp(xpGained);
					totalXpGained += xpGained; // Add to total XP counter
					lastStrengthXp = xp;
					isRelevantXp = true;
				}
				break;
			case DEFENCE:
				if (xp > lastDefenceXp)
				{
					xpGained = xp - lastDefenceXp;
					estimatedDamage = estimateDamageFromXp(xpGained);
					totalXpGained += xpGained; // Add to total XP counter
					lastDefenceXp = xp;
					isRelevantXp = true;
				}
				break;
			case RANGED:
				if (xp > lastRangedXp)
				{
					xpGained = xp - lastRangedXp;
					estimatedDamage = estimateDamageFromXp(xpGained);
					totalXpGained += xpGained; // Add to total XP counter
					lastRangedXp = xp;
					isRelevantXp = true;
				}
				break;
			case MAGIC:
				if (xp > lastMagicXp)
				{
					xpGained = xp - lastMagicXp;
					estimatedDamage = estimateDamageFromXp(xpGained);
					totalXpGained += xpGained; // Add to total XP counter
					lastMagicXp = xp;
					isRelevantXp = true;
				}
				break;
			case HITPOINTS:
				if (xp > lastHitpointsXp)
				{
					// Track Hitpoints XP for total XP display, but don't count it for DPS
					xpGained = xp - lastHitpointsXp;
					totalXpGained += xpGained;
					lastHitpointsXp = xp;
					// Log Hitpoints XP gain
					log.debug("Hitpoints XP gained: {}, Total XP: {}", xpGained, totalXpGained);
				}
				break;
			default:
				break;
		}
		
		// If we got relevant XP for DPS calculation
		if (isRelevantXp && estimatedDamage > 0)
		{
			long currentTime = System.currentTimeMillis();
			
			// Only process damage if it's been more than the timeout since last XP gain
			if (currentTime - lastXpGainTime > XP_GAIN_TIMEOUT)
			{
				// If we're not tracking a fight yet, start now
				if (!fightInProgress)
				{
					fightInProgress = true;
					fightStartTime = currentTime;
				}
				
				// Add the estimated damage to our total
				totalDamage += estimatedDamage;
				
				// Update current DPS
				long currentDuration = currentTime - fightStartTime;
				if (currentDuration > 0)
				{
					currentDps = (double) totalDamage / (currentDuration / 1000.0);
				}
				
				log.debug("XP-based damage on Gemstone Crab: {}. Total damage: {}, DPS: {}, XP gained: {}", 
					estimatedDamage, totalDamage, currentDps, totalXpGained);
			}
			
			// Update last XP gain time
			lastXpGainTime = currentTime;
		}
	}
	
	private int estimateDamageFromXp(int xpGained)
	{
		// XP is roughly 3.5x the damage dealt for Gemstone Crabs
		return Math.max(1, Math.round(xpGained / 3.5f));
	}
	
	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		
		if (npc.getId() == GEMSTONE_CRAB_ID)
		{
			log.debug("Gemstone Crab boss spawned");
			bossPresent = true;
			notificationSent = false;
			
			// Start a new DPS tracking session
			// This is where we reset stats - when a new boss spawns
			resetDpsTracking();
			fightInProgress = true;
			fightStartTime = System.currentTimeMillis();
			log.debug("New boss spawned, resetting DPS stats");
		}
	}
	
	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		
		if (npc.getId() == GEMSTONE_CRAB_ID)
		{
			log.debug("Gemstone Crab boss despawned");
			bossPresent = false;
			notificationSent = false;
			
			// Finalize DPS tracking but don't reset stats
			if (fightInProgress)
			{
				fightDuration = System.currentTimeMillis() - fightStartTime;
				fightInProgress = false;
				
				// Calculate final DPS
				if (fightDuration > 0)
				{
					currentDps = (double) totalDamage / (fightDuration / 1000.0);
				}
				log.debug("Fight ended. Total damage: {}, Duration: {}s, DPS: {}, XP gained: {}", 
					totalDamage, fightDuration / 1000.0, currentDps, totalXpGained);
			}
			
			// When the boss dies, find and highlight the nearest tunnel
			if (config.highlightTunnel())
			{
				findNearestTunnel();
				shouldHighlightTunnel = true;
				log.debug("Boss died, highlighting nearest tunnel");
			}
		}
	}
	
	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Check if player is in any of the Gemstone Crab areas
		boolean playerInArea = isPlayerInGemstoneArea();
		
		// If player left the area, reset tracking
		if (!playerInArea && (bossPresent || fightInProgress))
		{
			// Player left the area
			bossPresent = false;
			
			// Reset tunnel highlighting
			shouldHighlightTunnel = false;
			nearestTunnel = null;
			
			// If we were tracking a fight, stop tracking
			if (fightInProgress)
			{
				fightInProgress = false;
				resetDpsTracking();
			}
			
			log.debug("Player left Gemstone Crab area, resetting tracking");
			return;
		}
		
		// Check for boss HP bar to detect boss presence when re-entering the area
		if (playerInArea && !bossPresent)
		{
			Widget bossHpBar = client.getWidget(BOSS_HP_BAR_WIDGET_ID);
			if (bossHpBar != null && !bossHpBar.isHidden())
			{
				// Boss is present but we weren't tracking it (player just entered area)
				bossPresent = true;
				notificationSent = false;
				
				// We don't reset DPS tracking here anymore
				// Only track if there's an actual NPC (not just HP bar)
				log.debug("Re-entered area with boss HP bar visible");
			}
		}
		
		// Check boss HP for notification
		if (bossPresent && config.enableNotifications() && !notificationSent)
		{
			checkBossHpAndNotify();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			// Reset notification state when logging in
			notificationSent = false;
		}
		else if (gameStateChanged.getGameState() == GameState.LOADING)
		{
			// Reset tunnel highlighting when changing areas
			shouldHighlightTunnel = false;
			nearestTunnel = null;
			tunnels.clear();
			
			// Reset DPS tracking when changing areas
			if (fightInProgress)
			{
				log.debug("Area change detected, resetting DPS tracking");
				resetDpsTracking();
			}
		}
	}

	/*
	 * Handles Gemstone Crab Events
	 * "Kill" - crab moves
	 * Mining Attempt, Success, and Failure
	 * Kill count checked
	 */
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
		String message = chatMessage.getMessage();
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE || 
			chatMessage.getType() == ChatMessageType.SPAM) {  

			switch (message) {
				case (GEMSTONE_CRAB_DEATH_MESSAGE):
					if (isKillable()) {
						crabCount++;
						log.debug("Gemstone crab killed! KC: {}", crabCount);
						String msg = new ChatMessageBuilder().append(Color.RED, String.format("Gemstone Crab Killed! KC: %d", crabCount)).build();
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
					}
					else {
						log.debug("Gemstone crab kill did not count! Less than 5mins at the boss or not attacking.");
						String msg = new ChatMessageBuilder().append(Color.MAGENTA, String.format("Gemstone Crab not fought long enough for kill count.")).build();
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, "");
					}
					break;
				case (GEMSTONE_CRAB_MINE_SUCCESS_MESSAGE):
					if (!isMiningBeforeCooldown()) {
						log.debug("Gemstone Crab successfully mined!");
						miningAttempts++;
						minedCount++;
						setLastMiningAttempt();
					}
					break;
				case (GEMSTONE_CRAB_MINE_FAIL_MESSAGE):
					if (!isMiningBeforeCooldown()) {
						log.debug("Failed to mine Gemstone Crab!");
						miningAttempts++;
						miningFailedCount++;
						setLastMiningAttempt();
					}
					break;
				default:
					break;
			}
			saveCrabCounts();
        }
	}

	
	/**
	 * Check if the player is within any of the three Gemstone Crab areas
	 * @return true if player is in any of the three areas
	 */
	public boolean isPlayerInGemstoneArea()
	{
		if (client == null || client.getLocalPlayer() == null)
		{
			return false;
		}
		
		// Get player's world location
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		if (playerLocation == null)
		{
			return false;
		}
		
		// Checks if player is close enough to any of the crabs
		return playerLocation.distanceTo2D(EAST_CRAB) <= DISTANCE_THRESHOLD 
			|| playerLocation.distanceTo2D(SOUTH_CRAB) <= DISTANCE_THRESHOLD 
			|| playerLocation.distanceTo2D(NORTH_CRAB) <= DISTANCE_THRESHOLD;
	}
	

	
	private void checkBossHpAndNotify()
	{
		// Get the boss HP widget
		Widget bossHpBar = client.getWidget(BOSS_HP_BAR_WIDGET_ID);
		
		if (bossHpBar == null || bossHpBar.isHidden())
		{
			return;
		}
		
		// Get HP percentage directly from the widget's text value
		String text = bossHpBar.getText();
		if (text == null || text.isEmpty())
		{
			return;
		}
		
		// Parse the percentage value (e.g., "50%" -> 50)
		int hpPercent;
		try
		{
			// Remove the % sign and parse the number
			hpPercent = Integer.parseInt(text.replace("%", "").trim());
		}
		catch (NumberFormatException e)
		{
			log.debug("Failed to parse HP percentage from text: {}", text);
			return;
		}
		
		// Check if HP is at or below the threshold
		if (hpPercent <= config.hpThreshold() && !notificationSent)
		{
			notifier.notify(config.notificationMessage() + " (" + hpPercent + "% HP)");
			notificationSent = true;
			log.debug("Sent notification for Gemstone Crab at {}% HP", hpPercent);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		final GameObject gameObject = event.getGameObject();
		
		// Track tunnels in the scene
		if (gameObject.getId() == TUNNEL_OBJECT_ID)
		{
			WorldPoint location = gameObject.getWorldLocation();
			tunnels.put(location, gameObject);
		}
	}
	
	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		final GameObject gameObject = event.getGameObject();
		
		// Remove tunnels from tracking when they despawn
		if (gameObject.getId() == TUNNEL_OBJECT_ID)
		{
			WorldPoint location = gameObject.getWorldLocation();
			tunnels.remove(location);
			
			// If this was our highlighted tunnel, clear it
			if (nearestTunnel != null && nearestTunnel.equals(gameObject))
			{
				nearestTunnel = null;
				shouldHighlightTunnel = false;
			}
		}
	}
	

	// Find the nearest tunnel to the player
	private void findNearestTunnel()
	{
		if (client.getLocalPlayer() == null || tunnels.isEmpty())
		{
			return;
		}
		
		WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
		GameObject closest = null;
		int closestDistance = Integer.MAX_VALUE;
		
		for (GameObject tunnel : tunnels.values())
		{
			int distance = tunnel.getWorldLocation().distanceTo(playerLocation);
			
			// Only consider tunnels within the maximum distance
			if (distance <= MAX_TUNNEL_DISTANCE && distance < closestDistance)
			{
				closest = tunnel;
				closestDistance = distance;
			}
		}
		
		nearestTunnel = closest;
		log.debug("Found nearest tunnel at distance: {}", closestDistance);
	}

	private void saveCrabCounts() {
        configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_COUNT, String.valueOf(crabCount));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MINING_ATTEMPTS, String.valueOf(miningAttempts));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_MINED, String.valueOf(minedCount));
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_FAILED, String.valueOf(miningFailedCount));
    }

	@Provides
	GemstoneCrabTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GemstoneCrabTimerConfig.class);
	}
}
