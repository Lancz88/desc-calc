package com.desc.calc.processor.service.processor;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.desc.calc.processor.CalcUtil.angleEquals;

/**
 * Subclass of DefragmentationProcessor and the goal is very similar.
 * Instead of cleaning up angles between constraints, this service
 * challenging constraints that causing unnecessary steps in path.
 * - Temporary switching of constraints
 * - Processing defragmentation
 * - Check if constraint passing after turning back, restore if not
 */
@Service
public class ChallengerProcessor extends DefragmentationProcessor {

    private final Map<String, Integer> wpAltBackupMap = new HashMap<>();
    private final Map<String, Double> wpAngleBackupMap = new HashMap<>();

    @Override
    public void processPath(List<Waypoint> waypoints) {

        List<Waypoint> constraintsToChallenge = new ArrayList<>();
        Waypoint wpFrom = waypoints.get(0);
        Waypoint wpTo = waypoints.get(waypoints.size() - 1);

        for (int i = waypoints.size() - 1; i >= 0; i--) {
            Waypoint wp = waypoints.get(i);

            // If wp is in middle and worth rechecking
            if (wp != wpFrom && wp != wpTo && isWpQuestionable(waypoints, wp)) {
                constraintsToChallenge.add(wp);
            }
        }

        if (!constraintsToChallenge.isEmpty()) {

            for (Waypoint ctrWp : constraintsToChallenge) {

                // Creating backup
                wpAltBackupMap.clear();
                wpAngleBackupMap.clear();
                for (Waypoint wp : waypoints) {
                    wpAltBackupMap.put(wp.getName(), wp.getAltCalculated());
                    wpAngleBackupMap.put(wp.getName(), wp.getAngle());
                }

                // Replacing WP with constraints free version of the origin
                int listIdx = waypoints.indexOf(ctrWp);
                Waypoint copyWp = copyWpNoCtr(ctrWp);
                waypoints.set(listIdx, copyWp);

                // Processing defragmentation
                super.challengeMode = true;
                super.processPath(waypoints);

                // Replace back original constraint with new data calculated
                ctrWp.setAltCalculated(copyWp.getAltCalculated());
                ctrWp.setAngle(copyWp.getAngle());
                waypoints.set(listIdx, ctrWp);

                // Validate if new path passing constraints
                boolean challengeSucceeded = true;
                for (Waypoint wp : waypoints) {
                    if (wp.validateCtr() != null) {
                        challengeSucceeded = false;
                        break;
                    }
                }

                for (Waypoint wp : waypoints) {

                    Integer backUpAlt = wpAltBackupMap.get(wp.getName());
                    Double backupAngle = wpAngleBackupMap.get(wp.getName());

                    // Check if wp modified
                    if (wp.getAngle() != backupAngle || !Objects.equals(wp.getAltCalculated(), backUpAlt)) {

                        if (challengeSucceeded) {
                            System.out.println("ANGLE OPTIMIZED (CTR CHALLENGE): " +
                                    wp.getName() + ", " + backupAngle + " -> " + wp.getAngle());

                            // Restore if new path not passing
                        } else {
                            wp.setAngle(backupAngle);
                            wp.setAltCalculated(backUpAlt);
                        }
                    }
                }
            }
            super.challengeMode = false;
        }
    }

    private boolean isWpQuestionable(List<Waypoint> waypoints, Waypoint wp) {

        if (waypoints.indexOf(wp) - 1 < 0) {
            return false;
        }

        Waypoint prevWp = waypoints.get(waypoints.indexOf(wp) - 1);
        return wp.getAltHold() == null && wp.hasAltConstraints() && prevWp != null &&
                !angleEquals(wp.getAngle(), prevWp.getAngle());
    }

    private Waypoint copyWpNoCtr(Waypoint ctrWp) {

        Waypoint copyWp = new Waypoint(ctrWp.getIdx(),
                ctrWp.getName(),
                ctrWp.getDistanceToNext(),
                null, null, null,
                ctrWp.getSpeed()
        );

        copyWp.setAltCalculated(ctrWp.getAltCalculated());
        copyWp.setAngle(ctrWp.getAngle());
        copyWp.setDistanceToEnd(ctrWp.getDistanceToEnd());
        copyWp.setDistanceToNextDecOptimized(ctrWp.getDistanceToNextDecOptimized());
        copyWp.setDistanceToEndDecOptimized(ctrWp.getDistanceToEndDecOptimized());
        copyWp.setDecelTo(ctrWp.getDecelTo());

        return copyWp;
    }
}