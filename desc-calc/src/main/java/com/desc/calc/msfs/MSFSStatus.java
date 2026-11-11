package com.desc.calc.msfs;

import tools.jackson.databind.JsonNode;

/** Panel heartbeat status, not proof of a loaded aircraft or usable flight plan. */
public record MSFSStatus(boolean panelConnected, long lastPollAgeMs, JsonNode diagnostics) {
}
