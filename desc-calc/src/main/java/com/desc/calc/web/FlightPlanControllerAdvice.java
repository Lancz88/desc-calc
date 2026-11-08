package com.desc.calc.web;

import com.desc.calc.msfs.MSFSException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;

import java.util.Map;

/**
 * Keeps form-binding rules and shared MSFS error responses outside the page/panel controllers.
 * Spring invokes these hooks automatically for the selected controllers.
 */
@ControllerAdvice(assignableTypes = {HomeController.class, MSFSController.class})
public class FlightPlanControllerAdvice {

    /** Limits editor binding to route inputs and at most 200 automatically created rows. */
    @InitBinder("flightPlan")
    public void bindFlightPlan(WebDataBinder binder) {

        binder.setAutoGrowCollectionLimit(200);
        binder.setAllowedFields("cruiseAltitude", "initialSpeed", "tdExtraDistance", "preparationLeadDistance",
                "waypoints[*].idx", "waypoints[*].name", "waypoints[*].distanceToNext",
                "waypoints[*].altHold", "waypoints[*].altAbove", "waypoints[*].altBelow", "waypoints[*].speed");
    }

    /** Preserves the simulator's error code and HTTP status for both web imports and panel replies. */
    @ExceptionHandler(MSFSException.class)
    public ResponseEntity<Map<String, Object>> msfsError(MSFSException exception) {

        return ResponseEntity.status(exception.getStatus())
                .header("Cache-Control", "no-store")
                .body(Map.of("ok", false, "error", exception.getCode(), "message", exception.getMessage()));
    }
}
