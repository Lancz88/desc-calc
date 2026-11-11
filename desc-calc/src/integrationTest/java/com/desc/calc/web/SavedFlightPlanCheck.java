package com.desc.calc.web;

import com.desc.calc.SpringApp;
import com.desc.calc.web.service.*;
import com.desc.calc.web.service.save.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real local file and MVC checks; never touches the user's saved-plan archive. */
public class SavedFlightPlanCheck {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL)).build();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory(Files.createDirectories(Path.of("build/tmp")), "saved-plans-check-");
        Path file = folder.resolve("plans.json");
        try {
            var service = service(file);
            check(service.list().isEmpty() && !Files.exists(file), "empty library creates no file");
            checkLegacyArchive(file);
            var form = form();
            var saved = service.save(form, "  Practice <route>  ");
            check(saved.name().equals("Practice <route>"), "trim names, preserve plain text");
            check(JSON.valueToTree(service.load(saved.id()).plan()).equals(JSON.valueToTree(form)), "all settings and nullable waypoint fields round-trip");
            form.setCruiseAltitude(37000);
            form.getWaypoints().get(0).setDistanceToNext(19);
            check(service.load(saved.id()).plan().getCruiseAltitude() == 39000, "saved snapshot detached from later edits");
            var edited = service.save(form, "Edited");
            check(service.load(edited.id()).plan().getWaypoints().get(0).getDistanceToNext() == 19, "edited data saved");
            check(service(file).load(edited.id()).plan().getCruiseAltitude() == 37000, "new service reads durable archive");
            for (int i = 0; i < 10; i++) service.save(form, "Plan " + i);
            check(service.list().size() == 10 && service.list().get(0).name().equals("Plan 9")
                    && service.list().get(9).name().equals("Plan 0"), "retain latest ten in save order");
            expectMissing(service, saved.id());
            check(service.save(form, " ").name().equals("START to FINAL"), "automatic route name");
            String before = Files.readString(file);
            try { service.save(form, "x".repeat(81)); throw new AssertionError("long name accepted"); }
            catch (IllegalArgumentException expected) { check(Files.readString(file).equals(before), "invalid name preserves archive"); }
            form.setInitialSpeed(-1);
            try { service.save(form, "invalid"); throw new AssertionError("invalid form accepted"); }
            catch (IllegalArgumentException expected) { check(Files.readString(file).equals(before), "invalid plan preserves archive"); }
            Files.writeString(file, "broken JSON");
            try { service.save(form(), "new"); throw new AssertionError("corrupt archive overwritten"); }
            catch (FlightPlanFileStore.StorageException expected) { check(Files.readString(file).equals("broken JSON"), "corrupt file not overwritten"); }
            Files.writeString(file, before);
            var document = JSON.readTree(before);
            ((tools.jackson.databind.node.ObjectNode) document).put("version", 99);
            Files.writeString(file, document.toString());
            try { service.list(); throw new AssertionError("unsupported version accepted"); }
            catch (FlightPlanFileStore.StorageException expected) { checks++; }
            Files.writeString(file, before);
            checkHttp(file);
            try (var files = Files.list(folder)) {
                check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "no temporary write files remain");
            }
            System.out.println("PASS: " + checks + " saved-flight-plan file and MVC checks.");
        } finally {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void checkHttp(Path file) throws Exception {
        try (var app = (ServletWebServerApplicationContext) SpringApplication.run(SpringApp.class,
                "--server.port=0", "--flight-plans.file=" + file.toAbsolutePath())) {
            String root = "http://127.0.0.1:" + app.getWebServer().getPort();
            check(get(root, "/").body().contains("Saved flight plans"), "landing exposes saved plans without simulator");
            var library = get(root, "/flight-plan/saved");
            check(library.statusCode() == 200 && library.body().contains("Plan 9"), "library renders saved list");
            check(library.headers().firstValue("Cache-Control").orElse("").equals("no-store"), "library not cached");
            var editor = get(root, "/flight-plan/csv");
            check(editor.statusCode() == 200 && editor.body().contains("Save plan")
                    && editor.body().contains("Saved plans (10/10)"), "import editor exposes save and load controls");
            check(editor.body().indexOf("class=\"plan-storage\"") < editor.body().indexOf("class=\"plan-summary\""),
                    "save/load toolbar appears before generation settings");
            var response = post(root, "/flight-plan/saved", fields(form(), "Saved <flight>"));
            check(response.statusCode() == 302, "save redirects after successful write");
            String location = response.headers().firstValue("Location").orElseThrow();
            String loaded = get(root, location).body();
            check(loaded.contains("Saved &lt;flight&gt;") && !loaded.contains("Saved <flight>"), "saved name escaped in rendered editor");
            check(loaded.contains("Plan saved.") && loaded.contains("SAVED PLAN"), "successful save loads editor with feedback");
            check(loaded.contains("name=\"cruiseAltitude\"") && loaded.contains("value=\"39000\""), "loaded settings rendered");
            check(loaded.contains("value=\"FINAL\"") && loaded.contains("value=\"3000\""), "loaded route and constraints rendered");
            var invalid = fields(form(), "invalid");
            invalid.put("initialSpeed", "bad");
            var rejected = post(root, "/flight-plan/saved", invalid);
            check(rejected.statusCode() == 200 && rejected.body().contains("Please check your inputs"), "binding errors return editor");
            check(!service(file).list().get(0).name().equals("invalid"), "invalid submission not saved");
            var edit = post(root, "/flight-plan/edit", fields(form(), ""));
            check(edit.statusCode() == 200 && edit.body().contains("Save plan"), "back-to-editor supports saving");
            var changed = form();
            changed.setTdExtraDistance(17);
            changed.setPreparationLeadDistance(63);
            changed.setInitialSpeed(280);
            var second = post(root, "/flight-plan/saved", fields(changed, "Second"));
            String id = URI.create(second.headers().firstValue("Location").orElseThrow()).getPath()
                    .replace("/flight-plan/saved/", "").split(";")[0];
            var restored = service(file).load(id).plan();
            check(restored.getTdExtraDistance() == 17 && restored.getPreparationLeadDistance() == 63
                    && restored.getInitialSpeed() == 280, "modified global settings survive HTTP save");
            String restoredEditor = get(root, "/flight-plan/saved/" + id).body();
            checkInput(restoredEditor, "cruiseAltitude", "39000");
            checkInput(restoredEditor, "initialSpeed", "280");
            checkInput(restoredEditor, "tdExtraDistance", "17");
            checkInput(restoredEditor, "preparationLeadDistance", "63");
            var generated = post(root, "/flight-plan", fields(changed, "Second"));
            check(generated.statusCode() == 200 && generated.body().contains("id=\"edit-plan\""), "loaded settings generate itinerary");
            checkInput(generated.body(), "initialSpeed", "280");
            checkInput(generated.body(), "tdExtraDistance", "17");
            checkInput(generated.body(), "preparationLeadDistance", "63");
            var back = post(root, "/flight-plan/edit", hiddenFields(generated.body()));
            checkInput(back.body(), "cruiseAltitude", "39000");
            checkInput(back.body(), "initialSpeed", "280");
            checkInput(back.body(), "tdExtraDistance", "17");
            checkInput(back.body(), "preparationLeadDistance", "63");
            for (String source : List.of("SAVED PLAN", "CSV SNAPSHOT", "MSFS SNAPSHOT")) {
                var submission = fields(changed, "Second");
                submission.put("importSource", source);
                submission.put("importDetail", "Original source");
                var itinerary = post(root, "/flight-plan", submission);
                check(itinerary.body().contains("class=\"source-file\">Original source</span>"), "itinerary shows source detail: " + source);
                var returned = post(root, "/flight-plan/edit", hiddenFields(itinerary.body()));
                check(returned.body().contains(">" + source + "</span>"), "back retains source type: " + source);
                check(returned.body().contains("class=\"source-file\">Original source</span>"), "back retains source detail: " + source);
                checkInput(returned.body(), "planName", "Second");
                submission.put("initialSpeed", "bad");
                var failedSave = post(root, "/flight-plan/saved", submission);
                check(failedSave.body().contains("class=\"source-file\">Original source</span>"), "failed save retains original identity: " + source);
            }
            var loadedFields = hiddenFields(loaded);
            loadedFields.putAll(fields(form(), "Uncommitted rename"));
            var escaped = post(root, "/flight-plan", loadedFields);
            check(escaped.body().contains("class=\"source-file\">Saved &lt;flight&gt;</span>"), "unsaved rename preserves escaped loaded identity");
            var missing = get(root, "/flight-plan/saved/../../invalid");
            check(missing.statusCode() >= 400, "path input cannot load arbitrary files");
            check(get(root, "/flight-plan/saved/unknown").statusCode() == 404, "unknown saved ID returns readable 404");
            Files.writeString(file, "corrupt");
            check(get(root, "/flight-plan/saved").statusCode() == 503, "corrupt library presents readable failure");
            check(get(root, "/flight-plan/csv").statusCode() == 200, "archive error does not block editor");
            var failed = post(root, "/flight-plan/saved", fields(form(), "Retained"));
            check(failed.statusCode() == 200 && failed.body().contains("Retained")
                    && failed.body().contains("read saved plans"), "save error retains current editor inputs");
        }
    }

    private static SavedFlightPlanService service(Path path) {
        return new SavedFlightPlanService(new FlightPlanFileStore(JSON, path.toString()));
    }

    /** Literal version-1 schema from the Jackson 2 era, independent of the new writer. */
    private static void checkLegacyArchive(Path file) throws Exception {
        String legacy = """
                {"version":1,"plans":[{"id":"legacy-plan","name":"Legacy route",
                "savedAt":"2026-11-10T12:00:00Z","plan":{"cruiseAltitude":39000,
                "initialSpeed":290,"tdExtraDistance":7,"preparationLeadDistance":41,
                "waypoints":[{"idx":1,"name":"FINAL","distanceToNext":12,
                "altHold":4000,"altAbove":3000,"altBelow":5000,"speed":180},
                {"idx":2,"name":"START","distanceToNext":55,"altHold":null,
                "altAbove":null,"altBelow":null,"speed":null}]}}]}
                """;
        Files.writeString(file, legacy);
        var store = new FlightPlanFileStore(JSON, file.toString());
        var archived = store.read();
        check(archived.size() == 1 && archived.get(0).name().equals("Legacy route"),
                "Jackson 2 version-1 archive remains readable");
        check(JSON.valueToTree(archived.get(0).plan()).equals(JSON.valueToTree(form())),
                "legacy archive preserves all settings, constraints and nulls");
        store.write(archived);
        check(JSON.readTree(file.toFile()).equals(JSON.readTree(legacy)),
                "rewriting a legacy archive preserves the JSON schema and values");
        Files.delete(file);
    }

    private static WebPlanDTO form() {
        var form = new WebPlanDTO();
        form.setCruiseAltitude(39000); form.setInitialSpeed(290);
        form.setTdExtraDistance(7); form.setPreparationLeadDistance(41);
        var end = new WebPlanDTO.Row();
        end.setIdx(1); end.setName("FINAL"); end.setDistanceToNext(12); end.setAltAbove(3000);
        end.setAltBelow(5000); end.setAltHold(4000); end.setSpeed(180);
        var start = new WebPlanDTO.Row();
        start.setIdx(2); start.setName("START"); start.setDistanceToNext(55);
        form.setWaypoints(new ArrayList<>(List.of(end, start)));
        return form;
    }

    private static Map<String, String> fields(WebPlanDTO form, String name) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("planName", name);
        var tree = JSON.valueToTree(form);
        for (String key : List.of("cruiseAltitude", "initialSpeed", "tdExtraDistance", "preparationLeadDistance")) fields.put(key, tree.path(key).asString(""));
        for (int i = 0; i < form.getWaypoints().size(); i++) {
            var row = tree.path("waypoints").get(i);
            for (String key : List.of("idx", "name", "distanceToNext", "altHold", "altAbove", "altBelow", "speed")) {
                fields.put("waypoints[" + i + "]." + key, row.path(key).isNull() ? "" : row.path(key).asString(""));
            }
        }
        return fields;
    }

    private static HttpResponse<String> get(String root, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(root).resolve(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(String root, String path, Map<String, String> fields) throws Exception {
        String body = fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(java.util.stream.Collectors.joining("&"));
        return HTTP.send(HttpRequest.newBuilder(URI.create(root + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static Map<String, String> hiddenFields(String html) {
        Map<String, String> fields = new LinkedHashMap<>();
        var inputs = java.util.regex.Pattern.compile("<input[^>]*type=\"hidden\"[^>]*>").matcher(html);
        while (inputs.find()) {
            var name = java.util.regex.Pattern.compile("name=\"([^\"]+)\"").matcher(inputs.group());
            var value = java.util.regex.Pattern.compile("value=\"([^\"]*)\"").matcher(inputs.group());
            if (name.find()) fields.put(name.group(1), value.find() ? value.group(1)
                    .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<")
                    .replace("&gt;", ">").replace("&amp;", "&") : "");
        }
        return fields;
    }
    private static void checkInput(String html, String name, String value) {
        var input = java.util.regex.Pattern.compile("<input(?=[^>]*name=\"" + name + "\")[^>]*>").matcher(html);
        check(input.find() && input.group().contains("value=\"" + value + "\""),
                "rendered " + name + " must be " + value + "; actual " + (input.hasMatch() ? input.group() : "missing"));
    }
    private static void expectMissing(SavedFlightPlanService service, String id) {
        try { service.load(id); throw new AssertionError("expired plan loaded"); }
        catch (SavedFlightPlanService.PlanNotFoundException expected) { checks++; }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
