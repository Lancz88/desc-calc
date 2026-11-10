/* Isolated telemetry UI checks; no simulator or network. Run with Node. */
async function testItineraryHighlight(source) {
    const elements = {}, rows = [], routeNames = ["END", "D", "C", "B", "A", "START", "REPEAT", "REPEAT"];
    function element(id) {
        return elements[id] || (elements[id] = { dataset: {}, attributes: {}, events: {},
            addEventListener(name, callback) { this.events[name] = callback; },
            setAttribute(name, value) { this.attributes[name] = value; },
            removeAttribute(name) { delete this.attributes[name]; },
            classList: { toggle(name, active) { this[name] = active; } } });
    }
    // Flight order is A -> B -> C -> D -> END; only B and D have actions.
    for (const name of ["B", "D"]) {
        const row = element(`row-${name}`);
        row.dataset.waypoint = name;
        rows.push(row);
    }
    class FormData {
        has(key) { return routeNames.some((_, index) => key === `waypoints[${index}].name`); }
        get(key) {
            const index = Number(key.match(/\[(\d+)\]/)[1]);
            return key.endsWith(".name") ? routeNames[index] : "10";
        }
    }
    let next = { telemetry: { panelConnected: false } }, timer, fail = false, checks = 0;
    const pageEvents = {};
    new Function("document", "window", "FormData", "fetch", "setTimeout", "clearTimeout", "AbortController", source)(
        { getElementById: element, querySelectorAll: () => rows },
        { addEventListener(name, callback) { pageEvents[name] = callback; } }, FormData,
        async () => { if (fail) throw new Error("offline"); return { ok: true, json: async () => next }; },
        (callback, delay) => { if (delay === 500) timer = callback; return 1; }, () => {}, class { abort() {} });
    async function settle() { for (let i = 0; i < 12; i++) await Promise.resolve(); }
    function check(value, message) { checks++; if (!value) throw new Error(message); }
    function highlighted() { return rows.filter(row => row.classList["current-waypoint"]); }
    async function sample(name, connected = true) {
        next = { telemetry: { panelConnected: connected, nextWaypoint: name, groundSpeedKnots: 300 } };
        await timer(); await settle();
    }
    await settle();
    check(highlighted().length === 0, "initial disconnect has no highlight");
    await sample("A");
    check(highlighted()[0] === rows[0], "waypoint without actions selects first upcoming action B");
    check(rows[0].attributes["aria-current"] === "step", "highlight is accessible");
    await sample("B");
    check(highlighted()[0] === rows[0], "next waypoint's own action is included");
    await sample("C");
    check(highlighted().length === 1 && highlighted()[0] === rows[1], "passing B advances to D across intermediate C");
    check(!rows[0].attributes["aria-current"], "previous row loses accessible current state");
    await sample("D");
    check(highlighted()[0] === rows[1], "last action remains selected while approaching its waypoint");
    await sample("END");
    check(highlighted().length === 0, "after final action no earlier row remains selected");
    for (const name of ["UNKNOWN", "", undefined, "REPEAT"]) {
        await sample("A"); await sample(name);
        check(highlighted().length === 0, "unknown, absent or ambiguous next waypoint clears highlight");
    }
    await sample("A", false);
    check(highlighted().length === 0, "disconnect cannot highlight an otherwise matching route");
    await sample("C");
    check(highlighted()[0] === rows[1], "reconnect restores the upcoming group");
    pageEvents.pageshow();
    check(highlighted().length === 0, "restored page clears stale highlight");
    await sample("A"); fail = true; await timer(); await settle();
    check(highlighted().length === 0, "server error clears highlight");
    return `PASS: ${checks} itinerary highlight checks`;
}
if (typeof require === "function") {
    testItineraryHighlight(require("fs").readFileSync(require("path").join(__dirname,
        "../src/main/resources/static/js/telemetry.js"), "utf8"))
        .then(console.log).catch(error => { console.error(error); process.exitCode = 1; });
}
