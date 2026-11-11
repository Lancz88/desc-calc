package com.desc.calc.msfs;

import com.desc.calc.processor.obj.Waypoint;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Converts FBW snapshots to destination-first calculator input without using predictions
 * as constraints. Geometry is in NM; each waypoint uses the NEXT leg's inbound distance.
 * Departure constraints are intentionally omitted; later route constraints are preserved.
 */
@Component
public class FlightPlanMapper {

    private static final Set<String> DEPARTURE_SEGMENTS = Set.of("departureRunwayTransitionSegment",
            "departureSegment", "departureEnrouteTransitionSegment");
    // Flight order; origin, missed approach and alternate legs are not calculator input.
    private static final List<String> ROUTE_SEGMENTS = List.of("departureRunwayTransitionSegment",
            "departureSegment", "departureEnrouteTransitionSegment", "enrouteSegment",
            "arrivalEnrouteTransitionSegment", "arrivalSegment", "arrivalRunwayTransitionSegment",
            "approachViaSegment", "approachSegment", "destinationSegment");
    private static final Set<String> FIX_LEG_TYPES = Set.of("AF", "CF", "DF", "IF", "RF", "TF");
    // Altitude-terminated departure points use the snapshot's calculated geometry as a planning approximation.
    private static final Set<String> DEPARTURE_ALTITUDE_LEG_TYPES = Set.of("CA", "FA", "VA");

    public List<Waypoint> toWaypoints(JsonNode snapshot) {

        if (!snapshot.path("ok").asBoolean(false) || snapshot.path("schemaVersion").asInt(0) != 1) {
            throw invalid("Unsupported or unsuccessful FBW snapshot.");
        }

        JsonNode plan = snapshot.path("activePlan");
        JsonNode segments = plan.path("segments");
        String runway = plan.path("destinationRunway").asString("");

        if (!segments.isObject() || runway.isBlank()) {
            throw invalid("Load an active route with a destination runway before importing.");
        }

        List<ImportedLeg> legs = collectLegs(segments, runway);
        int waypointCount = legs.size() - 1; // The final runway supplies geometry, not an editor row.

        if (waypointCount < 2 || waypointCount > 200
                || !legs.get(legs.size() - 1).data().path("ident").asString("").equals(runway)) {
            throw invalid("The route must contain 2-200 fixes followed by the destination runway.");
        }

        List<Waypoint> waypoints = new ArrayList<>();

        // The calculator numbers waypoints backwards, starting nearest the destination.
        for (int index = waypointCount - 1; index >= 0; index--) {
            waypoints.add(toWaypoint(legs.get(index), legs.get(index + 1), waypoints.size() + 1));
        }

        return waypoints;
    }

    private Waypoint toWaypoint(ImportedLeg imported, ImportedLeg nextLeg, int index) {

        JsonNode leg = imported.data();
        // FBW distances are inbound; the calculator stores the distance OUT of this waypoint.
        int distanceToNext = Math.max(1, roundedNumber(nextLeg.data().path("calculated"),
                "distanceWithTransitions", 0, 10000));

        // Departure extends route tracking without applying climb constraints to descent planning.
        Altitude bounds = imported.departure() ? new Altitude(null, null, null)
                : readAltitude(effectiveConstraint(leg, "pilotEnteredAltitudeConstraint"));
        Integer speed = imported.departure() ? null
                : readMaximumSpeed(effectiveConstraint(leg, "pilotEnteredSpeedConstraint"));

        return new Waypoint(index, leg.path("ident").asString(""), distanceToNext,
                bounds.hold(), bounds.above(), bounds.below(), speed);
    }

    private List<ImportedLeg> collectLegs(JsonNode segments, String runway) {

        List<ImportedLeg> legs = new ArrayList<>();

        for (String segmentName : ROUTE_SEGMENTS) {
            boolean departure = DEPARTURE_SEGMENTS.contains(segmentName);
            JsonNode segment = segments.get(segmentName);

            if (segment == null || segment.isNull()) {
                continue;
            }

            JsonNode entries = segment.has("allLegs") ? segment.get("allLegs") : segment.path("legs");

            if (!entries.isArray()) {
                throw invalid("Unsupported FBW segment layout: " + segmentName);
            }

            for (JsonNode leg : entries) {
                validateLeg(leg, segmentName, departure);
                legs.add(new ImportedLeg(leg, departure));

                if (leg.path("ident").asString("").equals(runway)) {
                    return legs;
                }
            }
        }

        return legs;
    }

    private void validateLeg(JsonNode leg, String segmentName, boolean departure) {

        String name = leg.path("ident").asString("");
        String type = leg.path("effectiveType").asString(leg.path("definition").path("type").asString(""));
        boolean supportedType = FIX_LEG_TYPES.contains(type)
                || (departure && DEPARTURE_ALTITUDE_LEG_TYPES.contains(type));

        if (!leg.isObject() || leg.path("isDiscontinuity").asBoolean(false) || name.isBlank()
                || name.length() > 32 || !supportedType) {
            throw invalid("Discontinuity, hold, vector or unsupported leg in " + segmentName + ". Resolve it before import.");
        }
    }

    /** Pilot-entered constraints replace the corresponding database constraint when present. */
    private JsonNode effectiveConstraint(JsonNode leg, String pilotField) {

        return leg.hasNonNull(pilotField) ? leg.get(pilotField) : leg.path("definition");
    }

    private Altitude readAltitude(JsonNode source) {

        String descriptor = source.path("altitudeDescriptor").asString("");

        return switch (descriptor) {
            case "", "0" -> new Altitude(null, null, null);
            case "@", "G", "I", "X" -> new Altitude(roundedNumber(source, "altitude1", 0, 60000), null, null);
            case "+", "H", "J", "V" -> new Altitude(null, roundedNumber(source, "altitude1", 0, 60000), null);
            case "-", "Y" -> new Altitude(null, null, roundedNumber(source, "altitude1", 0, 60000));
            case "C" -> new Altitude(null, roundedNumber(source, "altitude2", 0, 60000), null);
            case "B" -> new Altitude(null, roundedNumber(source, "altitude2", 0, 60000),
                    roundedNumber(source, "altitude1", 0, 60000));
            default -> throw invalid("Unsupported altitude constraint descriptor: " + descriptor);
        };
    }

    private Integer readMaximumSpeed(JsonNode source) {

        String descriptor = source.path("speedDescriptor").asString("");

        if (descriptor.isEmpty() && !source.hasNonNull("speed")) {
            return null;
        }

        if (!descriptor.equals("-")) {
            throw invalid("Only maximum-speed constraints can currently be represented by the calculator.");
        }

        return roundedNumber(source, "speed", 100, 600);
    }

    /** Validate the raw value before rounding; missing or invalid values never become defaults. */
    private int roundedNumber(JsonNode source, String field, int min, int max) {

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

    private record ImportedLeg(JsonNode data, boolean departure) {
    }
}
