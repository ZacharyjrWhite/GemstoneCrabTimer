package com.example;


import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
// Using OverlayUtil instead of deprecated OverlayPriority
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;

public class GemstoneCrabTimerOverlay extends Overlay
{
    private final GemstoneCrabTimerPlugin plugin;
    private final GemstoneCrabTimerConfig config;

    @Inject
    private GemstoneCrabTimerOverlay(GemstoneCrabTimerPlugin plugin, GemstoneCrabTimerConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightTunnel() || plugin.getNearestTunnel() == null || !plugin.shouldHighlightTunnel())
        {
            return null;
        }

        GameObject tunnel = plugin.getNearestTunnel();
        if (tunnel == null)
        {
            return null;
        }

        Color color = config.tunnelHighlightColor();
        if (tunnel.getCanvasTextLocation(graphics, "Tunnel", 0) != null)
        {
            Point textLocation = tunnel.getCanvasTextLocation(graphics, "Tunnel", 0);
            if (textLocation != null)
            {
                OverlayUtil.renderTextLocation(graphics, textLocation, "Tunnel", color);
            }
        }

        Shape objectClickbox = tunnel.getConvexHull();
        if (objectClickbox != null)
        {
            // Semi-transparent fill with the configured color
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
            graphics.fill(objectClickbox);
            
            // Solid yellow outline (not thick)
            graphics.setColor(Color.YELLOW);
            graphics.draw(objectClickbox);
        }

        return null;
    }
}
