package com.gimserenity;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.DecimalFormat;

public class GemstoneCrabTimerDpsOverlay extends Overlay
{
    private final GemstoneCrabTimerPlugin plugin;
    private final PanelComponent panelComponent = new PanelComponent();
    private static final DecimalFormat DPS_FORMAT = new DecimalFormat("#,##0.0");
    
    @Inject
    private GemstoneCrabTimerDpsOverlay(GemstoneCrabTimerPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    
    @Inject
    private GemstoneCrabTimerConfig config;
    
    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Don't render if DPS tracking is disabled in config
        if (!config.showDpsTracker())
        {
            return null;
        }
        
        // Don't render if player is not in a Gemstone Crab area
        if (!plugin.isPlayerInGemstoneArea())
        {
            return null;
        }
        
        // Set up the panel
        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(new Color(18, 18, 18)); // Dark background
        panelComponent.setPreferredSize(new Dimension(150, 0));
        
        // Add title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Gemstone Crab DPS")
            .color(Color.GREEN)
            .build());
        
        // Add total damage
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Total Damage:")
            .right(String.format("%,d", plugin.getTotalDamage()))
            .build());
        
        // Add DPS
        panelComponent.getChildren().add(LineComponent.builder()
            .left("DPS:")
            .right(DPS_FORMAT.format(plugin.getCurrentDps()))
            .build());
        
        // Add XP gained
        panelComponent.getChildren().add(LineComponent.builder()
            .left("XP Gained:")
            .right(String.format("%,d", plugin.getTotalXpGained()))
            .build());

        // Add fight duration
        if (plugin.isFightInProgress() || plugin.getFightDuration() > 0)
        {
            long seconds = plugin.getFightDuration() / 1000;
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Duration:")
                .right(String.format("%d:%02d", seconds / 60, seconds % 60))
                .build());
        }
        
        return panelComponent.render(graphics);
    }
}
