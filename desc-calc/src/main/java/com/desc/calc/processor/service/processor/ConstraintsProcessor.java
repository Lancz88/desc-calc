package com.desc.calc.processor.service.processor;

import com.desc.calc.processor.obj.CtrConflict;
import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.*;
import static com.desc.calc.processor.GlobalVar.crzAlt;

/**
 * Setting up altitudes respecting altitude constraints,
 * expecting that altitudes are set already following 3.0 angle.
 */
@Service
public class ConstraintsProcessor extends PathProcessor {

    @Override
    public void processPath(List<Waypoint> waypoints) {

        // Processing from finish to TD.
        for (int i = waypoints.size() - 1; i >= 0; i--) {

            Waypoint wp = waypoints.get(i);
            CtrConflict conflict = wp.validateCtr();

            if (conflict != null) {
                // TOO HIGH: angle will be below 3.0, keep max alt, respect constraint
                // TOO LOW: angle must be above 3.0, keep min alt, respect constraint
                Integer newAlt = conflict.getValue();

                System.out.println("CONFLICT FOR ADJUSTMENT: " + wp.getName() + ", " + conflict.name() +
                        ", REQUIRED: " + conflict.getValue() + ", ACTUAL: " + wp.getAltCalculated());

                wp.setAltCalculated(newAlt);

                // Ensure no climbs in the path
                for (Waypoint wpEarlier : subListFromWPToEnd(waypoints, wp)) {
                    if (wpEarlier.getAltCalculated() > newAlt) {
                        wpEarlier.setAltCalculated(newAlt);
                    }
                }

                // Process 3.0 from constraint to T/D
                processDreamPathTo(wp, waypoints);

                // Process next constraint
                processPath(waypoints);
            }
        }
    }

    private void processDreamPathTo(Waypoint wpTo, List<Waypoint> waypoints) {

        List<Waypoint> sublist = subListFromTDToWP(waypoints, wpTo);

        // Calculate references
        int altToDesc = wpTo.getAltCalculated();
        int totalDistanceAtAltToDesc = wpTo.getDistanceToEndDecOptimized();

        int distanceNeeded = calcDistanceNeeded(crzAlt, altToDesc, 3) + totalDistanceAtAltToDesc;

        // Calculate waypoint alt data
        for (Waypoint wp : sublist) {
            if ((wp.getDistanceToEndDecOptimized()) < distanceNeeded) { // WP in des path

                int distanceToAltToDesc = wp.getDistanceToEndDecOptimized() - totalDistanceAtAltToDesc;
                wp.setAltCalculated(
                        calcAltAtDistance(distanceToAltToDesc, altToDesc, 3)
                );

            } else {
                wp.setAltCalculated(crzAlt);
            }
        }

        // Calculate waypoint angle data
        resetAngles(waypoints);
    }
}
