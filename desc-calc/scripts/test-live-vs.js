/* Isolated UI checks, without simulator or network. Run with Node. */
async function testLiveVs(source) {
    const elements = {};
    function element(id) {
        return elements[id] || (elements[id] = { dataset: {}, hidden: id === "live-vs-panel",
            textContent: "", disabled: false, events: {}, attributes: {},
            addEventListener(name, callback) { this.events[name] = callback; },
            setAttribute(name, value) { this.attributes[name] = value; } });
    }
    const document = { getElementById: element, querySelectorAll: () => [] };
    let next = { telemetry: { panelConnected: false } }, timer, fail = false, checks = 0;
    const fetch = async () => {
        if (fail) throw new Error("offline");
        return { ok: true, json: async () => next };
    };
    const setTimeout = (callback, delay) => { if (delay === 500) timer = callback; return 1; };
    class FormData { has() { return false; } }
    new Function("document", "window", "FormData", "fetch", "setTimeout", "clearTimeout", "AbortController", source)(
        document, { addEventListener() {} }, FormData, fetch, setTimeout, () => {},
        class { abort() {} });
    async function settle() { for (let i = 0; i < 12; i++) await Promise.resolve(); }
    function check(value, message) { checks++; if (!value) throw new Error(message); }
    async function sample(connected, gs) {
        next = { telemetry: { panelConnected: connected, groundSpeedKnots: gs } };
        await timer(); await settle();
    }
    await settle();
    const toggle = element("live-vs-toggle"), panel = element("live-vs-panel");
    const rate = element("live-vs-value"), angle = element("live-vs-angle");
    const plus = element("live-vs-increase"), minus = element("live-vs-decrease");
    const rate2 = element("live-vs-value-2"), angle2 = element("live-vs-angle-2");
    const plus2 = element("live-vs-increase-2"), minus2 = element("live-vs-decrease-2");
    check(toggle.disabled && plus.disabled && panel.hidden, "initially unavailable and collapsed");
    await sample(true, 450);
    check(!toggle.disabled && rate.textContent === "\u22122400", "450 KT / 3 degrees rounds to -2400");
    check(angle2.textContent === "0.9" && rate2.textContent === "\u2212700", "second instrument defaults to 0.9 degrees");
    plus2.events.click();
    check(angle2.textContent === "1.0" && rate2.textContent === "\u2212800" && angle.textContent === "3.0", "second control independently recalculates");
    minus2.events.click();
    toggle.events.click();
    check(!panel.hidden && toggle.attributes["aria-expanded"] === "true", "accessible expansion");
    check(panel.inert === false, "expanded controls are interactive");
    toggle.events.click();
    check(panel.hidden && panel.inert && toggle.attributes["aria-expanded"] === "false", "collapsed controls cannot receive focus");
    toggle.events.click();
    check(!panel.hidden && !panel.inert, "rapid toggle restores interaction");
    plus.events.click();
    check(angle.textContent === "3.1" && rate.textContent === "\u22122500", "angle immediately recalculates in tenths");
    minus.events.click();
    check(angle.textContent === "3.0", "angle decreases by one tenth");
    await sample(true, 300);
    check(rate.textContent === "\u22121600", "live GS recalculates recommendation");
    check(rate2.textContent === "\u2212500", "GS updates both instruments");
    await sample(true, 0);
    check(rate.textContent === "0", "zero GS is valid");
    for (const invalid of [undefined, -1, NaN, Infinity, "450"]) {
        await sample(true, invalid);
        check(rate.textContent === "\u2014", "missing or invalid GS clears recommendation");
    }
    await sample(false, 450);
    check(toggle.disabled && plus.disabled && minus.disabled && rate.textContent === "\u2014", "disconnect disables and clears");
    check(plus2.disabled && minus2.disabled && rate2.textContent === "\u2014", "disconnect disables and clears second instrument");
    plus.events.click();
    check(angle.textContent === "3.0", "disconnected angle cannot change");
    await sample(true, 450);
    check(!plus.disabled && rate.textContent === "\u22122400", "reconnect resumes");
    check(angle2.textContent === "0.9" && rate2.textContent === "\u2212700", "second angle survives reconnect");
    for (let i = 0; i < 100; i++) plus.events.click();
    check(angle.textContent === "6.0" && plus.disabled, "upper angle limit");
    for (let i = 0; i < 100; i++) minus.events.click();
    check(angle.textContent === "0.1" && minus.disabled, "lower angle limit");
    fail = true; await timer(); await settle();
    check(toggle.disabled && rate.textContent === "\u2014", "server failure clears recommendation");
    return `PASS: ${checks} Live V/S checks`;
}
if (typeof require === "function") {
    testLiveVs(require("fs").readFileSync(require("path").join(__dirname,
        "../src/main/resources/static/js/telemetry.js"), "utf8"))
        .then(console.log)
        .catch(error => { console.error(error); process.exitCode = 1; });
}
