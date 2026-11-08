package com.desc.calc.web;

import com.desc.calc.msfs.MSFSException;
import com.desc.calc.msfs.MSFSService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * Keeps form-binding rules and import error presentation outside the page controller.
 * Spring invokes these hooks automatically for the selected controllers.
 */
@ControllerAdvice(assignableTypes = HomeController.class)
public class FlightPlanControllerAdvice {

    private final MSFSService msfsService;

    public FlightPlanControllerAdvice(MSFSService msfsService) {

        this.msfsService = msfsService;
    }

    /** Limits editor binding to route inputs and at most 200 automatically created rows. */
    @InitBinder("flightPlan")
    public void bindFlightPlan(WebDataBinder binder) {

        binder.setAutoGrowCollectionLimit(200);
        binder.setAllowedFields("cruiseAltitude", "initialSpeed", "tdExtraDistance", "preparationLeadDistance",
                "waypoints[*].idx", "waypoints[*].name", "waypoints[*].distanceToNext",
                "waypoints[*].altHold", "waypoints[*].altAbove", "waypoints[*].altBelow", "waypoints[*].speed");
    }

    /** Returns the landing page with a dismissible import error and refreshed connection state. */
    @ExceptionHandler(MSFSException.class)
    public ModelAndView msfsError(MSFSException exception, HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        return new ModelAndView("home", Map.of(
                "panelConnected", msfsService.getStatus().panelConnected(),
                "importError", exception.getMessage()), HttpStatus.valueOf(exception.getStatus()));
    }
}
