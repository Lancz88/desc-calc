package com.desc.calc.processor.service;

import com.desc.calc.processor.obj.Waypoint;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CSVService {

    private static final String VAR_IDX = "idx";
    private static final String VAR_NAME = "name";
    private static final String VAR_DIS_TO_NEXT = "distanceToNext";
    private static final String VAR_ALT_HOLD = "altHold";
    private static final String VAR_ALT_ABOVE = "altAbove";
    private static final String VAR_ALT_BELOW = "altBelow";
    private static final String VAR_SPEED = "speed";

    public List<Waypoint> read(String fileName) {

        List<Waypoint> waypoints = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(fileName).getInputStream(), StandardCharsets.UTF_8))) {

            CSVFormat csvFormat = CSVFormat.Builder.create()
                    .setHeader(VAR_IDX, VAR_NAME, VAR_DIS_TO_NEXT, VAR_ALT_HOLD, VAR_ALT_ABOVE, VAR_ALT_BELOW, VAR_SPEED)
                    .setSkipHeaderRecord(true).get();

            CSVParser parser = CSVParser.builder()
                    .setReader(reader)
                    .setFormat(csvFormat)
                    .get();

            for (CSVRecord record : parser.getRecords()) {
                waypoints.add(new Waypoint(
                        Integer.parseInt(record.get(VAR_IDX)),
                        record.get(VAR_NAME),
                        Integer.parseInt(record.get(VAR_DIS_TO_NEXT)),
                        getVarIfPresent(record, VAR_ALT_HOLD),
                        getVarIfPresent(record, VAR_ALT_ABOVE),
                        getVarIfPresent(record, VAR_ALT_BELOW),
                        getVarIfPresent(record, VAR_SPEED))
                );
            }

            if (waypoints.isEmpty()) {
                throw new RuntimeException("No Waypoints provided");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed parsing CSV file, " + e);
        }
        return waypoints;
    }

    private Integer getVarIfPresent(CSVRecord record, String csvVar) {
        return !record.get(csvVar).isEmpty() ? Integer.parseInt(record.get(csvVar)) : null;
    }
}
