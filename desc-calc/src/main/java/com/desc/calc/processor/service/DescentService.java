package com.desc.calc.processor.service;

import com.desc.calc.processor.obj.Waypoint;
import com.desc.calc.processor.service.processor.ChallengerProcessor;
import com.desc.calc.processor.service.processor.ConstraintsProcessor;
import com.desc.calc.processor.service.processor.DefragmentationProcessor;
import com.desc.calc.processor.service.processor.DreamPathProcessor;
import com.desc.calc.processor.service.processor.decel.DecelPointProcessor;
import com.desc.calc.processor.service.processor.decel.FlChangeDecelProcessor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static com.desc.calc.processor.CalcUtil.*;
import static com.desc.calc.processor.GlobalVar.*;

@Service
public class DescentService {

    private Double initialAngle;

    private final DreamPathProcessor dreamPathProcessor;
    private final ConstraintsProcessor constraintsProcessor;
    private final DecelPointProcessor decelPointProcessor;
    private final FlChangeDecelProcessor flChangeDecelProcessor;
    private final DefragmentationProcessor defragmentationProcessor;
    private final ChallengerProcessor challengerProcessor;

    public DescentService(DreamPathProcessor dreamPathProcessor,
                          ConstraintsProcessor constraintsProcessor,
                          DecelPointProcessor decelPointProcessor,
                          FlChangeDecelProcessor flChangeDecelProcessor,
                          DefragmentationProcessor defragmentationProcessor,
                          ChallengerProcessor challengerProcessor) {

        this.dreamPathProcessor = dreamPathProcessor;
        this.constraintsProcessor = constraintsProcessor;
        this.decelPointProcessor = decelPointProcessor;
        this.flChangeDecelProcessor = flChangeDecelProcessor;
        this.defragmentationProcessor = defragmentationProcessor;
        this.challengerProcessor = challengerProcessor;
    }

    public void process(List<Waypoint> waypoints) {

        // Sort waypoints by distance from destination (descending)
        waypoints.sort(Comparator.comparingInt(Waypoint::getIdx).reversed());

        // Calculate total distance values
        resetDistancesToEnd(waypoints);

        processVerticalPath(waypoints);

        decelPointProcessor.processPath(waypoints);
        processVerticalPath(waypoints);

        flChangeDecelProcessor.processPath(waypoints);
        processVerticalPath(waypoints);

        flChangeDecelProcessor.markFlChange(waypoints);

        printWPInfo(waypoints);
        printItinerary(waypoints);
    }

    private void processVerticalPath(List<Waypoint> waypoints) {

        // Process dream path, then process to constraints
        dreamPathProcessor.processPath(waypoints);
        constraintsProcessor.processPath(waypoints);

        // Straightening angles, removing unnecessary fractures between WPs
        defragmentationProcessor.processPath(waypoints);

        // Further angle cleaning, this time challenging constraints causing fractures
        // Executing 2 iterations for full cover (2nd is meant to be rare to be effective)
        challengerProcessor.processPath(waypoints);
        challengerProcessor.processPath(waypoints);
    }

    private String calcTopOfDescent(int crzAlt, List<Waypoint> waypoints) {

        String tdMessage = "";
        for (int i = 0; i <= waypoints.size() - 1; i++) {

            Waypoint wp = waypoints.get(i);
            if (wp.getAngle() > 0) {

                Waypoint wpNext = waypoints.get(i + 1);

                // Fix (2026.01.17) angle was coming from wpNext.getAngle(), now it's hardcoded to 3.0
                int distToWp = calcDistanceNeeded(crzAlt, wpNext.getAltCalculated(), 3);
                initialAngle = 3d;

                // Normalise with wind if given
                if (ts != null && gs != null) {
                    distToWp = (int) ((gs - ts) * windAltRatio / 10) + distToWp;
                }

                tdMessage = wpNext.getName() + " + " + distToWp;
                break;
            }
        }
        return tdMessage;
    }

    private void resetDistancesToEnd(List<Waypoint> waypoints) {
        int distanceCovered = 0;
        for (int i = waypoints.size() - 1; i >= 0; i--) {
            Waypoint wp = waypoints.get(i);
            distanceCovered += wp.getDistanceToNext();
            wp.setDistanceToEnd(distanceCovered);
        }
    }

