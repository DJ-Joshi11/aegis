/* =====================================================================
   AEGIS UI core — shared by every page.
   - Domain constants (stages, decision types, exposure categories, agents)
   - Live store: polls the real REST API and notifies page renderers
   - Rendering helpers (chips, agent badges, sparklines, JSON)
   - Global drawers: decision detail + "inject a decision" with live trace
   ===================================================================== */
(function () {
    'use strict';

    var A = window.AEGIS = {};
    var POLL_MS = 4000;

    /* ------------------------------------------------------------ domain */
    A.STAGES = {
        ingestion: { n: '01', label: 'Ingestion', hex: '#22d3ee', href: '/pipeline/ingestion' },
        detection: { n: '02', label: 'Detection & Compliance', hex: '#f59e0b', href: '/pipeline/detection' },
        evidence:  { n: '03', label: 'Evidence Mapping', hex: '#f472b6', href: '/pipeline/evidence' },
        exposure:  { n: '04', label: 'Exposure Aggregation', hex: '#34d399', href: '/pipeline/exposure' }
    };

    A.TYPES = {
        UNDERWRITING: { label: 'Underwriting', product: 'PolicyCenter', hex: '#60a5fa', category: 'Underwriting Risk Exposure' },
        CLAIMS:       { label: 'Claims', product: 'ClaimCenter', hex: '#fbbf24', category: 'Claims Handling Exposure' },
        FRAUD_FLAG:   { label: 'Fraud flag', product: 'ClaimCenter · SIU', hex: '#f87171', category: 'Fraud & Integrity Exposure' }
    };

    A.CATS = {
        'Underwriting Risk Exposure': { short: 'Underwriting Risk', hex: '#60a5fa', from: 'UNDERWRITING' },
        'Claims Handling Exposure':   { short: 'Claims Handling', hex: '#fbbf24', from: 'CLAIMS' },
        'Fraud & Integrity Exposure': { short: 'Fraud & Integrity', hex: '#f87171', from: 'FRAUD_FLAG' },
        'General AI Decision Exposure': { short: 'General', hex: '#a1a1aa', from: 'other' }
    };
    A.CAT_ORDER = ['Underwriting Risk Exposure', 'Claims Handling Exposure', 'Fraud & Integrity Exposure', 'General AI Decision Exposure'];

    A.AGENTS = {
        'qsr-underwriting-agent-01': { type: 'UNDERWRITING', short: 'UW', tag: '01' },
        'qsr-underwriting-agent-04': { type: 'UNDERWRITING', short: 'UW', tag: '04' },
        'qsr-claims-agent-02':       { type: 'CLAIMS', short: 'CL', tag: '02' },
        'qsr-claims-agent-07':       { type: 'CLAIMS', short: 'CL', tag: '07' },
        'qsr-fraud-agent-03':        { type: 'FRAUD_FLAG', short: 'FR', tag: '03' }
    };
    A.AGENT_ORDER = Object.keys(A.AGENTS);

    A.OUTCOMES = {
        APPROVED: '#34d399', DENIED: '#f87171', REFERRED_TO_HUMAN: '#fbbf24',
        PARTIAL_APPROVAL: '#fbbf24', FLAGGED: '#f472b6', CLEARED: '#a1a1aa'
    };

    A.OPTIONS = {
        UNDERWRITING: { agents: ['qsr-underwriting-agent-01', 'qsr-underwriting-agent-04'], outcomes: ['APPROVED', 'DENIED', 'REFERRED_TO_HUMAN'],
            reasons: {
                APPROVED: 'Applicant risk score 0.62, within carrier appetite band; auto-approved per policy UW-108.',
                DENIED: 'Credit score 611 below 650 threshold; risk score 0.81 exceeds appetite; auto-denied per policy UW-114.',
                REFERRED_TO_HUMAN: 'Property in flood zone AE with no prior claims data; confidence below auto-decision threshold, referred to human underwriter.'
            } },
        CLAIMS: { agents: ['qsr-claims-agent-02', 'qsr-claims-agent-07'], outcomes: ['APPROVED', 'DENIED', 'PARTIAL_APPROVAL'],
            reasons: {
                APPROVED: 'Damage estimate $4,200 consistent with submitted photos and adjuster notes; approved in full.',
                DENIED: 'Loss occurred 11 days after policy lapse; no coverage in force at time of loss; claim denied.',
                PARTIAL_APPROVAL: 'Estimate exceeds policy sublimit for water damage; approved up to sublimit of $10,000, balance denied.'
            } },
        FRAUD_FLAG: { agents: ['qsr-fraud-agent-03'], outcomes: ['FLAGGED', 'CLEARED'],
            reasons: {
                FLAGGED: 'Claim filed 2 days after policy inception; similar loss pattern to 3 prior claims by same claimant; flagged for SIU review.',
                CLEARED: 'Claimant history and loss circumstances consistent with baseline; no fraud indicators found, cleared.'
            } }
    };

    A.JURISDICTIONS = ['US-CA', 'US-NY', 'US-TX', 'EU-DE', 'EU-FR', 'IN-MH', 'UK-LON'];

    /** Mirrors ComplianceRulesService.requiredStandardFor — display only. */
    A.STANDARDS = [
        { prefix: 'EU-', name: 'EU AI Act Art.14', full: 'EU AI Act Art.14 — human oversight & explainability', hex: '#818cf8' },
        { prefix: 'UK-', name: 'UK FCA', full: 'UK FCA AI governance principles', hex: '#22d3ee' },
        { prefix: 'IN-', name: 'IRDAI', full: 'IRDAI algorithmic decision disclosure', hex: '#fb923c' },
        { prefix: 'US-', name: 'US state rule', full: 'State insurance-AI disclosure rule', hex: '#60a5fa' }
    ];
    A.standardFor = function (j) {
        for (var i = 0; i < A.STANDARDS.length; i++) {
            if (j && j.indexOf(A.STANDARDS[i].prefix) === 0) return A.STANDARDS[i];
        }
        return { prefix: '', name: 'General disclosure', full: 'General AI decision disclosure', hex: '#a1a1aa' };
    };

    A.typeOf = function (t) { return A.TYPES[t] || { label: t || 'Unknown', product: 'Unknown', hex: '#a1a1aa', category: 'General AI Decision Exposure' }; };
    A.catOf = function (c) { return A.CATS[c] || { short: c || '—', hex: '#a1a1aa' }; };
    A.agentOf = function (id) {
        var a = A.AGENTS[id];
        if (a) return { id: id, type: a.type, short: a.short, tag: a.tag, hex: A.typeOf(a.type).hex };
        return { id: id, type: null, short: '??', tag: '', hex: '#a1a1aa' };
    };
    /** Which rule fired, inferred from AnomalyDetectionService's reason text. */
    A.ruleOf = function (flag) {
        if (!flag) return null;
        var r = flag.flagReason || '';
        if (r.indexOf('Fraud flag') === 0) return { key: 'fraud', label: 'Fraud rule', hex: '#f87171' };
        if (r.indexOf('Denial rate') === 0) return { key: 'outlier', label: 'Outlier rule', hex: '#f59e0b' };
        return { key: 'other', label: 'Rule', hex: '#a1a1aa' };
    };

    /* ------------------------------------------------------------ utils */
    A.esc = function (v) {
        return String(v == null ? '' : v)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };
    A.$ = function (s, r) { return (r || document).querySelector(s); };
    A.$$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
    A.ts = function (v) { var t = new Date(v).getTime(); return isNaN(t) ? 0 : t; };
    A.time = function (v) {
        var d = new Date(v); if (isNaN(d)) return '—';
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    };
    A.ago = function (v) {
        var s = Math.max(0, Math.round((Date.now() - A.ts(v)) / 1000));
        if (s < 5) return 'just now';
        if (s < 60) return s + 's ago';
        if (s < 3600) return Math.floor(s / 60) + 'm ago';
        if (s < 86400) return Math.floor(s / 3600) + 'h ago';
        return Math.floor(s / 86400) + 'd ago';
    };
    A.short = function (id) { return id ? String(id).slice(0, 8) : '—'; };
    A.pct = function (x, d) { return (x * 100).toFixed(d == null ? 0 : d) + '%'; };
    A.fmt = function (n) { return (n || 0).toLocaleString(); };
    A.groupCount = function (arr, fn) {
        var m = {};
        arr.forEach(function (x) { var k = fn(x); m[k] = (m[k] || 0) + 1; });
        return m;
    };

    /* ------------------------------------------------------------ html bits */
    A.chip = function (text, hex, opts) {
        opts = opts || {};
        return '<span class="chip' + (opts.mono ? ' mono' : '') + (opts.solid ? ' solid' : '') + '" style="--c:' + hex + '">' +
            (opts.dot ? '<span class="d"></span>' : '') + A.esc(text) + '</span>';
    };
    A.typeChip = function (t) { var x = A.typeOf(t); return A.chip(x.label, x.hex, { dot: true }); };
    A.productChip = function (t) { var x = A.typeOf(t); return A.chip(x.product, '#3b82f6'); };
    A.outcomeChip = function (o) { return A.chip(String(o || '—').replace(/_/g, ' '), A.OUTCOMES[o] || '#a1a1aa', { mono: true }); };
    A.catChip = function (c) { var x = A.catOf(c); return A.chip(x.short, x.hex, { dot: true }); };
    A.jurChip = function (j) { return A.chip(j || '—', '#9ca3af', { mono: true }); };
    A.stdChip = function (j) { var s = A.standardFor(j); return A.chip(s.name, s.hex); };
    A.ruleChip = function (flag) { var r = A.ruleOf(flag); return r ? A.chip(r.label, r.hex, { dot: true }) : ''; };
    A.agentHtml = function (id, compact) {
        var a = A.agentOf(id);
        return '<span class="agent"><span class="av" style="--c:' + a.hex + '">' + A.esc(a.short) + '</span>' +
            (compact ? '' : '<span class="nm">' + A.esc(id) + '</span>') + '</span>';
    };

    /** 4-dot per-decision pipeline track: ingested · detection · evidence · exposure. */
    A.trackHtml = function (d) {
        var f = A.store.flagById[d.decisionId];
        var e = A.store.evById[d.decisionId];
        var S = A.STAGES;
        var dots = [
            '<i class="on" style="--c:' + S.ingestion.hex + '" title="Ingested"></i>',
            f ? '<i class="on" style="--c:' + S.detection.hex + '" title="Flagged by detection"></i>'
              : '<i class="on" style="--c:#4b5563" title="Reviewed — cleared"></i>',
            e ? '<i class="on" style="--c:' + S.evidence.hex + '" title="Evidence recorded"></i>'
              : (f ? '<i title="Evidence pending"></i>' : '<i class="skip" title="No evidence needed"></i>'),
            '<i class="on" style="--c:' + S.exposure.hex + '" title="Counted in exposure summary"></i>'
        ];
        return '<span class="track">' + dots.join('<b></b>') + '</span>';
    };

    A.statusOf = function (d) {
        var f = A.store.flagById[d.decisionId];
        var e = A.store.evById[d.decisionId];
        if (e) return { key: 'evidence', label: 'Evidence recorded', hex: A.STAGES.evidence.hex };
        if (f) return { key: 'flagged', label: 'Flagged', hex: A.STAGES.detection.hex };
        return { key: 'cleared', label: 'Cleared', hex: '#6b7280' };
    };

    A.spark = function (values, hex, w, h) {
        w = w || 90; h = h || 32;
        if (!values || values.length < 2) values = [0, 0];
        var max = Math.max.apply(null, values.concat([1]));
        var step = w / (values.length - 1);
        var pts = values.map(function (v, i) { return [i * step, h - 2 - (v / max) * (h - 6)]; });
        var line = pts.map(function (p, i) { return (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1); }).join(' ');
        var gid = 'g' + Math.random().toString(36).slice(2, 8);
        return '<svg class="spark" viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none">' +
            '<defs><linearGradient id="' + gid + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + hex + '" stop-opacity=".35"/><stop offset="1" stop-color="' + hex + '" stop-opacity="0"/></linearGradient></defs>' +
            '<path d="' + line + ' L' + w + ' ' + h + ' L0 ' + h + ' Z" fill="url(#' + gid + ')"/>' +
            '<path d="' + line + '" fill="none" stroke="' + hex + '" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/></svg>';
    };

    /** Counts of items per time bucket (oldest → newest). */
    A.buckets = function (items, field, bucketMs, n) {
        var now = Date.now(), out = [];
        for (var i = 0; i < n; i++) out.push(0);
        items.forEach(function (x) {
            var age = now - A.ts(x[field]);
            var idx = n - 1 - Math.floor(age / bucketMs);
            if (idx >= 0 && idx < n) out[idx]++;
        });
        return out;
    };

    A.jsonHtml = function (obj) {
        var s = JSON.stringify(obj, null, 2);
        s = A.esc(s);
        return s.replace(/(&quot;(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\&]|&(?!quot;))*&quot;)(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?/g, function (m, str, _c, colon, bool) {
            if (str) return colon ? '<span class="k">' + str + '</span>' + colon : '<span class="s">' + str + '</span>';
            if (bool) return '<span class="b">' + m + '</span>';
            return '<span class="n">' + m + '</span>';
        });
    };

    A.ICON = {
        check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5L20 7"/></svg>',
        dash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round"><path d="M6 12h12"/></svg>',
        flag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 21V4h11l-2 4 2 4H5"/></svg>',
        info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>',
        shield: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l8 3v6c0 5-3.4 8.5-8 9.5C7.4 20.5 4 17 4 12V6z"/></svg>',
        arrow: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 6l6 6-6 6"/></svg>',
        empty: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18"/></svg>',
        download: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12M7 10l5 5 5-5M5 21h14"/></svg>',
        copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/></svg>'
    };

    A.emptyRow = function (cols, text) {
        return '<tr><td colspan="' + cols + '"><div class="empty">' + A.ICON.empty + A.esc(text) + '</div></td></tr>';
    };

    A.toast = function (text, hex) {
        var box = A.$('#toasts'); if (!box) return;
        var t = document.createElement('div');
        t.className = 'toast';
        t.style.setProperty('--c', hex || '#34d399');
        t.innerHTML = '<i></i><div>' + text + '</div>';
        box.appendChild(t);
        setTimeout(function () { t.style.transition = 'opacity .3s'; t.style.opacity = '0'; }, 3800);
        setTimeout(function () { t.remove(); }, 4200);
    };

    A.copy = function (text) {
        if (navigator.clipboard) navigator.clipboard.writeText(text).then(function () { A.toast('Copied to clipboard', '#a855f7'); });
    };

    /* ------------------------------------------------------------ API */
    A.fetchJson = function (url, opts) {
        return fetch(url, opts).then(function (res) {
            if (!res.ok) {
                return res.json().catch(function () { return null; }).then(function (body) {
                    var err = new Error((body && body.error) || ('Request failed: ' + res.status));
                    err.status = res.status; throw err;
                });
            }
            return res.json();
        });
    };
    function safe(url) { return A.fetchJson(url).catch(function () { return null; }); }

    /* ------------------------------------------------------------ live store */
    A.store = { decisions: [], flagged: [], evidence: [], exposure: [], byId: {}, flagById: {}, evById: {}, ready: false, lastSync: 0, ok: true };
    var subs = [];
    var seen = null;
    var paused = false;
    var syncing = null;

    A.subscribe = function (fn) {
        subs.push(fn);
        if (A.store.ready) fn(A.store, { first: true, newIds: [] });
    };

    A.sync = function () {
        if (syncing) return syncing;
        syncing = Promise.all([safe('/api/decisions'), safe('/api/flagged'), safe('/api/evidence'), safe('/api/exposure-summary')])
            .then(function (r) {
                var s = A.store;
                s.ok = r[0] !== null;
                if (!s.ok) { renderLive(); return; }
                s.decisions = r[0] || [];
                s.flagged = r[1] || [];
                s.evidence = r[2] || [];
                s.exposure = r[3] || [];
                s.byId = {}; s.flagById = {}; s.evById = {};
                s.decisions.forEach(function (d) { s.byId[d.decisionId] = d; });
                s.flagged.forEach(function (f) { s.flagById[f.decisionId] = f; });
                s.evidence.forEach(function (e) { s.evById[e.decisionId] = e; });

                var first = seen === null;
                var newIds = [];
                if (first) seen = new Set();
                s.decisions.forEach(function (d) {
                    if (!seen.has(d.decisionId)) { if (!first) newIds.push(d.decisionId); seen.add(d.decisionId); }
                });
                s.ready = true;
                s.lastSync = Date.now();
                renderBadges();
                renderLive();
                subs.forEach(function (fn) { try { fn(s, { first: first, newIds: newIds }); } catch (e) { console.error(e); } });
            })
            .finally(function () { syncing = null; });
        return syncing;
    };

    function renderBadges() {
        var s = A.store;
        var set = function (id, v) { var e = document.getElementById(id); if (e) e.textContent = v; };
        set('nb-ingestion', A.fmt(s.decisions.length));
        set('nb-detection', A.fmt(s.flagged.length));
        set('nb-evidence', A.fmt(s.evidence.length));
        set('nb-exposure', A.fmt(s.exposure.length));
    }

    function renderLive() {
        var pill = A.$('#live-pill'), txt = A.$('#live-text');
        if (!pill || !txt) return;
        pill.classList.toggle('paused', paused);
        pill.classList.toggle('down', !A.store.ok && !paused);
        if (paused) { txt.textContent = 'PAUSED · click to resume'; return; }
        if (!A.store.ok) { txt.textContent = 'OFFLINE · retrying'; return; }
        if (!A.store.lastSync) { txt.textContent = 'connecting…'; return; }
        var s = Math.round((Date.now() - A.store.lastSync) / 1000);
        txt.textContent = 'LIVE · synced ' + (s < 1 ? 'now' : s + 's ago');
    }
    A.isPaused = function () { return paused; };

    /* ------------------------------------------------------------ drawers */
    function openPanel(id) {
        A.$$('.drawer.open').forEach(function (d) { if (d.id !== id) d.classList.remove('open'); });
        A.$('#' + id).classList.add('open');
        A.$('#' + id).setAttribute('aria-hidden', 'false');
        A.$('#scrim').classList.add('open');
    }
    A.closeDrawers = function () {
        A.$$('.drawer').forEach(function (d) { d.classList.remove('open'); d.setAttribute('aria-hidden', 'true'); });
        var sc = A.$('#scrim'); if (sc) sc.classList.remove('open');
        A.$$('tr.sel').forEach(function (r) { r.classList.remove('sel'); });
        document.dispatchEvent(new CustomEvent('aegis:drawer-closed'));
    };
    A.openDrawer = function (o) {
        A.$('#drawer-eyebrow').textContent = o.eyebrow || 'Detail';
        A.$('#drawer-title').innerHTML = o.title || '';
        A.$('#drawer-body').innerHTML = o.body || '';
        A.$('#drawer-foot').innerHTML = o.foot || '';
        A.$('#drawer-foot').style.display = o.foot ? '' : 'none';
        A.$('#drawer-body').scrollTop = 0;
        openPanel('drawer');
    };

    /** Step-by-step trace of one decision through the four AEGIS stages. */
    A.traceSteps = function (d) {
        var S = A.STAGES, s = A.store;
        var f = s.flagById[d.decisionId], e = s.evById[d.decisionId];
        var ex = s.exposure.filter(function (x) { return x.agentId === d.agentId; })[0];
        var steps = [];
        steps.push({ state: 'ok', hex: S.ingestion.hex, title: '01 · Ingested',
            detail: 'Validated, given an audit row in <code>decision_audit</code>, published in-process to detection.' });
        if (f) {
            steps.push({ state: 'ok', hex: S.detection.hex, title: '02 · Flagged by detection', icon: 'flag',
                detail: A.esc(f.flagReason) });
            steps.push({ state: 'ok', hex: S.detection.hex, title: '02 · Compliance tag attached',
                detail: A.esc(f.complianceStatus) + ' <span class="muted">— a tag for human review, not a certification.</span>' });
        } else {
            steps.push({ state: 'ok', hex: '#9ca3af', title: '02 · Reviewed — cleared',
                detail: 'Neither rule fired (not a fraud flag; agent denial rate not an outlier). No compliance tag needed.' });
        }
        if (e) {
            steps.push({ state: 'ok', hex: S.evidence.hex, title: '03 · Evidence record created',
                detail: 'Mapped to <b>' + A.esc(A.catOf(e.exposureCategory).short) + '</b> · ' +
                    (e.controlGapIdentified ? 'control gap (review window missed)' : 'source: detection flag') });
        } else if (f) {
            steps.push({ state: 'pending', hex: S.evidence.hex, title: '03 · Evidence record pending', detail: 'Waiting on the evidence mapper.' });
        } else {
            steps.push({ state: 'skip', hex: '#6b7280', title: '03 · No evidence needed', detail: 'Clean decisions never produce an evidence record.' });
        }
        steps.push({ state: 'ok', hex: S.exposure.hex, title: '04 · Counted in exposure summary',
            detail: ex ? (A.esc(d.agentId) + ' now at <b>' + A.pct(ex.evidenceRate) + '</b> evidence rate over ' + ex.totalDecisions +
                ' decisions · dominant: ' + A.esc(A.catOf(ex.dominantExposureCategory).short)) : 'Agent summary updating…' });
        return steps;
    };

    A.timelineHtml = function (steps, reveal) {
        return '<ul class="timeline">' + steps.map(function (st, i) {
            var icon = st.state === 'ok' ? (st.icon === 'flag' ? A.ICON.flag : A.ICON.check) : st.state === 'skip' ? A.ICON.dash : '';
            return '<li class="' + st.state + (reveal ? ' reveal' : '') + '" style="--c:' + st.hex + (reveal ? ';animation-delay:' + (i * 260) + 'ms' : '') + '">' +
                '<span class="node">' + icon + '</span>' +
                '<div class="tt">' + st.title + '</div><div class="td">' + st.detail + '</div></li>';
        }).join('') + '</ul>';
    };

    A.openDecision = function (id) {
        var d = A.store.byId[id];
        if (!d) { A.toast('Decision not found (may have been reset on redeploy)', '#f87171'); return; }
        var t = A.typeOf(d.decisionType);
        var st = A.statusOf(d);
        var body =
            '<div class="chips" style="margin-bottom:16px">' + A.typeChip(d.decisionType) + A.outcomeChip(d.outcome) + A.jurChip(d.jurisdiction) +
            A.chip(st.label, st.hex, { dot: true }) + '</div>' +
            '<dl class="kv">' +
            '<dt>Decision ID</dt><dd class="mono" style="font-size:11.5px">' + A.esc(d.decisionId) + '</dd>' +
            '<dt>Qusar agent</dt><dd>' + A.agentHtml(d.agentId) + '</dd>' +
            '<dt>Guidewire source</dt><dd>' + A.productChip(d.decisionType) + '</dd>' +
            '<dt>Decided at</dt><dd class="mono">' + A.esc(new Date(d.timestamp).toLocaleString()) + ' <span class="muted">(' + A.ago(d.timestamp) + ')</span></dd>' +
            '<dt>Compliance tag</dt><dd>' + (A.store.flagById[id] ? A.stdChip(d.jurisdiction) : '<span class="muted">Not required — cleared</span>') + '</dd>' +
            '<dt>Exposure category</dt><dd>' + (A.store.evById[id] ? A.catChip(A.store.evById[id].exposureCategory) : '<span class="muted">— (no evidence) · would map to ' + A.esc(A.catOf(t.category).short) + '</span>') + '</dd>' +
            '</dl>' +
            '<div class="section-label label">Agent reasoning trail</div>' +
            '<div class="quote">' + A.esc(d.reasoningTrail) + '</div>' +
            '<div class="section-label label">Pipeline trace</div>' +
            A.timelineHtml(A.traceSteps(d)) +
            '<div class="section-label label">Raw decision event (as ingested)</div>' +
            '<pre class="json">' + A.jsonHtml(d) + '</pre>';
        A.openDrawer({
            eyebrow: 'Decision · ' + t.product,
            title: A.esc(t.label) + ' decision <span class="mono muted" style="font-size:12px;font-weight:500">#' + A.esc(A.short(d.decisionId)) + '</span>',
            body: body,
            foot: '<button class="btn sm" data-copy="' + A.esc(d.decisionId) + '">' + A.ICON.copy + 'Copy ID</button>' +
                '<a class="btn sm" href="/pipeline/ingestion">Ingestion</a>' +
                (A.store.flagById[id] ? '<a class="btn sm" href="/pipeline/detection">Detection</a>' : '') +
                (A.store.evById[id] ? '<a class="btn sm" href="/pipeline/evidence">Evidence</a>' : '')
        });
    };

    /* ------------------------------------------------------------ inject drawer */
    function fillSelect(sel, values, labelFn) {
        sel.innerHTML = values.map(function (v) { return '<option value="' + A.esc(v) + '">' + A.esc(labelFn ? labelFn(v) : v) + '</option>'; }).join('');
    }
    function syncInjectForm(keepAgentOutcome) {
        var type = A.$('#in-type').value;
        var o = A.OPTIONS[type];
        var agent = A.$('#in-agent').value, outcome = A.$('#in-outcome').value;
        fillSelect(A.$('#in-agent'), o.agents);
        fillSelect(A.$('#in-outcome'), o.outcomes, function (v) { return v.replace(/_/g, ' '); });
        if (keepAgentOutcome) {
            if (o.agents.indexOf(agent) >= 0) A.$('#in-agent').value = agent;
            if (o.outcomes.indexOf(outcome) >= 0) A.$('#in-outcome').value = outcome;
        }
        syncReason();
    }
    function syncReason() {
        var o = A.OPTIONS[A.$('#in-type').value];
        A.$('#in-reason').value = o.reasons[A.$('#in-outcome').value] || '';
    }
    var PRESETS = {
        fraud: { type: 'FRAUD_FLAG', agent: 'qsr-fraud-agent-03', outcome: 'FLAGGED', jur: 'EU-DE' },
        denial: { type: 'CLAIMS', agent: 'qsr-claims-agent-02', outcome: 'DENIED', jur: 'US-NY' },
        clean: { type: 'UNDERWRITING', agent: 'qsr-underwriting-agent-01', outcome: 'APPROVED', jur: 'IN-MH' }
    };
    A.openInject = function (preset) {
        A.$('#inject-trace').innerHTML = '';
        if (preset && PRESETS[preset]) applyPreset(preset);
        openPanel('inject');
    };
    function applyPreset(key) {
        var p = PRESETS[key];
        A.$('#in-type').value = p.type;
        syncInjectForm(false);
        A.$('#in-agent').value = p.agent;
        A.$('#in-outcome').value = p.outcome;
        A.$('#in-jur').value = p.jur;
        syncReason();
    }

    function submitInject(ev) {
        ev.preventDefault();
        var btn = A.$('#in-submit');
        btn.disabled = true;
        var payload = {
            agentId: A.$('#in-agent').value,
            decisionType: A.$('#in-type').value,
            outcome: A.$('#in-outcome').value,
            jurisdiction: A.$('#in-jur').value,
            reasoningTrail: A.$('#in-reason').value
        };
        var started = performance.now();
        A.$('#inject-trace').innerHTML = '<div class="section-label label">Live trace</div>' +
            A.timelineHtml([{ state: 'pending', hex: A.STAGES.ingestion.hex, title: 'POST /api/decisions', detail: 'Sending to the backend…' }]);
        A.fetchJson('/api/decisions', {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
        }).then(function (created) {
            var ms = Math.round(performance.now() - started);
            return A.sync().then(function () {
                var d = A.store.byId[created.decisionId] || created;
                var st = A.statusOf(d);
                A.$('#inject-trace').innerHTML =
                    '<div class="section-label label">Live trace · 201 Created · ' + ms + ' ms round-trip</div>' +
                    A.timelineHtml(A.traceSteps(d), true) +
                    '<div class="callout" style="--c:' + st.hex + ';margin-top:6px">' + A.ICON.info +
                    '<div><b>Result: ' + A.esc(st.label) + '.</b> ' + (st.key === 'cleared'
                        ? 'This decision passed both detection rules, so AEGIS keeps only the audit row — no evidence is generated for clean decisions.'
                        : 'This decision is now an auditable evidence record a compliance reviewer can export.') +
                    ' <a href="#" data-open-decision="' + A.esc(d.decisionId) + '" style="color:var(--text);text-decoration:underline">Open full detail</a></div></div>';
                A.toast('Decision <span class="mono">#' + A.esc(A.short(d.decisionId)) + '</span> → ' + A.esc(st.label), st.hex);
                document.dispatchEvent(new CustomEvent('aegis:injected', { detail: d }));
            });
        }).catch(function (err) {
            A.$('#inject-trace').innerHTML = '<div class="callout" style="--c:#f87171;margin-top:16px">' + A.ICON.info +
                '<div><b>Rejected.</b> ' + A.esc(err.message) + '</div></div>';
        }).finally(function () { btn.disabled = false; });
    }

    /* ------------------------------------------------------------ boot */
    function boot() {
        var page = document.body.getAttribute('data-page') || '';
        var crumb = document.body.getAttribute('data-crumb') || '';
        A.$$('[data-nav]').forEach(function (a) { a.classList.toggle('active', a.getAttribute('data-nav') === page); });
        var c = A.$('#crumbs');
        if (c) {
            c.innerHTML = page === 'overview'
                ? '<span>AEGIS</span><span class="sep">/</span><b>Pipeline overview</b>'
                : '<span>AEGIS</span><span class="sep">/</span><span class="hide-sm">Pipeline</span><span class="sep hide-sm">/</span><b>' + A.esc(crumb) + '</b>';
        }

        var mb = A.$('#menu-btn');
        if (mb) mb.addEventListener('click', function () { A.$('#sidebar').classList.toggle('open'); });

        var lp = A.$('#live-pill');
        if (lp) lp.addEventListener('click', function () { paused = !paused; renderLive(); if (!paused) A.sync(); });

        if (A.$('#inject')) {
            fillSelect(A.$('#in-jur'), A.JURISDICTIONS);
            syncInjectForm(false);
            A.$('#in-type').addEventListener('change', function () { syncInjectForm(true); });
            A.$('#in-outcome').addEventListener('change', syncReason);
            A.$('#inject-form').addEventListener('submit', submitInject);
            A.$$('[data-preset]').forEach(function (b) { b.addEventListener('click', function () { applyPreset(b.getAttribute('data-preset')); }); });
            A.$('#open-inject').addEventListener('click', function () { A.openInject(); });
        }

        document.addEventListener('click', function (e) {
            var t = e.target.closest('[data-close-drawer], #scrim');
            if (t) { A.closeDrawers(); return; }
            var od = e.target.closest('[data-open-decision]');
            if (od) { e.preventDefault(); A.openDecision(od.getAttribute('data-open-decision')); return; }
            var cp = e.target.closest('[data-copy]');
            if (cp) { A.copy(cp.getAttribute('data-copy')); return; }
            var oi = e.target.closest('[data-open-inject]');
            if (oi) { e.preventDefault(); A.openInject(oi.getAttribute('data-open-inject') || null); }
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') A.closeDrawers();
            var tag = (e.target.tagName || '').toLowerCase();
            if (e.key === 'n' && !e.metaKey && !e.ctrlKey && tag !== 'input' && tag !== 'textarea' && tag !== 'select') { A.openInject(); }
        });

        A.sync();
        setInterval(function () { if (!paused) A.sync(); }, POLL_MS);
        setInterval(renderLive, 1000);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
    else boot();
})();
