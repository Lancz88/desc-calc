package com.desc.calc.web.service;

import com.desc.calc.processor.obj.Waypoint;
import com.desc.calc.processor.GlobalVar;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.List;

/**
 * Editable route input, kept separate from the mutable calculation waypoints.
 */
@Getter
@Setter
public class WebPlanDTO {

    private Integer cruiseAltitude = GlobalVar.DEFAULT_CRUISE_ALTITUDE;
    private Integer initialSpeed = GlobalVar.DEFAULT_INITIAL_DESCENT_SPEED;
    private Integer tdExtraDistance = GlobalVar.DEFAULT_TD_EXTRA_DISTANCE;
    private Integer preparationLeadDistance = GlobalVar.DEFAULT_PREPARATION_LEAD_DISTANCE;
    private List<Row> waypoints = new ArrayList<>();

    /**
     * Copies imported data without exposing the processor's waypoint instances to form binding.
     */
    public static WebPlanDTO from(List<Waypoint> waypoints) {

        WebPlanDTO form = new WebPlanDTO();

        for (Waypoint wp : waypoints) {
            Row row = new Row();
            row.idx = wp.getIdx();
            row.name = wp.getName();
            row.distanceToNext = wp.getDistanceToNext();
            row.altHold = wp.getAltHold();
            row.altAbove = wp.getAltAbove();
            row.altBelow = wp.getAltBelow();
            row.speed = wp.getSpeed();

            form.waypoints.add(row);
        }

        return form;
    }

    /**
     * Creates a fresh, sortable list because processing modifies both its order and its waypoints.
     */
    public List<Waypoint> toWaypoints() {

        return new ArrayList<>(waypoints.stream()
                .map(row -> new Waypoint(row.idx, row.name.trim(), row.distanceToNext,
                        row.altHold, row.altAbove, row.altBelow, row.speed))
                .toList());
    }

    /**
     * Validates numeric ranges and altitude constraints before invoking the descent processor.
     * Binding errors are handled first so incomplete numeric fields are not processed.
     */
    public void validate(Errors errors) {

        if (errors.hasErrors()) {
            return;
        }

        if (!within(cruiseAltitude, 1000, 60000) || !within(initialSpeed, 100, 600)) {
            errors.reject("settings", "Enter a cruise altitude of 1,000–60,000 ft and an initial descent speed of 100–600 kt.");
            return;
        }

        if (waypoints == null || waypoints.size() < 2 || waypoints.size() > 200) {
            errors.reject("route", "The route must contain 2–200 waypoints.");
            return;
        }

        if (!within(tdExtraDistance, 0, 1000) || !within(preparationLeadDistance, 0, 1000)) {
            errors.reject("offsets", "Enter T/D extra distance and preparation lead distance between 0 and 1,000 NM.");
            return;
        }

        for (int i = 0; i < waypoints.size(); i++) {
            Row row = waypoints.get(i);

            if (row == null || row.idx == null || row.idx != i + 1 || row.name == null
                    || row.name.isBlank() || row.name.length() > 32 || !within(row.distanceToNext, 1, 10000)
                    || !optional(row.altHold, 0, 60000) || !optional(row.altAbove, 0, 60000)
                    || !optional(row.altBelow, 0, 60000) || !optional(row.speed, 100, 600)) {
                errors.reject("waypoint", "Check waypoint " + (i + 1) + ": name, positive whole-number distance, altitude (0–60,000 ft) and speed (100–600 kt).");
                return;
            }
        }

        validateAltitudeConstraints(errors);
    }

    private void validateAltitudeConstraints(Errors errors) {

        // Visit the route in flight order and carry forward the lowest permitted ceiling.
        int ceiling = cruiseAltitude;

        for (int i = waypoints.size() - 1; i >= 0; i--) {
            Row row = waypoints.get(i);
            int lower = row.altAbove == null ? 0 : row.altAbove;
            int upper = row.altBelow == null ? 60000 : row.altBelow;

            if (row.altHold != null) {
                lower = Math.max(lower, row.altHold);
                upper = Math.min(upper, row.altHold);
            }

            ceiling = Math.min(ceiling, upper);

            if (lower > ceiling) {
                errors.reject("constraints", "Altitude constraints conflict at " + row.name + ". A descent cannot require a climb or exceed cruise altitude.");
                return;
            }
        }
    }

    private static boolean within(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private static boolean optional(Integer value, int min, int max) {
        return value == null || within(value, min, max);
    }

    /**
     * One imported waypoint with optional altitude and speed constraints.
     */
    @Getter
    @Setter
    public static class Row {

        private Integer idx;
        private String name;
        private Integer distanceToNext;
        private Integer altHold;
        private Integer altAbove;
        private Integer altBelow;
        private Integer speed;
    }
}
