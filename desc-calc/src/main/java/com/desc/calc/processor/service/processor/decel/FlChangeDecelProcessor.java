package com.desc.calc.processor.service.processor.decel;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.desc.calc.processor.CalcUtil.calcAltAtDistance;
import static com.desc.calc.processor.GlobalVar.addFl100Wp;
import static com.desc.calc.processor.GlobalVar.initialSpeed;

@Service
public class FlChangeDecelProcessor extends PathDecelProcessor {

    @Override
    public void processPath(List<Waypoint> waypoints) {

        if (!addFl100Wp) {
            return;
        }

        LocationResult fl100Loc = locateFl100(waypoints);
        if (fl100Loc != null) {
            log(fl100Loc);
            updateDistances(waypoints, fl100Loc);
        }
    }

    // Setting up info for logging, no path processing happens
    public void markFlChange(List<Waypoint> waypoints) {

        if (!addFl100Wp) {
            return;
        }

        LocationResult fl100Loc = locateFl100(waypoints);
        if (fl100Loc != null) {
            log(fl100Loc);

            if (fl100Loc.wpBefore.getDistanceToEndDecOptimized() == fl100Loc.fl100DisToEnd) {
                fl100Loc.wpBefore.setDecelTo(FL100_LIMIT);

            } else {
                // Be aware of the log message is not! in decel context
                int currentDistanceToNext = fl100Loc.fl100DisToEnd - fl100Loc.wpAfter.getDistanceToEndDecOptimized();
                fl100Loc.wpBefore.setAdditionalMsg("FL100 (" + (fl100Loc.wpAfter.getDistanceToEnd() + currentDistanceToNext) + ") (" + fl100Loc.wpAfter.getName() + " + "
                        + (currentDistanceToNext) + ")  --  " + "DECEL TO " + FL100_LIMIT);
            }
        }
    }

    private LocationResult locateFl100(List<Waypoint> waypoints) {

        Waypoint prevWp = null;
        int prevAlt = 0;
        int currentSpeed = initialSpeed;

        for (Waypoint wp : waypoints) {

            if (prevWp == null) {
                prevWp = wp;
                prevAlt = wp.getAltCalculated();
                continue;
            }

            if (prevWp.getSpeed() != null && prevWp.getSpeed() < currentSpeed) {
                currentSpeed = prevWp.getSpeed();
            }

            for (int i = prevWp.getDistanceToEndDecOptimized(); i > wp.getDistanceToEndDecOptimized(); i--) {

                int currentDistanceToNext = i - wp.getDistanceToEndDecOptimized();
                int currentAlt = calcAltAtDistance(currentDistanceToNext, wp.getAltCalculated(), prevWp.getAngle());

                if (prevAlt > 10000 && currentAlt <= 10000) {
                    if (currentSpeed > FL100_LIMIT) {
                        int extraDistanceNeeded = (currentSpeed - FL100_LIMIT) / 10;
                        return new LocationResult(i, extraDistanceNeeded, prevWp, wp);
                    }
                    return null;
                }
            }
            prevWp = wp;
        }
        return null;
    }

    private void updateDistances(List<Waypoint> waypoints, LocationResult wpLoc) {
        updateDistances(waypoints, wpLoc.fl100DisToEnd, wpLoc.extraDisNeeded);
    }

    private void updateDistances(List<Waypoint> waypoints, int fl100DisToEnd,
                                 int extraDistanceNeeded) {

        int decelDistanceLeft = extraDistanceNeeded;
        Waypoint wpTackled = null;

        for (int i = fl100DisToEnd; i <= 100; i++) {

            Waypoint prevWp = getPrevWp(waypoints, i);
            if (prevWp != null && prevWp != wpTackled) {

                int distanceDiff = prevWp.getDistanceToEndDecOptimized() - i;
                if (distanceDiff - extraDistanceNeeded < 0) {

                    prevWp.setDistanceToNextDecOptimized(prevWp.getDistanceToNextDecOptimized() - distanceDiff);
                    prevWp.setDistanceToEndDecOptimized(prevWp.getDistanceToEndDecOptimized() - distanceDiff);
                    decelDistanceLeft = decelDistanceLeft - distanceDiff;
                    wpTackled = prevWp;

                } else {

                    prevWp.setDistanceToNextDecOptimized(prevWp.getDistanceToNextDecOptimized() - decelDistanceLeft);
                    prevWp.setDistanceToEndDecOptimized(prevWp.getDistanceToEndDecOptimized() - extraDistanceNeeded);
                    updateDistanceToEnd(waypoints, prevWp, extraDistanceNeeded);
                    return;
                }
            }
        }
    }

    private Waypoint getPrevWp(List<Waypoint> waypoints, int distanceToEnd) {

        return waypoints.stream().filter(w ->
                        w.getDistanceToEndDecOptimized() > distanceToEnd &&
                                (w.getDistanceToEndDecOptimized() - w.getDistanceToNextDecOptimized()) <= distanceToEnd)
                .findFirst().orElse(null);
    }

    private void log(LocationResult loc) {
        int currentDistanceToNext = loc.fl100DisToEnd - loc.wpAfter.getDistanceToEndDecOptimized();
        System.out.println("CURRENT FL100: " + loc.wpAfter.getName() + " + "
                + (currentDistanceToNext) + " (" + (loc.fl100DisToEnd) + ")");
    }

    private record LocationResult(int fl100DisToEnd,
                                  int extraDisNeeded,
                                  Waypoint wpBefore,
                                  Waypoint wpAfter) {
    }
}