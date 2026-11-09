package com.desc.calc.msfs;

import com.fasterxml.jackson.databind.JsonNode;

/** Latest optional simulator readings. Missing or disconnected values are null. */
public record MSFSTelemetry(boolean panelConnected, Double groundSpeedKnots, String nextWaypoint,
                            Double distanceToNextWaypointNm) {

    public static MSFSTelemetry from(MSFSStatus status) {

        if (!status.panelConnected()) {
            return new MSFSTelemetry(false, null, null, null);
        }

        JsonNode telemetry = status.diagnostics().path("telemetry");
        JsonNode waypoint = telemetry.path("nextWaypoint");
        String name = waypoint.isTextual() && !waypoint.textValue().isBlank() ? waypoint.textValue() : null;

        return new MSFSTelemetry(true, number(telemetry.path("groundSpeedKnots")), name,
                number(telemetry.path("distanceToNextWaypointNm")));
    }

    private static Double number(JsonNode value) {
        return value.isNumber() && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0
                ? value.doubleValue() : null;
    }
}
