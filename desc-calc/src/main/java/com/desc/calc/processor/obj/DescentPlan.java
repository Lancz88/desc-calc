package com.desc.calc.processor.obj;

import java.util.List;
import java.util.ArrayList;

/**
 * One calculation represented as both a console itinerary and ordered display steps.
 */
public record DescentPlan(String itinerary, ReferencePoint topOfDescent, List<Step> steps) {

    public DescentPlan {
        steps = List.copyOf(steps);
    }

    /**
     * A point located a number of nautical miles before a reference waypoint.
     */
    public record ReferencePoint(String waypoint, int distance) {

        public String getLabel() {

            return waypoint + " + " + distance;
        }
    }

    /**
     * USED BY THYMELEAF! Groups consecutive instructions at the same route location without changing flight order.
     * A later visit to a waypoint with the same name remains a separate group.
     */
    public List<WaypointActionsGroup> getWaypointActionsGroups() {

        List<WaypointActionsGroup> groups = new ArrayList<>();
        int index = 0;

        while (index < steps.size()) {
            Step first = steps.get(index);
            List<String> instructions = new ArrayList<>();

            do {
                instructions.add(steps.get(index).instruction());
                index++;
            } while (index < steps.size()
                    && steps.get(index).waypoint().equals(first.waypoint())
                    && steps.get(index).distance() == first.distance());

            groups.add(new WaypointActionsGroup(first.waypoint(), first.distanceToNext(), instructions));
        }

        return List.copyOf(groups);
    }

    /**
     * The ordered actions to perform at one waypoint occurrence in the route.
     */
    public record WaypointActionsGroup(String waypoint, int distanceToNext, List<String> instructions) {

        public WaypointActionsGroup {
            instructions = List.copyOf(instructions);
        }
    }

    /**
     * An instruction with both destination distance and the outbound leg length in NM.
     * Destination distance identifies the route occurrence when grouping actions.
     */
    public record Step(String waypoint, int distance, int distanceToNext, String instruction) {
    }
}
