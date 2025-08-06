package com.gimserenity;


import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

public class GemstoneCrabTimerOverlay extends Overlay
{
    private final GemstoneCrabTimerPlugin plugin;
    private final GemstoneCrabTimerConfig config;
    private final Client client;
    
    @Inject
    public GemstoneCrabTimerOverlay(GemstoneCrabTimerPlugin plugin, GemstoneCrabTimerConfig config, Client client)
    {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        boolean renderTunnel = config.highlightTunnel() && plugin.getNearestTunnel() != null && plugin.shouldHighlightTunnel();
        boolean showTimeLeft = config.tunnelTimeLeft() && plugin.getNearestTunnel() != null && plugin.getEstimatedTimeRemainingMillis() > 0;
        boolean renderShell = config.highlightShell() && plugin.getCrabShell() != null && plugin.shouldHighlightShell();
        
        // Render the crab shell if needed
        if (renderShell)
        {
            NPC shell = plugin.getCrabShell();
            if (shell != null)
            {
                // Use green color if top 16 damager, red otherwise
                Color shellColor = plugin.isTop16Damager() ? Color.GREEN : Color.RED;
                
                String shellText = plugin.isTop16Damager() ? "Can Mine" : "Can't Mine";
                
                if (shell.getCanvasTextLocation(graphics, shellText, 0) != null)
                {
                    Point textLocation = shell.getCanvasTextLocation(graphics, shellText, 0);
                    if (textLocation != null)
                    {
                        OverlayUtil.renderTextLocation(graphics, textLocation, shellText, shellColor);
                    }
                }
                
                Shape objectClickbox = shell.getConvexHull();
                if (objectClickbox != null)
                {
                    graphics.setColor(new Color(shellColor.getRed(), shellColor.getGreen(), shellColor.getBlue(), 50));
                    graphics.fill(objectClickbox);
                    
                    graphics.setColor(shellColor);
                    graphics.setStroke(new BasicStroke(1));
                    graphics.draw(objectClickbox);
                }
            }
        }
        
        GameObject tunnel = plugin.getNearestTunnel();
        if (tunnel != null)
        {
            Color color = config.tunnelHighlightColor();
            if (tunnel.getCanvasTextLocation(graphics, "Tunnel", 0) != null)
            {
                Point textLocation = tunnel.getCanvasTextLocation(graphics, "Tunnel", 0);
                if (textLocation != null)
                {
                    // Only render "Tunnel" text if we should highlight the tunnel
                    if (renderTunnel)
                    {
                        OverlayUtil.renderTextLocation(graphics, textLocation, "Tunnel", color);
                    }
                    
                    // Always show countdown timer overlay above tunnel when in area
                    if (showTimeLeft)
                    {
                        long timeLeftMillis = plugin.getEstimatedTimeRemainingMillis();
                        long secondsLeft = timeLeftMillis / 1000;
                        long minutes = secondsLeft / 60;
                        long seconds = secondsLeft % 60;
                        String countdownText = String.format("%d:%02d", minutes, seconds);
                        Point countdownLocation = new Point(textLocation.getX(), textLocation.getY() - 15);
                        OverlayUtil.renderTextLocation(graphics, countdownLocation, countdownText, Color.WHITE);
                    }
                }
            
            // Only highlight the tunnel if we should
            if (renderTunnel)
            {
                Shape objectClickbox = tunnel.getConvexHull();
                if (objectClickbox != null)
                {
                    // Semi-transparent fill with the configured color
                    graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                    graphics.fill(objectClickbox);
                    
                    // Solid yellow outline
                    graphics.setColor(Color.YELLOW);
                    graphics.draw(objectClickbox);
                }
            }
        }
        }

        return null;
    }
}
