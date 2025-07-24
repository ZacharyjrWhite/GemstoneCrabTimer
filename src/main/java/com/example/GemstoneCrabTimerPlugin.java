package com.example;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.Hitsplat;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Gemstone Crab Timer",
	description = "Tracks Gemstone Crab boss HP and sends notifications at threshold",
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
	
	@Inject
	private Client client;

	@Inject
	private GemstoneCrabTimerConfig config;
	
	@Inject
	private Notifier notifier;
	
	@Inject
	private OverlayManager overlayManager;
	
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
	
	/**
	 * Reset all DPS tracking variables
	 */
	private void resetDpsTracking()
	{
		totalDamage = 0;
		fightStartTime = 0;
		fightDuration = 0;
		currentDps = 0;
		fightInProgress = false;
	}
	
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();
		
		// Check if the target is the Gemstone Crab boss
		if (target instanceof NPC && ((NPC) target).getId() == GEMSTONE_CRAB_ID)
		{
			Hitsplat hitsplat = event.getHitsplat();
			
			// Only count damage from the player's hits
			if (hitsplat.isMine())
			{
				// Add the damage to our total
				int damage = hitsplat.getAmount();
				totalDamage += damage;
				
				// If we're not tracking a fight yet, start now
				if (!fightInProgress)
				{
					fightInProgress = true;
					fightStartTime = System.currentTimeMillis();
				}
				
				// Update current DPS
				long currentDuration = System.currentTimeMillis() - fightStartTime;
				if (currentDuration > 0)
				{
					currentDps = (double) totalDamage / (currentDuration / 1000.0);
				}
				
				log.debug("Hit on Gemstone Crab: {}. Total damage: {}, DPS: {}", 
					damage, totalDamage, currentDps);
			}
		}
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
			resetDpsTracking();
			fightInProgress = true;
			fightStartTime = System.currentTimeMillis();
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
			
			// Finalize DPS tracking
			if (fightInProgress)
			{
				fightDuration = System.currentTimeMillis() - fightStartTime;
				fightInProgress = false;
				
				// Calculate final DPS
				if (fightDuration > 0)
				{
					currentDps = (double) totalDamage / (fightDuration / 1000.0);
				}
				log.debug("Fight ended. Total damage: {}, Duration: {}s, DPS: {}", 
					totalDamage, fightDuration / 1000.0, currentDps);
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
		// Check boss HP for notification
		if (bossPresent && config.enableNotifications() && !notificationSent)
		{
			checkBossHpAndNotify();
		}
		
		// If boss respawns, reset tunnel highlighting
		if (bossPresent && shouldHighlightTunnel)
		{
			shouldHighlightTunnel = false;
			nearestTunnel = null;
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
		
		// We're no longer tracking damage via HP percentage
		// Damage is now tracked via HitsplatApplied event
		
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
	
	/**
	 * Find the nearest tunnel to the player
	 */
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

	@Provides
	GemstoneCrabTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GemstoneCrabTimerConfig.class);
	}
}
