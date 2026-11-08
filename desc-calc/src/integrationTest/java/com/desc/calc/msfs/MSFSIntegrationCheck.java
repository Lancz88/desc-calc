package com.desc.calc.msfs;

import com.desc.calc.SpringApp;
import com.desc.calc.processor.obj.Waypoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Exercises the real application and panel API with a synthetic panel. */
public class MSFSIntegrationCheck {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static int checks;

    public static void main(String[] args) throws Exception {

        JsonNode snapshot = snapshot();
        checkMapper(snapshot);

        if (args.length == 2) {
            checkRecordedSnapshot(Path.of(args[0]), Path.of(args[1]));
        }

        try (var context = (ServletWebServerApplicationContext) SpringApplication.run(SpringApp.class,
                "--server.port=0", "--msfs.request-timeout-ms=1500")) {
            String web = "http://127.0.0.1:" + context.getWebServer().getPort();
            String panelApi = web + "/msfs";
            MSFSService service = context.getBean(MSFSService.class);

            check(!service.getStatus().panelConnected(), "initial disconnected");
            String landing = call(web, "GET", "/", null).body();
            check(landing.contains("Import from MSFS") && landing.contains("Use sample CSV")
                    && landing.contains("href=\"/flight-plan/csv\""), "landing offers simulator and CSV imports");
            check(Pattern.compile("<button(?=[^>]*id=\"import-flight-plan\")(?=[^>]*disabled)[^>]*>")
                    .matcher(landing).find(), "simulator import disabled without panel");
            var connection = call(web, "GET", "/flight-plan/connection", null);
            check(json(connection).isBoolean() && !json(connection).asBoolean(), "web connection state is false");
            check(connection.headers().firstValue("Cache-Control").orElse("").equals("no-store"),
                    "web connection state is not cached");
            check(call(web, "GET", "/msfs/status", null).statusCode() == 404, "no panel status endpoint");
            check(call(web, "GET", "/msfs/flightplan", null).statusCode() == 404, "no panel read endpoint");
            check(call(web, "GET", "/flight-plan", null).statusCode() == 503, "disconnected import");
            check(call(web, "GET", "/flight-plan/csv", null).body().contains("waypoints-paste.csv"), "CSV works disconnected");
            check(call(panelApi, "POST", "/poll", "[]").statusCode() == 400, "reject non-object");
            check(call(panelApi, "POST", "/poll", "{}junk").statusCode() == 400, "reject trailing JSON");
            check(call(panelApi, "POST", "/poll", "{\"x\":\"" + "x".repeat(17000) + "\"}").statusCode() == 413,
                    "bounded heartbeat payload");
            HttpRequest external = HttpRequest.newBuilder(URI.create(panelApi + "/poll"))
                    .header("Origin", "https://example.com").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            check(CLIENT.send(external, HttpResponse.BodyHandlers.ofString()).statusCode() == 403, "external origin rejected");
            HttpRequest preflight = request(panelApi, "OPTIONS", "/poll", null)
                    .header("Origin", "coui://html_ui")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Private-Network", "true")
                    .header("Access-Control-Request-Headers", "Content-Type").build();
            var cors = CLIENT.send(preflight, HttpResponse.BodyHandlers.ofString());
            check(cors.statusCode() == 200
                    && cors.headers().firstValue("Access-Control-Allow-Origin").orElse("").equals("coui://html_ui"),
                    "Spring handles panel preflight");
            check(cors.headers().firstValue("Access-Control-Allow-Private-Network").orElse("").equals("true"),
                    "panel private-network preflight");

            var heartbeat = call(panelApi, "POST", "/poll", "{\"state\":\"ready\"}");
            check(heartbeat.headers().firstValue("Access-Control-Allow-Origin").orElse("").equals("coui://html_ui"),
                    "panel receives CORS headers on heartbeat");
            check(service.getStatus().panelConnected(), "application service reports panel status");
            String connectedLanding = call(web, "GET", "/", null).body();
            check(!Pattern.compile("<button(?=[^>]*id=\"import-flight-plan\")(?=[^>]*disabled)[^>]*>")
                    .matcher(connectedLanding).find(), "simulator import enabled after heartbeat");
            check(json(call(web, "GET", "/flight-plan/connection", null)).asBoolean(), "web connection state is true");
            var imported = async(web, "/flight-plan");
            String id = command(panelApi);
            check(call(web, "GET", "/flight-plan", null).statusCode() == 409, "concurrent import rejected");
            check(!json(call(panelApi, "POST", "/poll", "{}")).has("requestId"), "command delivered once");
            check(call(panelApi, "POST", "/flightplan/wrong?kind=ok", "{}").statusCode() == 409, "unmatched result rejected");
            check(call(panelApi, "POST", "/flightplan/" + id + "?kind=ok", "[]").statusCode() == 400, "invalid result rejected");
            check(call(panelApi, "POST", "/flightplan/" + id + "?kind=ok", snapshot.toString()).statusCode() == 200,
                    "correlated result accepted");
            var page = imported.get(4, TimeUnit.SECONDS);
            check(page.statusCode() == 200 && page.body().contains("MSFS SNAPSHOT") && page.body().contains("FIXC"),
                    "controller uses fresh mapped simulator route");
            check(!page.body().contains("FF25"), "no CSV fallback in simulator import");
            check(page.headers().firstValue("Cache-Control").orElse("").equals("no-store"), "snapshot is not cached");
            check(!service.getStatus().diagnostics().toString().contains("FIXC"), "status does not retain snapshot");

            String invalidForm = formBody(page.body()).replace("initialSpeed=290", "initialSpeed=not-a-number");
            var invalidInput = call(web, "POST", "/flight-plan", invalidForm);
            check(invalidInput.statusCode() == 200 && invalidInput.body().contains("Please enter a whole number"),
                    "shared binding advice preserves numeric form errors");

            var generated = call(web, "POST", "/flight-plan", formBody(page.body()));
            check(generated.statusCode() == 200 && generated.body().contains("class=\"descent-table\""),
                    "simulator import can generate a descent using the existing editor");
            var edited = call(web, "POST", "/flight-plan/edit", formBody(generated.body()));
            check(edited.statusCode() == 200 && edited.body().contains("FIXC")
                    && edited.body().contains("class=\"waypoint-table\""), "return preserves imported route");

            var nextImport = async(web, "/flight-plan");
            String second = command(panelApi);
            check(!id.equals(second), "each request has a fresh id");
            ObjectNode changedSnapshot = snapshot.deepCopy();
            ((ObjectNode) changedSnapshot.at("/activePlan/segments/approachSegment/allLegs/0")).put("ident", "FIXD");
            call(panelApi, "POST", "/flightplan/" + second + "?kind=ok", changedSnapshot.toString());
            String nextPage = nextImport.get(4, TimeUnit.SECONDS).body();
            check(nextPage.contains("FIXD") && !nextPage.contains("FIXC"), "second import uses fresh panel data");
            for (String kind : List.of("empty", "error")) {
                var failed = async(web, "/flight-plan");
                String failedId = command(panelApi);
                call(panelApi, "POST", "/flightplan/" + failedId + "?kind=" + kind,
                        "{\"ok\":false,\"error\":\"FBW_READ_FAILED\"}");
                check(failed.get(4, TimeUnit.SECONDS).statusCode() == (kind.equals("empty") ? 422 : 502), "propagate " + kind);
            }

            var timeout = async(web, "/flight-plan");
            String lateId = command(panelApi);
            check(timeout.get(4, TimeUnit.SECONDS).statusCode() == 504, "missing result times out");
            check(call(panelApi, "POST", "/flightplan/" + lateId + "?kind=ok", snapshot.toString()).statusCode() == 409,
                    "late result rejected");
            check(call(web, "GET", "/flight-plan/csv", null).statusCode() == 200, "CSV survives panel failure");
            Thread.sleep(5100);
            check(!service.getStatus().panelConnected(), "heartbeat expires");
            check(!json(call(web, "GET", "/flight-plan/connection", null)).asBoolean(), "web connection follows expiry");
            checkShutdown(context.getBean(MSFSAdapter.class));
        } finally {
            CLIENT.close();
        }

        System.out.println("PASS: " + checks + " MSFS mapper, panel protocol and MVC checks (simulated panel).");
    }

