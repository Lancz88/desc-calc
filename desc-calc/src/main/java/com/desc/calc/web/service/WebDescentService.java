package com.desc.calc.web.service;

import com.desc.calc.processor.GlobalVar;
import com.desc.calc.processor.obj.DescentPlan;
import com.desc.calc.processor.service.DescentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adapts validated web input to the descent processor and isolates its shared settings.
 */
@Service
public class WebDescentService {

    private static final Logger log = LoggerFactory.getLogger(WebDescentService.class);
    private final DescentService descentService;

    public WebDescentService(DescentService descentService) {

        this.descentService = descentService;
    }

    /**
     * Serializes web calculations because the processor uses global settings.
     * Previous settings are restored even when a calculation fails.
     *
     * @param form validated flight plan input
     * @return the calculated itinerary or an error suitable for display
     */
    public synchronized Result generate(WebPlanDTO form) {

        int oldAltitude = GlobalVar.crzAlt;
        int oldSpeed = GlobalVar.initialSpeed;
        Integer oldTs = GlobalVar.ts;
        Integer oldGs = GlobalVar.gs;
        double oldRatio = GlobalVar.windAltRatio;
        boolean oldFl100 = GlobalVar.addFl100Wp;

        try {
            GlobalVar.crzAlt = form.getCruiseAltitude();
            GlobalVar.initialSpeed = form.getInitialSpeed();
            GlobalVar.ts = null;
            GlobalVar.gs = null;
            GlobalVar.windAltRatio = 1;
            GlobalVar.addFl100Wp = false;

            DescentPlan plan = descentService.process(form.toWaypoints());

            // Apply margins to display milestones only; keep the calculated plan untouched.
            DescentPlan.ReferencePoint calculated = plan.topOfDescent();
            int descentDistance = calculated.distance() + form.getTdExtraDistance();
            Timing timing = new Timing(
                    new DescentPlan.ReferencePoint(calculated.waypoint(), descentDistance),
                    new DescentPlan.ReferencePoint(calculated.waypoint(),
                            descentDistance + form.getPreparationLeadDistance()));

            return new Result(plan, timing, null);

        } catch (RuntimeException exception) {
            log.warn("Descent calculation failed", exception);
            return new Result(null, null, "The calculator could not build a descent for these inputs. Review the route and constraints.");

        } finally {
            GlobalVar.crzAlt = oldAltitude;
            GlobalVar.initialSpeed = oldSpeed;
            GlobalVar.ts = oldTs;
            GlobalVar.gs = oldGs;
            GlobalVar.windAltRatio = oldRatio;
            GlobalVar.addFl100Wp = oldFl100;
        }
    }

    /**
     * Contains the calculated plan and display milestones, or an error message on failure.
     */
    public record Result(DescentPlan plan, Timing timing, String error) {
    }

    /**
     * Display milestones with user-selected margins applied after the descent calculation.
     */
    public record Timing(DescentPlan.ReferencePoint topOfDescent, DescentPlan.ReferencePoint startPreparation) {
    }
}
