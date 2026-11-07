package com.desc.calc.simbrief;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
public class SimBriefService {

    private static final String SIMBRIEF_API_BASE = "https://www.simbrief.com/api/xml.fetcher.php";
    private static final String SIMBRIEF_USER_ID = "READ IN ENV-VARS.TXT RESOURCE";

    public void presentSimBriefData(int ofpId) throws IOException, InterruptedException {

        JsonNode waypointsRaw = loadJsonData(ofpId);
        List<SimBriefWaypoint> waypoints = buildWaypoints(waypointsRaw);
        reverseWaypoints(waypoints);
        cleanWaypoints(waypoints);

        System.out.println("\n");
        System.out.println("--------------- WAYPOINT NAMES FROM ARRIVAL ---------------");
        System.out.println("\n");

        for (SimBriefWaypoint wp : waypoints) {
            System.out.println(wp.getName());
        }

        System.out.println("\n");
        System.out.println("--------------- WAYPOINT DISTANCES TO NEXT FROM ARRIVAL ---------------");
        System.out.println("\n");

        for (SimBriefWaypoint wp : waypoints) {
            System.out.println(wp.getDistanceToNext());
        }
    }

    private JsonNode loadJsonData(int ofpId) throws IOException, InterruptedException {

        String url = SIMBRIEF_API_BASE
                + "?userid=" + SIMBRIEF_USER_ID
                + "&ofp_id=" + ofpId
                + "&json=1";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        System.out.println("Fetching SimBrief JSON for OFP ID: " + ofpId);

        // Fetch the response as a String
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("Failed to fetch SimBrief JSON. HTTP status: " + response.statusCode());
            return null;
        }

        String jsonString = response.body();

        // Parse JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonString);

        JsonNode waypointsRaw = root.get("navlog").get("fix");
        if (waypointsRaw == null || !waypointsRaw.isArray()) {
            System.out.println("No waypoints found in JSON!");
            return null;
        }

        return waypointsRaw;
    }

    private List<SimBriefWaypoint> buildWaypoints(JsonNode raw) {

        List<SimBriefWaypoint> result = new ArrayList<>();
        int idx = 0;
        for (JsonNode wp : raw) {
            result.add(new SimBriefWaypoint(idx, wp.get("ident").asText(),
                    wp.get("type").asText(), wp.get("distance").asInt()));
            idx++;
        }
        return result;
    }

    private void reverseWaypoints(List<SimBriefWaypoint> wps) {
        wps.sort(Comparator.comparingInt(SimBriefWaypoint::getIdx).reversed());

        Integer prevDistance = null;
        for (SimBriefWaypoint wp : wps) {

            if (prevDistance == null) {
                prevDistance = wp.getDistanceToNext();
                wp.setDistanceToNext(-1);

            } else {
                int tmpDistance = wp.getDistanceToNext();
                wp.setDistanceToNext(prevDistance);
                prevDistance = tmpDistance;
            }
        }
    }

    private void cleanWaypoints(List<SimBriefWaypoint> wps) {

        Set<SimBriefWaypoint> wpsToDelete = new HashSet<>();

        for (SimBriefWaypoint wp : wps) {

            if (wp.getType().equals("apt")) {
                wpsToDelete.add(wp);

            } else if (wp.getName().equals("TOC") || wp.getName().equals("TOD")) {
                if (wps.size() - 1 >= wps.indexOf(wp) + 1) {
                    SimBriefWaypoint prev = wps.get(wps.indexOf(wp) + 1);
                    prev.setDistanceToNext(prev.getDistanceToNext() + wp.getDistanceToNext());
                    wpsToDelete.add(wp);
                }
            }
        }

        wps.removeAll(wpsToDelete);
    }
}
