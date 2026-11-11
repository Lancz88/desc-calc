package com.desc.calc.msfs;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coordinates web imports with the in-game panel's polling protocol.
 * No HTTP server or cached flight plan is owned by this adapter.
 */
@Service
public class MSFSAdapter {

    private static final long HEARTBEAT_MAX_AGE_MS = 5000;

    private final Object lock = new Object();
    private final long requestTimeoutMs;

    private JsonNode panelDiagnostics = JsonNodeFactory.instance.objectNode();
    private long lastPanelPollNanos;
    private PendingRequest pendingRequest;
    private boolean shuttingDown;

    public MSFSAdapter(@Value("${msfs.request-timeout-ms:12000}") long requestTimeoutMs) {

        if (requestTimeoutMs <= 0) {
            throw new IllegalArgumentException("MSFS request timeout must be positive");
        }

        this.requestTimeoutMs = requestTimeoutMs;
    }

    // Web application requests.

    /**
     * Returns panel liveness and its last diagnostics without reading a flight plan.
     * A recent heartbeat does not prove that an aircraft or usable route is loaded.
     */
    public MSFSStatus getPanelStatus() {

        synchronized (lock) {
            long age = lastPanelPollNanos == 0 ? -1
                    : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastPanelPollNanos);

            return new MSFSStatus(!shuttingDown && age >= 0 && age < HEARTBEAT_MAX_AGE_MS,
                    age, panelDiagnostics.deepCopy());
        }
    }

    /**
     * Obtains a fresh snapshot using a request/reply exchange with the polling panel.
     * <ol>
     *     <li>MSFSService2 calls this method when the web application imports a route.</li>
     *     <li>A pending request with a unique id is created; no HTTP call is sent to the panel.</li>
     *     <li>The panel's next poll receives that id, then reads the route from the FBW FMS.</li>
     *     <li>The panel posts its result to MSFSController, which completes the matching request.</li>
     *     <li>The waiting call returns the snapshot, or fails on a panel error or timeout.</li>
     *     <li>The request is cleared in every case, allowing the next import to start fresh.</li>
     * </ol>
     * Concurrent imports are rejected; an existing snapshot is never reused.
     *
     * @return the raw FBW snapshot for FlightPlanMapper to convert into calculator waypoints
     */
    public JsonNode getFlightPlan() {

        // Register the read intent. The panel will discover it on its next poll.
        PendingRequest request = queueFlightPlanRequest();

        try {
            // Wait while other HTTP threads handle panel polls and the correlated result.
            return awaitFlightPlan(request);
        } finally {
            // Success, timeout and failure all release the slot for the next import.
            clearPendingRequest(request);
        }
    }


    // MSFS in-game panel requests.

    /**
     * Records a heartbeat and delivers the pending read command at most once.
     * The panel receives an empty object when no new command is waiting.
     */
    public Map<String, String> pollForFlightPlanRequest(JsonNode diagnostics) {

        synchronized (lock) {
            ensureNotShuttingDown();
            panelDiagnostics = diagnostics.deepCopy();
            lastPanelPollNanos = System.nanoTime();

            if (pendingRequest != null && !pendingRequest.delivered) {
                pendingRequest.delivered = true;
                return Map.of("requestId", pendingRequest.id);
            }

            return Map.of();
        }
    }

    /**
     * Releases the waiting web import with the panel's snapshot or read failure.
     * Unknown, late and duplicate results cannot complete a different request.
     */
    public void submitFlightPlanResult(String requestId, String kind, JsonNode content) {

        int status = switch (kind) {
            case "ok" -> 200;
            case "empty" -> 422;
            case "error" -> 502;
            default -> throw failure(400, "INVALID_RESULT_KIND", "Expected result kind: ok, empty or error.");
        };

        synchronized (lock) {
            ensureNotShuttingDown();

            if (pendingRequest == null || !pendingRequest.id.equals(requestId)
                    || !pendingRequest.delivered || pendingRequest.result.isDone()) {
                throw failure(409, "EXPIRED_REQUEST", "No matching pending flight plan request.");
            }

            pendingRequest.result.complete(new PanelResult(status, content));
        }
    }

    // Application lifecycle and internal request coordination.

    /** Spring invokes this hook on shutdown to release any waiting import immediately. */
    @PreDestroy
    public void onShutdown() {

        synchronized (lock) {
            shuttingDown = true;

            if (pendingRequest != null) {
                pendingRequest.result.completeExceptionally(
                        failure(503, "APPLICATION_STOPPING", "The application is stopping."));
            }
        }
    }

    private PendingRequest queueFlightPlanRequest() {

        synchronized (lock) {
            ensureNotShuttingDown();

            if (pendingRequest != null) {
                throw failure(409, "BUSY", "Another flight plan import is in progress.");
            }
            if (!getPanelStatus().panelConnected()) {
                throw failure(503, "PANEL_DISCONNECTED", "Open the GlideLink panel configured for this application and keep it visible.");
            }
            pendingRequest = new PendingRequest();
            return pendingRequest;
        }
    }

    private JsonNode awaitFlightPlan(PendingRequest request) {

        try {
            // The future is completed by submitFlightPlanResult, not by another polling loop here.
            PanelResult result = request.result.get(requestTimeoutMs, TimeUnit.MILLISECONDS);

            if (result.status() != 200) {
                throw failure(result.status(), result.content().path("error").asString("FBW_READ_FAILED"),
                        "The simulator could not return a flight plan. Check the panel status and loaded route.");
            }
            return result.content();

        } catch (TimeoutException exception) {
            throw failure(504, "PANEL_TIMEOUT", "The MSFS panel did not return a flight plan in time.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(503, "INTERRUPTED", "Flight plan import was interrupted.");
        } catch (ExecutionException exception) {
            throw failure(503, "APPLICATION_STOPPING", "The application stopped before the import completed.");
        }
    }

    private void clearPendingRequest(PendingRequest request) {

        synchronized (lock) {
            if (pendingRequest == request) {
                pendingRequest = null;
            }
        }
    }

    private void ensureNotShuttingDown() {

        if (shuttingDown) {
            throw failure(503, "APPLICATION_STOPPING", "The application is stopping.");
        }
    }

    private MSFSException failure(int status, String code, String message) {
        return new MSFSException(status, code, message);
    }

    private record PanelResult(int status, JsonNode content) {
    }

    private static class PendingRequest {

        private final String id = UUID.randomUUID().toString();
        private final CompletableFuture<PanelResult> result = new CompletableFuture<>();
        private boolean delivered;
    }
}