    private static void checkMapper(JsonNode snapshot) {

        FlightPlanMapper mapper = new FlightPlanMapper();
        List<Waypoint> points = mapper.toWaypoints(snapshot);
        check(points.size() == 3 && points.get(0).getName().equals("FIXC"), "destination-first route");
        check(points.get(0).getDistanceToNext() == 6 && points.get(1).getDistanceToNext() == 9
                && points.get(2).getDistanceToNext() == 12, "next-leg geometry rounded once");
        check(points.get(0).getAltAbove() == 4000 && points.get(0).getAltHold() == null, "H constraint minimum");
        check(points.get(1).getAltHold() == 5000 && points.get(1).getSpeed() == 210, "pilot constraints override database");
        ObjectNode invalid = snapshot.deepCopy();
        ((ObjectNode) invalid.at("/activePlan/segments/enrouteSegment/allLegs/0")).put("isDiscontinuity", true);
        rejects(() -> mapper.toWaypoints(invalid), "discontinuity rejected");
        ObjectNode missing = snapshot.deepCopy();
        ((ObjectNode) missing.at("/activePlan/segments/approachSegment/allLegs/1/calculated")).remove("distanceWithTransitions");
        rejects(() -> mapper.toWaypoints(missing), "missing geometry rejected");
        ObjectNode speed = snapshot.deepCopy();
        ((ObjectNode) speed.at("/activePlan/segments/enrouteSegment/allLegs/1/pilotEnteredSpeedConstraint")).put("speedDescriptor", "+");
        rejects(() -> mapper.toWaypoints(speed), "minimum speed not silently treated as maximum");
    }