    private void printWPInfo(List<Waypoint> waypoints) {
        waypoints.forEach(wp -> {
            System.out.println("wp: " + wp.getName() +
                    ", total distance: " + wp.getDistanceToEnd() +
                    (wp.getDistanceToEndDecOptimized() != wp.getDistanceToEnd() ? " (" + wp.getDistanceToEndDecOptimized() + ")" : "") +
                    ", distance to next: " + wp.getDistanceToNext() +
                    (wp.getDistanceToNextDecOptimized() != wp.getDistanceToNext() ? " (" + wp.getDistanceToNextDecOptimized() + ")" : "") +
                    ", expected alt: " + wp.getAltCalculated() +
                    ", angle from here: " + wp.getAngle() +
                    ", alt below: " + wp.getAltBelow() +
                    ", alt above: " + wp.getAltAbove() +
                    ", speed: " + wp.getSpeed());
        });
    }

    private void printItinerary(List<Waypoint> waypoints) {

        String topOfDescendMsg = calcTopOfDescent(crzAlt, waypoints);

        System.out.println("\n--------------- DES PLAN ---------------");
        System.out.println("\nT/D  --   " + topOfDescendMsg + "  --  DESCEND BY " + roundAngle(initialAngle) + "\n");

        double currentAngle = 0;
        boolean inDesPath = false;
        boolean wpPrinted = false;

        for (int i = 0; i <= waypoints.size() - 1; i++) {

            Waypoint wp = waypoints.get(i);
            if (!inDesPath && wp.getAngle() >= initialAngle) {
                inDesPath = true;
                currentAngle = initialAngle;
            }

            if (inDesPath) {

                if (wp.getDecelTo() != null) {
                    wpPrinted = printWp(wp, "DECEL TO " + wp.getDecelTo());
                }

                if (wp.getAltHold() != null && wp.getAngle() > 0 && currentAngle != 0) {
                    wpPrinted = printWp(wp, "HOLD " + wp.getAltHold());
                }

                if (wp.getAltBelow() != null && wp.getAltBelow() - wp.getAltCalculated() <= 500 && wp.getAngle() > 0) {
                    wpPrinted = printWp(wp, "HOLD BELOW " + wp.getAltBelow());
                }

                if (wp.getAltAbove() != null && wp.getAltCalculated() - wp.getAltAbove() <= 500 && wp.getAngle() > 0) {
                    wpPrinted = printWp(wp, "HOLD ABOVE " + wp.getAltAbove());
                }

                if (wp.getAngle() > 0 && !angleEquals(wp.getAngle(), currentAngle)) {
                    wpPrinted = printWp(wp, "DESCEND BY " + roundAngle(wp.getAngle()));
                    currentAngle = wp.getAngle();

                } else if (wp.getAngle() == 0 && !angleEquals(wp.getAngle(), currentAngle)
                        && !isAltBecauseOfDecelPoint(wp, waypoints)) {

                    wpPrinted = printWp(wp, "ALT " + wp.getAltCalculated());
                    currentAngle = wp.getAngle();
                }

                if (wp.getAdditionalMsg() != null) {
                    if (wpPrinted) {
                        System.out.println(" ");
                    }
                    System.out.println(wp.getAdditionalMsg());
                    wpPrinted = true;
                }
            }

            if (wpPrinted) {
                System.out.println(" ");
                wpPrinted = false;
            }
        }
        System.out.println("--------------- END ---------------");
        System.out.println("(info: normalise T/D with wind)");
    }

    // Check if waypoint got 0 distance due decel reduction, but there is no other path
    // change related. In that case don't present it as an ALT action.
    private boolean isAltBecauseOfDecelPoint(Waypoint wp, List<Waypoint> waypoints) {

        if (wp.getDistanceToNextDecOptimized() > 0) {
            return false;
        }

        // WP is very first one, so should follow descending
        Waypoint prevWp = getPrevWp(waypoints, wp);
        if (prevWp == null) {
            return true;
        }

        Waypoint nextWpWithDistance = null;

        List<Waypoint> nextWps = subListFromWPToEnd(waypoints, wp);
        nextWps.sort(Comparator.comparingInt(Waypoint::getIdx).reversed());
        nextWps.remove(wp);

        for (Waypoint nextWp : nextWps) {
            if (nextWp.getDistanceToNextDecOptimized() > 0) {
                nextWpWithDistance = nextWp;
                break;
            }
        }

        // No angles after wp, so that is real alt
        if (nextWpWithDistance == null) {
            return false;
        }

        return angleEquals(prevWp.getAngle(), nextWpWithDistance.getAngle());
    }

    private boolean printWp(Waypoint wp, String msg) {
        System.out.println(getWpTag(wp) + msg);
        return true;
    }

    private String getWpTag(Waypoint wp) {
        return wp.getName() + " (" + wp.getDistanceToEnd() + ")  --  ";
    }
}