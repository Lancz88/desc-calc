package com.desc.calc.processor.service.processor.decel;

import com.desc.calc.processor.obj.Waypoint;
import com.desc.calc.processor.service.processor.PathProcessor;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.subListFromTDToWP;

public abstract class PathDecelProcessor extends PathProcessor {

    protected static final int FL100_LIMIT = 250;

    protected void updateDistanceToEnd(List<Waypoint> waypoints, Waypoint wp,
                                       int extraDistanceNeeded) {

        List<Waypoint> earlyWps = subListFromTDToWP(waypoints, wp);
        Waypoint nextWp = wp;

        for (int i = earlyWps.size() - 1; i >= 0; i--) {

            Waypoint earlyWp = waypoints.get(i);
            if (earlyWp == wp) {
                continue;

            } else if (earlyWp.getDistanceToNextDecOptimized() == 0) {
                earlyWp.setDistanceToEndDecOptimized(nextWp.getDistanceToEndDecOptimized());

            } else {
                int newDistanceToEnd = earlyWp.getDistanceToEndDecOptimized() - extraDistanceNeeded;
                earlyWp.setDistanceToEndDecOptimized(newDistanceToEnd);
            }
            nextWp = earlyWp;
        }
    }
}
