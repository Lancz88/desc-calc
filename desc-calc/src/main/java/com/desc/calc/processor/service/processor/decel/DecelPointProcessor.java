package com.desc.calc.processor.service.processor.decel;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.*;
import static com.desc.calc.processor.GlobalVar.addFl100Wp;
import static com.desc.calc.processor.GlobalVar.initialSpeed;

@Service
public class DecelPointProcessor extends PathDecelProcessor {

    private static final int FLAPS_SPEED = 210;

    @Override
    public void processPath(List<Waypoint> waypoints) {

        int currentSpeed = initialSpeed;

        // Looking for DECEL points
        for (int i = 0; i <= waypoints.size() - 1; i++) {

            Waypoint wp = waypoints.get(i);
            wp.setDecelTo(null); //Clean up tho not needed normally

            // Expecting speed in limit below FL100
            if (wp.getAltCalculated() < 10000 && currentSpeed > FL100_LIMIT) {
                currentSpeed = FL100_LIMIT;
                addFl100Wp = true;
            }

            // Checking speed limitations
            if (wp.getSpeed() != null && wp.getSpeed() < currentSpeed) {

                int newSpeedReq = wp.getSpeed();
                int speedDiff = currentSpeed - newSpeedReq;

                // Reducing relevance of flaps decel
                if (newSpeedReq < FLAPS_SPEED) {
                    int normSpeedDiff = currentSpeed > FLAPS_SPEED ? currentSpeed - FLAPS_SPEED : 0;
                    int flapsSpeedDiff = FLAPS_SPEED - newSpeedReq;
                    speedDiff = (int) (normSpeedDiff + (flapsSpeedDiff * 0.5f));
                }

                int extraDistanceNeeded = (speedDiff) / 10;
                Waypoint prevWp = getPrevWp(waypoints, wp);
                Waypoint actualDecelWp = wp;

                if (prevWp != null) {
                    // Normalise distance to next data, ensuring extra room for deceleration
                    actualDecelWp = updateDistanceToNext(waypoints, wp, extraDistanceNeeded);

                    // Normalise distance to end data, ensuring extra room for deceleration
                    // Updating all waypoints between T/D and decel point.
                    updateDistanceToEnd(waypoints, wp, extraDistanceNeeded);
                }

                actualDecelWp.setDecelTo(newSpeedReq);
                currentSpeed = newSpeedReq;
            }
        }
        logUpdates(waypoints);
    }

    // Returns the actual decel points, the first one if 0 distance waypoints created
    private Waypoint updateDistanceToNext(List<Waypoint> waypoints, Waypoint wp,
                                          int extraDistanceNeeded) {

        Waypoint prevWp = getPrevWp(waypoints, wp);
        if (prevWp == null) {
            return wp;
        }

        if (prevWp.getDistanceToNextDecOptimized() >= extraDistanceNeeded) {
            int newDistance = prevWp.getDistanceToNextDecOptimized() - extraDistanceNeeded;
            prevWp.setDistanceToNextDecOptimized(newDistance);
            return wp;

        } else {
            int distanceLeftover = extraDistanceNeeded - prevWp.getDistanceToNextDecOptimized();
            prevWp.setDistanceToNextDecOptimized(0);
            return updateDistanceToNext(waypoints, prevWp, distanceLeftover);
        }
    }

    private void logUpdates(List<Waypoint> waypoints) {
        for (Waypoint wp : waypoints) {
            if (wp.getDistanceToNext() != wp.getDistanceToNextDecOptimized()) {
                System.out.println("DECEL POINT (CTR): " + wp.getName() + ", DISTANCE OPTIMIZED: " +
                        wp.getDistanceToNext() + " -> " + wp.getDistanceToNextDecOptimized());
            }
        }
    }
}