package com.desc.calc.processor.service.processor;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.desc.calc.processor.CalcUtil.calcAltAtDistance;

/**
 * Looking for wp chains in path having no alt constraints and being enclosed
 * by waypoints having alt constraints, and setting up their alt data following
 * a straight angle, removing unnecessary steps and fragmentation.
 */
@Service
public class DefragmentationProcessor extends PathProcessor {

    protected boolean challengeMode = false;

    @Override
    public void processPath(List<Waypoint> waypoints) {

        List<Waypoint> wpChain = new ArrayList<>();
        for (int i = waypoints.size() - 1; i >= 0; i--) {

            // Finding waypoint chains with no constraints
            // enclosed by wps with constraints

            Waypoint wp = waypoints.get(i);
            if (wpChain.isEmpty()) {
                wpChain.add(wp);

            } else {

                if (wp.hasAltConstraints()) {

                    // Wp is upper bound constraint of the chain
                    if (wpChain.size() >= 2) {
                        wpChain.add(wp);
                        processAnglesOnWpChain(wpChain);
                    }

                    // Or the first element of a new chain
                    wpChain.clear();
                    wpChain.add(wp);

                } else {
                    wpChain.add(wp);
                }
            }
        }
    }

    protected void processAnglesOnWpChain(List<Waypoint> wpChain) {

        Waypoint wpFrom = wpChain.get(wpChain.size() - 1);
        Waypoint wpTo = wpChain.get(0);

        int altDiff = wpFrom.getAltCalculated() - wpTo.getAltCalculated();
        double angle = (double) altDiff / (wpFrom.getDistanceToEndDecOptimized() - wpTo.getDistanceToEndDecOptimized()) / 100;

        resetWpData(wpChain, wpTo, angle);
    }

    private void resetWpData(List<Waypoint> wpChain, Waypoint wpTo, double angle) {

        for (Waypoint middle : wpChain) {
            if (middle != wpTo && middle.getAngle() != angle) {

                double originalAngle = middle.getAngle();
                middle.setAngle(angle);

                int newAlt = calcAltAtDistance(
                        middle.getDistanceToEndDecOptimized() - wpTo.getDistanceToEndDecOptimized(),
                        wpTo.getAltCalculated(), angle
                );
                middle.setAltCalculated(newAlt);

                if (!challengeMode) {
                    System.out.println("ANGLE OPTIMIZED: " + middle.getName() + ", " + originalAngle + " -> " + middle.getAngle());
                }
            }
        }
    }
}