    private static void checkShutdown(MSFSAdapter adapter) throws Exception {

        adapter.pollForFlightPlanRequest(JSON.createObjectNode());
        var waitingImport = CompletableFuture.supplyAsync(() -> {
            try {
                adapter.getFlightPlan();
                return 200;
            } catch (MSFSException exception) {
                return exception.getStatus();
            }
        });

        boolean delivered = false;

        for (int attempt = 0; attempt < 100 && !delivered; attempt++) {
            delivered = adapter.pollForFlightPlanRequest(JSON.createObjectNode()).containsKey("requestId");

            if (!delivered) {
                Thread.sleep(10);
            }
        }

        check(delivered, "shutdown test has an active import");
        adapter.onShutdown();
        check(waitingImport.get(1, TimeUnit.SECONDS) == 503, "shutdown releases waiting import");
        check(!adapter.getPanelStatus().panelConnected(), "shutdown disconnects panel status");
    }
    private static void checkRecordedSnapshot(Path snapshot, Path expected) throws Exception {

        // The manually pasted terminal capture contains raw line breaks inside path strings.
        // Only this historical fixture is read leniently; live panel JSON stays strict.
        JsonNode recorded = JSON.reader()
                .with(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .readTree(Files.readString(snapshot));
        List<Waypoint> points = new FlightPlanMapper().toWaypoints(recorded);
        List<String> rows = Files.readAllLines(expected);
        check(points.size() == rows.size() - 1, "recorded snapshot count");

        for (int index = 0; index < points.size(); index++) {
            Waypoint wp = points.get(index);
            String actual = wp.getIdx() + "," + wp.getName() + "," + wp.getDistanceToNext() + ","
                    + text(wp.getAltHold()) + "," + text(wp.getAltAbove()) + "," + text(wp.getAltBelow()) + "," + text(wp.getSpeed());
            check(actual.equals(rows.get(index + 1)), "recorded snapshot row " + (index + 1));
        }
    }

    private static String text(Object value) {

        return Objects.toString(value, "");
    }

    private static JsonNode snapshot() throws Exception {

        return JSON.readTree("""
                {"ok":true,"schemaVersion":1,"activePlan":{"destinationRunway":"TEST24","segments":{
                  "enrouteSegment":{"allLegs":[
                    {"ident":"FIXA","effectiveType":"TF","definition":{},"calculated":{"distanceWithTransitions":1}},
                    {"ident":"FIXB","effectiveType":"TF","definition":{"altitudeDescriptor":"@","altitude1":6000},
                     "pilotEnteredAltitudeConstraint":{"altitudeDescriptor":"@","altitude1":5000},
                     "pilotEnteredSpeedConstraint":{"speedDescriptor":"-","speed":210},
                     "calculated":{"distanceWithTransitions":12.4}}]},
                  "approachSegment":{"allLegs":[
                    {"ident":"FIXC","effectiveType":"CF","definition":{"altitudeDescriptor":"H","altitude1":4000,"altitude2":4000},
                     "calculated":{"distanceWithTransitions":8.6}},
                    {"ident":"TEST24","effectiveType":"CF","definition":{},"calculated":{"distanceWithTransitions":6.3}}]},
                  "missedApproachSegment":{"allLegs":[{"isDiscontinuity":true}]}
                }}}
                """);
    }

    private static void rejects(Runnable action, String message) {

        try {
            action.run();
        } catch (MSFSException exception) {
            check(exception.getStatus() == 422, message);
            return;
        }

        throw new AssertionError(message);
    }

    private static String command(String panelApi) throws Exception {

        for (int attempt = 0; attempt < 100; attempt++) {
            JsonNode result = json(call(panelApi, "POST", "/poll", "{}"));

            if (result.has("requestId")) {
                return result.get("requestId").asText();
            }

            Thread.sleep(10);
        }

        throw new AssertionError("No panel command received");
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {

        return JSON.readTree(response.body());
    }

    private static CompletableFuture<HttpResponse<String>> async(String root, String path) {

        return CLIENT.sendAsync(request(root, "GET", path, null).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> call(String root, String method, String path, String body) throws Exception {

        return CLIENT.send(request(root, method, path, body).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder request(String root, String method, String path, String body) {

        boolean panelRequest = path.equals("/poll") || path.startsWith("/flightplan/");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(root + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", panelRequest ? "text/plain;charset=UTF-8" : "application/x-www-form-urlencoded")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));

        if (panelRequest) {
            builder.header("Origin", "coui://html_ui");
        }

        return builder;
    }

    private static String formBody(String html) {

        StringBuilder body = new StringBuilder();
        var inputs = Pattern.compile("<input\\b[^>]*>").matcher(html);

        while (inputs.find()) {
            var name = Pattern.compile("name=\"([^\"]+)\"").matcher(inputs.group());
            var value = Pattern.compile("value=\"([^\"]*)\"").matcher(inputs.group());

            if (name.find()) {
                if (!body.isEmpty()) {
                    body.append('&');
                }

                body.append(URLEncoder.encode(name.group(1), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(value.find() ? value.group(1) : "", StandardCharsets.UTF_8));
            }
        }

        return body.toString();
    }

    private static void check(boolean condition, String message) {

        checks++;

        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
