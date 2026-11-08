package com.desc.calc.msfs;

import com.desc.calc.processor.obj.Waypoint;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts FBW snapshots to destination-first calculator input without using predictions
 * as constraints. Geometry is in NM; each waypoint uses the NEXT leg's inbound distance.
 * Unsupported route constructs fail explicitly rather than silently losing constraints.
 */
@Component
public class FlightPlanMapper {

    private static final List<String> ROUTE_SEGMENTS = List.of("enrouteSegment",
            "arrivalEnrouteTransitionSegment", "arrivalSegment", "arrivalRunwayTransitionSegment",
            "approachViaSegment", "approachSegment", "destinationSegment");
    private static final Set<String> FIX_LEG_TYPES = Set.of("AF", "CF", "DF", "IF", "RF", "TF");

    public List<Waypoint> toWaypoints(JsonNode snapshot) {

        if (!snapshot.path("ok").asBoolean() || snapshot.path("schemaVersion").asInt() != 1) {
            throw invalid("Unsupported or unsuccessful FBW snapshot.");
        }

        JsonNode plan = snapshot.path("activePlan");
        JsonNode segments = plan.path("segments");
        String runway = plan.path("destinationRunway").asText("");

        if (!segments.isObject() || runway.isBlank()) {
            throw invalid("Load an active route with a destination runway before importing.");
        }

        List<JsonNode> legs = collectLegs(segments, runway);
        int count = legs.size() - 1;

        if (count < 2 || count > 200 || !legs.get(legs.size() - 1).path("ident").asText().equals(runway)) {
            throw invalid("The route must contain 2-200 fixes followed by the destination runway.");
        }

        List<Waypoint> waypoints = new ArrayList<>();

        for (int index = count - 1; index >= 0; index--) {
            JsonNode leg = legs.get(index);
            String name = leg.path("ident").asText();
            int distance = number(legs.get(index + 1).path("calculated"), "distanceWithTransitions", 0, 10000);

            if (distance < 1) {
                throw invalid("A leg near " + name + " rounds to zero NM; the calculator needs positive whole-mile legs.");
            }

            JsonNode altitude = constraint(leg, "pilotEnteredAltitudeConstraint");
            Altitude bounds = altitude(altitude);
            Integer speed = speed(constraint(leg, "pilotEnteredSpeedConstraint"));
            waypoints.add(new Waypoint(waypoints.size() + 1, name, distance,
                    bounds.hold(), bounds.above(), bounds.below(), speed));
        }

        return waypoints;
    }

    private List<JsonNode> collectLegs(JsonNode segments, String runway) {

        List<JsonNode> legs = new ArrayList<>();

        for (String segmentName : ROUTE_SEGMENTS) {
            JsonNode segment = segments.get(segmentName);

            if (segment == null || segment.isNull()) {
                continue;
            }

            JsonNode entries = segment.has("allLegs") ? segment.get("allLegs") : segment.path("legs");

            if (!entries.isArray()) {
                throw invalid("Unsupported FBW segment layout: " + segmentName);
            }

            for (JsonNode leg : entries) {
                String name = leg.path("ident").asText("");
                String type = leg.path("effectiveType").asText(leg.path("definition").path("type").asText());

                if (!leg.isObject() || leg.path("isDiscontinuity").asBoolean() || name.isBlank()
                        || name.length() > 32 || !FIX_LEG_TYPES.contains(type)) {
                    throw invalid("Discontinuity, hold, vector or unsupported leg in " + segmentName + ". Resolve it before import.");
                }

                legs.add(leg);

                if (name.equals(runway)) {
                    return legs;
                }
            }
        }

        return legs;
    }

    private JsonNode constraint(JsonNode leg, String pilotField) {

        return leg.hasNonNull(pilotField) ? leg.get(pilotField) : leg.path("definition");
    }

    private Altitude altitude(JsonNode source) {

        String descriptor = source.path("altitudeDescriptor").asText("");

        return switch (descriptor) {
            case "", "0" -> new Altitude(null, null, null);
            case "@", "G", "I", "X" -> new Altitude(number(source, "altitude1", 0, 60000), null, null);
            case "+", "H", "J", "V" -> new Altitude(null, number(source, "altitude1", 0, 60000), null);
            case "-", "Y" -> new Altitude(null, null, number(source, "altitude1", 0, 60000));
            case "C" -> new Altitude(null, number(source, "altitude2", 0, 60000), null);
            case "B" -> new Altitude(null, number(source, "altitude2", 0, 60000),
                    number(source, "altitude1", 0, 60000));
            default -> throw invalid("Unsupported altitude constraint descriptor: " + descriptor);
        };
    }

    private Integer speed(JsonNode source) {

        String descriptor = source.path("speedDescriptor").asText("");

        if (descriptor.isEmpty() && !source.hasNonNull("speed")) {
            return null;
        }

        if (!descriptor.equals("-")) {
            throw invalid("Only maximum-speed constraints can currently be represented by the calculator.");
        }

        return number(source, "speed", 100, 600);
    }

    private int number(JsonNode source, String field, int min, int max) {

        JsonNode value = source.path(field);
        double number = value.asDouble(Double.NaN);

        if (!value.isNumber() || !Double.isFinite(number) || number < min || number > max) {
            throw invalid("Missing or invalid " + field + " in the FBW snapshot.");
        }

        return (int) Math.round(number);
    }

    private MSFSException invalid(String message) {
        return new MSFSException(422, "UNSUPPORTED_FLIGHT_PLAN", message);
    }

    private record Altitude(Integer hold, Integer above, Integer below) {
    }
}
