package com.desc.calc.processor.service.processor;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.calcAltAtDistance;
import static com.desc.calc.processor.CalcUtil.calcDistanceNeeded;
import static com.desc.calc.processor.GlobalVar.crzAlt;

/**
 * Setting up altitudes following a 3.0 angle from the runway
 * to the crz altitude, disrespecting any kind of constraints.
 */
@Service
public class DreamPathProcessor extends PathProcessor {

    @Override
    public void processPath(List<Waypoint> waypoints) {

        // Calculate references
        int altToDesc = 0;
        int totalDistanceAtAltToDesc = 0;

        // Finding the closest reference to runway for final alt
        for (int i = waypoints.size() - 1; i >= 0; i--) {

            Waypoint wp = waypoints.get(i);
            if (wp.getAltAbove() != null) {
                altToDesc = wp.getAltAbove();
                totalDistanceAtAltToDesc = wp.getDistanceToEndDecOptimized();
                break;

            } else if (wp.getAltHold() != null) {
                altToDesc = wp.getAltHold();
                totalDistanceAtAltToDesc = wp.getDistanceToEndDecOptimized();
                break;
            }
        }
        int distanceNeeded = calcDistanceNeeded(crzAlt, altToDesc, 3) + totalDistanceAtAltToDesc;

        // Calculate waypoint alt data
        for (Waypoint wp : waypoints) {
            if ((wp.getDistanceToEndDecOptimized()) < distanceNeeded) { // WP in des path

                int distanceToAltToDesc = wp.getDistanceToEndDecOptimized() - totalDistanceAtAltToDesc;
                int altAtDistance = calcAltAtDistance(distanceToAltToDesc, altToDesc, 3);
                wp.setAltCalculated(altAtDistance);

            } else {
                wp.setAltCalculated(crzAlt);
            }
        }

        // Calculate waypoint angle data
        resetAngles(waypoints);
        System.out.println("DREAM PATH (3.0) HAS BEEN SET UP");
    }
}
