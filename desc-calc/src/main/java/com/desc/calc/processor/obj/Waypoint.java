package com.desc.calc.processor.obj;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Waypoint {

    public Waypoint(int idx,
                    String name,
                    int distanceToNext,
                    Integer altHold,
                    Integer altAbove,
                    Integer altBelow,
                    Integer speed) {

        this.idx = idx;
        this.name = name;
        this.distanceToNext = distanceToNext;
        this.altHold = altHold;
        this.altAbove = altAbove;
        this.altBelow = altBelow;
        this.speed = speed;
    }

    // Basic vars coming from CSV

    private final int idx;
    private final String name;
    private final int distanceToNext;
    private final Integer altHold;
    private final Integer altAbove;
    private final Integer altBelow;
    private final Integer speed;

    // Basic vars based on calculations

    @Setter
    private Integer altCalculated;

    @Setter
    private double angle;

    @Setter
    private Integer distanceToEnd;


    // Optional vars calculated, mainly for handling decel points

    @Setter
    private Integer distanceToNextDecOptimized;

    @Setter
    private Integer distanceToEndDecOptimized;

    @Setter
    private Integer distanceToNextBackup;

    @Setter
    private Integer distanceToEndBackup;

    @Setter
    private Integer decelTo;

    @Setter
    private String additionalMsg = null;


    public CtrConflict validateCtr() {
        return validateCtr(altCalculated);
    }

    public CtrConflict validateCtr(int altitudeToCheck) {

        CtrConflict conflict = null;

        if ((altBelow != null && altitudeToCheck > altBelow) ||
                (altHold != null && altitudeToCheck > altHold)) {

            conflict = CtrConflict.TOO_HIGH;
            conflict.setValue(altHold != null ? altHold : altBelow);

        } else if ((altAbove != null && altitudeToCheck < altAbove) ||
                (altHold != null && altitudeToCheck < altHold)) {

            conflict = CtrConflict.TOO_LOW;
            conflict.setValue(altHold != null ? altHold : altAbove);
        }
        return conflict;
    }

    public Integer getDistanceToNextDecOptimized() {
        return distanceToNextDecOptimized != null ? distanceToNextDecOptimized : distanceToNext;
    }

    public Integer getDistanceToEndDecOptimized() {
        return distanceToEndDecOptimized != null ? distanceToEndDecOptimized : distanceToEnd;
    }

    public boolean hasAltConstraints() {
        return altHold != null || altAbove != null || altBelow != null;
    }

    public void setDistanceToNextDecWithRollback(int newDistance) {
        distanceToNextBackup = distanceToNextDecOptimized;
        distanceToNextDecOptimized = newDistance;
    }

    public void setDistanceToEndDecWithRollback(int newDistance) {
        distanceToEndBackup = distanceToEndDecOptimized;
        distanceToEndDecOptimized = newDistance;
    }

    public void rollBackDecelOptimization() {
        distanceToEndDecOptimized = distanceToEndBackup;
        distanceToNextDecOptimized = distanceToNextBackup;
    }
}