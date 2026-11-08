package com.desc.calc.msfs;

import com.desc.calc.processor.obj.Waypoint;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serves the web application's simulator imports and connection status.
 * Panel HTTP requests go directly from MSFSController to MSFSAdapter2.
 */
@Service
public class MSFSService {

    private final MSFSAdapter adapter;
    private final FlightPlanMapper flightPlanMapper;

    public MSFSService(MSFSAdapter adapter, FlightPlanMapper flightPlanMapper) {

        this.adapter = adapter;
        this.flightPlanMapper = flightPlanMapper;
    }

    /** Requests a fresh snapshot and converts it to the editor's destination-first route. */
    public List<Waypoint> getFlightPlan() {
        return flightPlanMapper.toWaypoints(adapter.getFlightPlan());
    }

    /** Internal application status; intentionally not exposed as a panel HTTP endpoint. */
    public MSFSStatus getStatus() {
        return adapter.getPanelStatus();
    }
}
