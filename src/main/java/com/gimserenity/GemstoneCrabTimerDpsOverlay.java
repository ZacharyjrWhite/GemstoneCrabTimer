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
        // Don't render if player is not in a Gemstone Crab area
        if (!plugin.isPlayerInGemstoneArea())
        {
            return null;
        }
        
        // Set up the panel
        panelComponent.getChildren().clear();
        panelComponent.setBackgroundColor(new Color(18, 18, 18, 180)); // Dark background
        panelComponent.setPreferredSize(new Dimension(150, 0));
        
        // Show main stats section if enabled
        if (config.showMainStats())
        {
            // Add title
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Current Fight")
                .color(Color.GREEN)
                .build());
                
            // Add total damage if enabled
            if (config.displayTotalDamage()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Total Damage:")
                    .right(String.format("%,d", plugin.getTotalDamage()))
                    .build());
            }
            
            // Add DPS if enabled
            if (config.displayDps()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("DPS:")
                    .right(DPS_FORMAT.format(plugin.getCurrentDps()))
                    .build());
            }
            
            // Add XP gained if enabled
            if (config.displayXpGained()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("XP Gained:")
                    .right(String.format("%,d", plugin.getTotalXpGained()))
                    .build());
            }
            
            // Add fight duration if enabled
            if (config.displayDuration()) {
                long seconds = plugin.getFightDuration() / 1000;
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Duration:")
                    .right(String.format("%d:%02d", seconds / 60, seconds % 60))
                    .build());
            }
            
            // Add estimated time left if enabled
            if (config.displayTimeLeft() && plugin.isFightInProgress()) {
                long timeLeftMillis = plugin.getEstimatedTimeRemainingMillis();
                if (timeLeftMillis > 0) {
                    long secondsLeft = timeLeftMillis / 1000;
                    panelComponent.getChildren().add(LineComponent.builder()
                        .left("Time Left:")
                        .right(String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60))
                        .build());
                }
            }
        }

        if (config.showOverlay()) {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Kill Stats")
                .color(Color.GREEN)
                .build());

            if (config.displayKillCount()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Crabs Killed:")
                    .right(String.valueOf(plugin.getCrabCount()))
                    .build());
            }

            if (config.displayMiningAttempts()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mining Attempts:")
                    .right(String.valueOf(plugin.getMiningAttemptsCount()))
                    .build());
            }


            if (config.displayMinedCount()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Successful:")
                    .right(String.valueOf(plugin.getMinedCount()))
                    .build());
            }

            if (config.displayFailedMiningCount()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Failed:")
                    .right(String.valueOf(plugin.getMiningFailedCount()))
                    .build());
            }

            if (config.displayFailedMiningCount()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Gems Mined:")
                    .right(String.valueOf(plugin.getGemsCount()))
                    .build());
            }
        }

        
        return panelComponent.render(graphics);
    }
}
