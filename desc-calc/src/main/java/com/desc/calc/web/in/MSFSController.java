package com.desc.calc.web.in;

import com.desc.calc.msfs.MSFSAdapter;
import com.desc.calc.msfs.MSFSException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Panel-only MSFS protocol served by Spring on the application's port (default 8080).
 * Panel base URL: http://127.0.0.1:8080/msfs. Responses are never cached.
 */
@RestController
@RequestMapping("/msfs")
@CrossOrigin(origins = {"null", "coui://html_ui"}, allowPrivateNetwork = "true")
public class MSFSController {

    private static final int MAX_DIAGNOSTIC_BYTES = 16 * 1024;
    private static final int MAX_PLAN_BYTES = 8 * 1024 * 1024;

    private final MSFSAdapter adapter;
    private final ObjectMapper objectMapper;

    public MSFSController(MSFSAdapter adapter, ObjectMapper objectMapper) {

        this.adapter = adapter;
        this.objectMapper = objectMapper;
    }


    /**
     * POST /msfs/poll: panel heartbeat JSON, with its current diagnostics.
     * Returns {requestId: ...} for a waiting command, otherwise an empty object.
     * The panel uses text/plain JSON to avoid unnecessary CORS preflights.
     */
    @PostMapping("/poll")
    public Map<String, String> poll(HttpServletRequest request) throws IOException {
        return adapter.pollForFlightPlanRequest(readObject(request, MAX_DIAGNOSTIC_BYTES));
    }

    /**
     * POST /msfs/flightplan/{requestId}?kind=ok|empty|error: completes a command
     * with the panel's JSON response. Unknown, late or duplicate ids return 409.
     */
    @PostMapping("/flightplan/{requestId}")
    public Map<String, Boolean> flightPlan(@PathVariable String requestId,
                                           @RequestParam String kind,
                                           HttpServletRequest request) throws IOException {

        adapter.submitFlightPlanResult(requestId, kind, readObject(request, MAX_PLAN_BYTES));
        return Map.of("accepted", true);
    }



    /** Spring invokes this hook before panel requests to validate the Host and disable caching. */
    @ModelAttribute
    public void validatePanelRequest(HttpServletRequest request, HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        String host = request.getHeader("Host");

        if (host == null || !host.matches("(127\\.0\\.0\\.1|localhost)(:[0-9]+)?")) {
            throw new MSFSException(403, "HOST_REJECTED", "Use localhost or 127.0.0.1.");
        }
    }

    /**
     * Reads a bounded body before parsing, accepting both text/plain and application/json.
     * Manual body reading here prevents Spring from buffering oversized snapshot payloads.
     */
    private JsonNode readObject(HttpServletRequest request, int limit) throws IOException {

        byte[] bytes = request.getInputStream().readNBytes(limit + 1);

        if (bytes.length > limit) {
            throw new MSFSException(413, "BODY_TOO_LARGE", "MSFS request exceeded its size limit.");
        }

        try {
            JsonNode body = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);

            if (body == null || !body.isObject()) {
                throw new MSFSException(400, "INVALID_JSON", "Expected a JSON object.");
            }

            return body;
        } catch (JsonProcessingException exception) {
            throw new MSFSException(400, "INVALID_JSON", "Expected a valid JSON object.");
        }
    }
}
