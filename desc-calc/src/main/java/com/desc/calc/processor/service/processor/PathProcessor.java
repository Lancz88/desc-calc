package com.desc.calc.processor.service.processor;

import com.desc.calc.processor.obj.Waypoint;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.calcAngle;

public abstract class PathProcessor {

    public abstract void processPath(List<Waypoint> waypoints);

    /**
     * Adjusting angles to calculated altitude information
     */
    public static void resetAngles(List<Waypoint> waypoints) {
        for (int i = 0; i <= waypoints.size() - 1; i++) {

            Waypoint currentWp = waypoints.get(i);
            Waypoint nextWp = i <= waypoints.size() - 2 ? waypoints.get(i + 1) : null;

            if (nextWp != null) {
                int altDiff = currentWp.getAltCalculated() - nextWp.getAltCalculated();
                currentWp.setAngle(calcAngle(altDiff, currentWp.getDistanceToNextDecOptimized()));
            }
        }
    }
}