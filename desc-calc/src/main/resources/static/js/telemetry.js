/* Live itinerary estimates use the page's route, keeping different tabs independent. */
(function () {
    "use strict";

    const panel = document.getElementById("live-telemetry");
    const connection = document.getElementById("telemetry-connection");
    const status = document.getElementById("telemetry-status");
    const speed = document.getElementById("live-ground-speed");
    const waypoint = document.getElementById("live-next-waypoint");
    const distance = document.getElementById("live-waypoint-distance");
    const itineraryRows = document.querySelectorAll(".descent-table tbody tr[data-waypoint]");
    const empty = "\u2014";
    const vsToggle = document.getElementById("live-vs-toggle");
    const vsPanel = document.getElementById("live-vs-panel");
    const vsInstruments = ["", "-2"].map((suffix, index) => ({
        value: document.getElementById(`live-vs-value${suffix}`),
        angle: document.getElementById(`live-vs-angle${suffix}`),
        decrease: document.getElementById(`live-vs-decrease${suffix}`),
        increase: document.getElementById(`live-vs-increase${suffix}`),
        angleTenths: index === 0 ? 30 : 9
    }));
    let vsConnected = false;
    let vsGroundSpeed = null;
    const localTime = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

    function milestone(id) {
        const element = document.getElementById(id);
        return { waypoint: element.dataset.waypoint, offsetNm: Number(element.dataset.offset) };
    }

    // Hidden inputs retain the exact destination-first editor route used for this calculation.
    const fields = new FormData(document.getElementById("edit-plan"));
    const route = [];
    for (let index = 0; fields.has(`waypoints[${index}].name`); index++) {
        route.push({ name: fields.get(`waypoints[${index}].name`).trim(),
            distanceToNext: Number(fields.get(`waypoints[${index}].distanceToNext`)) });
    }
    const context = JSON.stringify({ route, topOfDescent: milestone("td-milestone"),
        startPreparation: milestone("prep-milestone") });

    function validNumber(value) {
        return typeof value === "number" && Number.isFinite(value) && value >= 0;
    }

    function formatDistance(value) {
        return validNumber(value) ? value.toFixed(value < 20 ? 1 : 0) : empty;
    }

    function renderVerticalSpeed() {
        vsToggle.disabled = !vsConnected;
        vsPanel.dataset.state = vsConnected ? "connected" : "disconnected";
        vsInstruments.forEach(instrument => {
        instrument.decrease.disabled = !vsConnected || instrument.angleTenths <= 1;
        instrument.increase.disabled = !vsConnected || instrument.angleTenths >= 60;
        instrument.angle.textContent = (instrument.angleTenths / 10).toFixed(1);
        // GS (NM/hour) -> feet/minute; tan(angle) gives vertical / horizontal distance.
        const rate = vsConnected && validNumber(vsGroundSpeed)
            ? Math.round(vsGroundSpeed * (1852 / 0.3048 / 60)
                * Math.tan(instrument.angleTenths / 10 * Math.PI / 180) / 100) * 100 : null;
        instrument.value.textContent = rate === null ? empty : rate === 0 ? "0" : `\u2212${rate}`;
        });
    }

    vsToggle.addEventListener("click", () => {
        if (!vsConnected) return;
        vsPanel.hidden = !vsPanel.hidden;
        vsPanel.inert = vsPanel.hidden;
        vsToggle.setAttribute("aria-expanded", String(!vsPanel.hidden));
        vsToggle.textContent = vsPanel.hidden ? "Show Live V/S" : "Hide Live V/S";
    });
    vsInstruments.forEach(instrument => {
        instrument.decrease.addEventListener("click", () => {
            if (vsConnected) { instrument.angleTenths = Math.max(1, instrument.angleTenths - 1); renderVerticalSpeed(); }
        });
        instrument.increase.addEventListener("click", () => {
            if (vsConnected) { instrument.angleTenths = Math.min(60, instrument.angleTenths + 1); renderVerticalSpeed(); }
        });
    });
    renderVerticalSpeed();

    function duration(seconds) {
        const total = Math.max(0, Math.round(seconds));
        const hours = Math.floor(total / 3600);
        const minutes = Math.floor(total % 3600 / 60);
        const rest = String(total % 60).padStart(2, "0");
        return hours ? `${hours}h ${String(minutes).padStart(2, "0")}m ${rest}s` : `${minutes}m ${rest}s`;
    }

    function renderCountdown(id, target) {
        const label = document.getElementById(id);
        if (target && Number.isFinite(target.distanceNm) && target.distanceNm <= 0) {
            label.textContent = target.distanceNm === 0 ? "Reached" : `${formatDistance(-target.distanceNm)} NM past`;
            return;
        }
        const remaining = target ? formatDistance(target.distanceNm) : empty;
        const seconds = target && validNumber(target.secondsRemaining) ? target.secondsRemaining : null;
        const elapsed = seconds === null ? empty : duration(seconds);
        const eta = seconds === null ? empty : localTime.format(new Date(Date.now() + seconds * 1000));
        label.textContent = `${remaining} NM away \u00b7 in ${elapsed} \u00b7 ETA ${eta} local`;
    }

    function nextItineraryRow(nextWaypoint) {
        if (typeof nextWaypoint !== "string" || !nextWaypoint.trim()) return null;
        const next = route.findIndex(point => point.name === nextWaypoint);
        if (next < 0 || route.some((point, index) => index !== next && point.name === nextWaypoint)) return null;

        // The editor route is destination-first: lower indices are ahead in flight order.
        for (let index = next; index >= 0; index--) {
            const rows = Array.from(itineraryRows).filter(row => row.dataset.waypoint === route[index].name);
            if (rows.length) return rows.length === 1 ? rows[0] : null;
        }
        return null;
    }

    function render(data, unavailable) {
        const readings = data && data.telemetry;
        const active = readings && readings.panelConnected === true;
        vsConnected = !!active;
        vsGroundSpeed = active ? readings.groundSpeedKnots : null;
        renderVerticalSpeed();
        connection.dataset.state = active ? "connected" : "disconnected";
        status.textContent = unavailable ? "Server unavailable." : active ? "GlideLink connected" : "GlideLink disconnected";
        speed.textContent = active && validNumber(readings.groundSpeedKnots) ? readings.groundSpeedKnots.toFixed(0) : empty;
        waypoint.textContent = active && typeof readings.nextWaypoint === "string" && readings.nextWaypoint.trim()
            ? readings.nextWaypoint : empty;
        distance.textContent = active ? formatDistance(readings.distanceToNextWaypointNm) : empty;
        const highlightedRow = active ? nextItineraryRow(readings.nextWaypoint) : null;
        itineraryRows.forEach(row => {
            const current = row === highlightedRow;
            row.classList.toggle("current-waypoint", current);
            if (current) row.setAttribute("aria-current", "step");
            else row.removeAttribute("aria-current");
        });
        renderCountdown("td-countdown", active ? data.topOfDescent : null);
        renderCountdown("prep-countdown", active ? data.startPreparation : null);
    }

    async function refresh() {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 2000);

        try {
            const response = await fetch(panel.dataset.url, { method: "POST", cache: "no-store",
                headers: { "Content-Type": "application/json" }, body: context, signal: controller.signal });
            if (!response.ok) throw new Error("Telemetry unavailable");
            const data = await response.json();
            if (!data || !data.telemetry || typeof data.telemetry.panelConnected !== "boolean") throw new Error("Invalid telemetry");
            render(data, false);
        } catch (error) {
            render(null, true);
        } finally {
            clearTimeout(timeout);
            setTimeout(refresh, 500);
        }
    }

    window.addEventListener("pageshow", () => render(null, false));
    refresh();
})();
