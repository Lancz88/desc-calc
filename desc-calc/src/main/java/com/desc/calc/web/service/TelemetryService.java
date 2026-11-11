package com.desc.calc.web.service;

import com.desc.calc.msfs.MSFSService;
import com.desc.calc.msfs.MSFSTelemetry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TelemetryService {

    private static final double MIN_TIMING_GROUND_SPEED_KNOTS = 250;

    private final MSFSService msfsService;

    public TelemetryService(MSFSService msfsService) {
        this.msfsService = msfsService;
    }

    public MSFSTelemetry getTelemetry() {
        return msfsService.getTelemetry();
    }

    /** Combines one live sample with the editor's destination-first route and display margins. */
    public LivePlan getLivePlan(PlanContext plan) {

        MSFSTelemetry telemetry = getTelemetry();

        if (!telemetry.panelConnected() || telemetry.distanceToNextWaypointNm() == null
                || !validRoute(plan)) {
            return new LivePlan(telemetry, null, null);
        }

        int next = uniqueIndex(plan.route(), telemetry.nextWaypoint());
        if (next < 0) {
            return new LivePlan(telemetry, null, null);
        }
        double remaining = telemetry.distanceToNextWaypointNm() + distanceToArrival(plan.route(), next);

        return new LivePlan(telemetry,
                countdown(plan.route(), plan.topOfDescent(), remaining, telemetry.groundSpeedKnots()),
                countdown(plan.route(), plan.startPreparation(), remaining, telemetry.groundSpeedKnots()));
    }

    private boolean validRoute(PlanContext plan) {

        return plan != null && plan.route() != null && !plan.route().isEmpty() && plan.route().size() <= 200
                && plan.route().stream().allMatch(point -> point != null && point.name() != null
                && point.name().length() <= 32 && !point.name().isBlank()
                && Double.isFinite(point.distanceToNext()) && point.distanceToNext() >= 0
                && point.distanceToNext() <= 10000);
    }

    private Countdown countdown(List<RoutePoint> route, Milestone target, double remaining, Double speed) {

        if (target == null || !Double.isFinite(target.offsetNm()) || target.offsetNm() < 0) {
            return null;
        }

        int reference = uniqueIndex(route, target.waypoint());
        if (reference < 0) {
            return null;
        }

        // Both locations use the same route distances, so passed waypoints cancel out.
        double distance = remaining - distanceToArrival(route, reference) - target.offsetNm();

        // Same distance / GS relationship as TimerService, retaining seconds and fractional NM.
        Long seconds = speed != null && speed >= MIN_TIMING_GROUND_SPEED_KNOTS && distance > 0
                ? Math.round(distance / speed * 3600) : null;

        return new Countdown(distance, seconds);
    }

    private double distanceToArrival(List<RoutePoint> route, int index) {

        double distance = 0;
        for (int i = 0; i <= index; i++) {
            distance += route.get(i).distanceToNext();
        }
        return distance;
    }

    /** Names alone cannot distinguish repeated fixes; do not guess which occurrence is active. */
    private int uniqueIndex(List<RoutePoint> route, String name) {

        int found = -1;

        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).name().equals(name)) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    public record RoutePoint(String name, double distanceToNext) {
    }

    /** Offset is NM before the reference waypoint, including the selected display margins. */
    public record Milestone(String waypoint, double offsetNm) {
    }

    public record PlanContext(List<RoutePoint> route, Milestone topOfDescent, Milestone startPreparation) {
    }

    /** Negative distance means the milestone has been passed; time is absent below 250 KT GS. */
    public record Countdown(double distanceNm, Long secondsRemaining) {
    }

    public record LivePlan(MSFSTelemetry telemetry, Countdown topOfDescent, Countdown startPreparation) {
    }
}