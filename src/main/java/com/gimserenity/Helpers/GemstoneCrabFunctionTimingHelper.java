package com.gimserenity.Helpers;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GemstoneCrabFunctionTimingHelper {
    public static double renders = 0;
    public static double runningTotal = 0;
    public static double highestRenderTime = 0;
    public static double lowestRenderTime = Double.MAX_VALUE;

    public void addRender(long timing) {
        renders++;
        runningTotal += timing;
        if (timing > highestRenderTime) {
            highestRenderTime = timing;
        }
        if (timing < lowestRenderTime) {
            lowestRenderTime = timing;
        }
        if (renders % 1000 == 0) {
            double averageRender = runningTotal / renders;
            log.info("Average render time: {} over a total of {} renders. Running total {}. Highest render time: {} Lowest render time: {}", averageRender, renders, runningTotal, highestRenderTime, lowestRenderTime);
        }
    }
}
