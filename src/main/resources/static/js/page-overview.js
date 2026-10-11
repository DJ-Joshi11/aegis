/* Pipeline overview: live flow map, inspector, walkthrough, stream, stage cards. */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var S = A.STAGES;

    var AGENT_NODE = {
        'qsr-underwriting-agent-01': 'uw01', 'qsr-underwriting-agent-04': 'uw04',
        'qsr-claims-agent-02': 'cl02', 'qsr-claims-agent-07': 'cl07', 'qsr-fraud-agent-03': 'fr03'
    };
    var AGENT_NODES = ['uw01', 'uw04', 'cl02', 'cl07', 'fr03'];

    /* [from, to, colour, style] — colour drives the animated flow overlay */
    var EDGES = [
        ['pc', 'uw01', '#3b82f6'], ['pc', 'uw04', '#3b82f6'],
        ['cc', 'cl02', '#3b82f6'], ['cc', 'cl07', '#3b82f6'], ['cc', 'fr03', '#3b82f6'],
        ['uw01', 'ing', '#a855f7'], ['uw04', 'ing', '#a855f7'], ['cl02', 'ing', '#a855f7'], ['cl07', 'ing', '#a855f7'], ['fr03', 'ing', '#a855f7'],
        ['ing', 'det', S.ingestion.hex],
        ['det', 'ev', S.detection.hex],
        ['det', 'clr', '#6b7280', 'muted'],
        ['ev', 'exp', S.evidence.hex],
        ['clr', 'exp', '#6b7280', 'muted'],
        ['exp', 'bundle', S.exposure.hex], ['exp', 'summary', S.exposure.hex], ['exp', 'reviewer', S.exposure.hex]
    ];

    var ZONES = [
        { cols: ['gw'], label: 'Guidewire core', hex: '#3b82f6' },
        { cols: ['qsr'], label: 'Qusar runtime', hex: '#a855f7' },
        { cols: ['s1', 's2', 's3', 's4'], label: 'AEGIS · this project', hex: '#e5e7eb', strong: true },
        { cols: ['out'], label: 'Outputs', hex: '#9ca3af' }
    ];

    /* ------------------------------------------------------------ inspector content */
    function st() { return A.store; }
    function rate(a, b) { return b ? A.pct(a / b) : '—'; }
    function countType(t) { return st().decisions.filter(function (d) { return d.decisionType === t; }).length; }
    function agentDecisions(id) { return st().decisions.filter(function (d) { return d.agentId === id; }); }
    function gaps() { return st().evidence.filter(function (e) { return e.controlGapIdentified; }).length; }

    function agentInfo(id, node) {
        var a = A.agentOf(id), t = A.typeOf(a.type);
        return {
            layer: 'Qusar agent', hex: a.hex, title: id,
            what: 'An autonomous Qusar agent issuing ' + t.label.toLowerCase() + ' decisions inside ' + t.product + '. Not built by us — AEGIS only consumes what it emits.',
            why: 'Qusar governs how this agent decides. AEGIS watches what it decided, independently.',
            api: 'emits → POST /api/decisions',
            metrics: function () {
                var ds = agentDecisions(id);
                var den = ds.filter(function (d) { return d.outcome === 'DENIED'; }).length;
                var ex = st().exposure.filter(function (x) { return x.agentId === id; })[0];
                return [['Decisions made', ds.length], ['Denials', den + ' (' + rate(den, ds.length) + ')'],
                    ['Evidence records', ex ? ex.evidenceCount : 0], ['Evidence rate', ex ? A.pct(ex.evidenceRate) : '—']];
            },
            link: { href: '/pipeline/exposure', label: 'See agent exposure' }
        };
    }

    var INFO = {
        pc: { layer: 'Guidewire core', hex: '#3b82f6', title: 'PolicyCenter',
            what: "Guidewire's policy system: new business, renewals and underwriting. Qusar underwriting agents decide here — approve, deny or refer to a human.",
            why: 'Every underwriting call an agent makes here becomes a decision event AEGIS can audit.',
            api: 'source of UNDERWRITING decisions',
            metrics: function () { return [['Underwriting decisions', countType('UNDERWRITING')], ['Agents', 2]]; } },
        cc: { layer: 'Guidewire core', hex: '#3b82f6', title: 'ClaimCenter',
            what: 'Claims from first notice of loss (FNOL) through adjudication. Qusar claims agents approve, deny or partially approve; the SIU fraud agent flags suspicious claims.',
            why: 'Claim denials and fraud flags are the most consequential calls an AI can make for a policyholder.',
            api: 'source of CLAIMS + FRAUD_FLAG decisions',
            metrics: function () { return [['Claims decisions', countType('CLAIMS')], ['Fraud-flag decisions', countType('FRAUD_FLAG')], ['Agents', 3]]; } },
        bc: { layer: 'Guidewire core', hex: '#64748b', title: 'BillingCenter',
            what: "Guidewire's billing system. Shown for completeness — no Qusar agent emits billing decisions in this build.",
            why: 'Same DecisionEvent contract would apply: plugging in a billing agent needs zero changes downstream.',
            api: 'roadmap · not wired',
            metrics: function () { return [['Decisions', '—']]; } },
        ing: { layer: 'AEGIS · Stage 01', hex: S.ingestion.hex, title: 'Ingestion',
            what: 'Receives every decision, validates required fields, generates an ID/timestamp if missing, rejects duplicates, and persists an immutable audit row — independent of the agent\'s own logs.',
            why: "You can't evidence a decision that was never recorded outside the agent's black box.",
            api: 'POST /api/decisions · GET /api/decisions',
            metrics: function () {
                var last5 = st().decisions.filter(function (d) { return Date.now() - A.ts(d.timestamp) < 300000; }).length;
                return [['Decisions logged', A.fmt(st().decisions.length)], ['Last 5 minutes', last5], ['Agents seen', Object.keys(A.groupCount(st().decisions, function (d) { return d.agentId; })).length]];
            },
            link: { href: S.ingestion.href, label: 'Open Ingestion' } },
        det: { layer: 'AEGIS · Stage 02', hex: S.detection.hex, title: 'Detection & Compliance',
            what: 'Runs synchronously on every decision. Rule 1: a FRAUD_FLAG/FLAGGED decision always routes to review. Rule 2: an agent whose denial rate exceeds 50% over 5+ decisions has its latest denial flagged. Flagged decisions are tagged with their jurisdiction\'s disclosure standard.',
            why: 'Catches what needs a second look without slowing the agent down. The tag tells a reviewer which bar to check against — it never certifies.',
            api: 'GET /api/flagged',
            metrics: function () {
                var f = st().flagged;
                var fr = f.filter(function (x) { var r = A.ruleOf(x); return r && r.key === 'fraud'; }).length;
                return [['Flagged', f.length + ' (' + rate(f.length, st().decisions.length) + ')'], ['Fraud rule', fr], ['Outlier rule', f.length - fr]];
            },
            link: { href: S.detection.href, label: 'Open Detection' } },
        clr: { layer: 'AEGIS · outcome', hex: '#9ca3af', title: 'Cleared decisions',
            what: 'Decisions where neither rule fired. AEGIS keeps the audit row but generates no evidence — clean decisions still count toward each agent\'s exposure denominator.',
            why: 'Evidence volume stays proportional to real risk, not raw decision volume.',
            api: '—',
            metrics: function () { var n = st().decisions.length - st().flagged.length; return [['Cleared', Math.max(0, n)], ['Share of all decisions', rate(Math.max(0, n), st().decisions.length)]]; } },
        ev: { layer: 'AEGIS · Stage 03', hex: S.evidence.hex, title: 'Evidence Mapping',
            what: 'Turns every flagged decision into an audit evidence record mapped to a broad exposure category: Underwriting Risk, Claims Handling, or Fraud & Integrity. A 10s sweep also records any decision left unreviewed past 45s as a control gap.',
            why: 'No AI decision that needed a second look can disappear without leaving evidence.',
            api: 'GET /api/evidence',
            metrics: function () {
                var by = A.groupCount(st().evidence, function (e) { return e.exposureCategory; });
                return [['Evidence records', st().evidence.length], ['Underwriting Risk', by['Underwriting Risk Exposure'] || 0],
                    ['Claims Handling', by['Claims Handling Exposure'] || 0], ['Fraud & Integrity', by['Fraud & Integrity Exposure'] || 0], ['Review gaps', gaps()]];
            },
            link: { href: S.evidence.href, label: 'Open Evidence Mapping' } },
        exp: { layer: 'AEGIS · Stage 04', hex: S.exposure.hex, title: 'Exposure Aggregation',
            what: 'Rolls evidence up per agent: decisions seen, evidence records, evidence rate and dominant exposure category — plus a downloadable audit bundle.',
            why: 'A carrier can\'t manage AI exposure it can\'t see. Informational only: no premium, no coverage term.',
            api: 'GET /api/exposure-summary · GET /api/evidence/export',
            metrics: function () {
                var x = st().exposure, top = x[0];
                var avg = x.length ? x.reduce(function (s, a) { return s + a.evidenceRate; }, 0) / x.length : 0;
                return [['Agents monitored', x.length], ['Avg evidence rate', A.pct(avg)], ['Highest', top ? top.agentId.replace('qsr-', '') + ' · ' + A.pct(top.evidenceRate) : '—']];
            },
            link: { href: S.exposure.href, label: 'Open Exposure Aggregation' } },
        bundle: { layer: 'Output', hex: '#e5e7eb', title: 'Audit evidence bundle',
            what: 'A single JSON file with every evidence record plus the exposure summary, ready to attach to a compliance review or hand to a regulator or reinsurer.',
            why: 'This file — not a payout or a price — is AEGIS\'s actual deliverable.',
            api: 'GET /api/evidence/export',
            metrics: function () { return [['Records in bundle', st().evidence.length], ['Agents in bundle', st().exposure.length]]; },
            link: { href: '/api/evidence/export', label: 'Download bundle', ext: true } },
        summary: { layer: 'Output', hex: '#e5e7eb', title: 'Exposure summary',
            what: 'Per-agent exposure signal, worst first. A carrier\'s compliance or underwriting team decides what — if anything — to do with it.',
            why: 'Visibility first; the pricing decision stays with Guidewire\'s rating engines.',
            api: 'GET /api/exposure-summary',
            metrics: function () { return [['Agents', st().exposure.length]]; },
            link: { href: S.exposure.href, label: 'Open Exposure Aggregation' } },
        reviewer: { layer: 'Output · human', hex: '#e5e7eb', title: 'Human reviewer',
            what: 'A compliance officer or underwriter who receives the evidence, checks it against the tagged standard, and makes the actual call.',
            why: 'Humans stay accountable for compliance and coverage — AEGIS just makes sure they see the right decisions.',
            api: 'consumes the bundle',
            metrics: function () { return [['Items with evidence', st().evidence.length], ['Control gaps to chase', gaps()]]; } }
    };
    AGENT_NODES.forEach(function (n) {
        var id = Object.keys(AGENT_NODE).filter(function (k) { return AGENT_NODE[k] === n; })[0];
        INFO[n] = agentInfo(id, n);
    });

    var selected = 'ing';

    function renderInspector() {
        var i = INFO[selected];
        if (!i) return;
        var metrics = (i.metrics ? i.metrics() : []).map(function (m) {
            return '<div class="m"><span>' + A.esc(m[0]) + '</span><b>' + A.esc(m[1]) + '</b></div>';
        }).join('');
        $('#inspector').innerHTML =
            '<div><span class="chip" style="--c:' + i.hex + '"><span class="d"></span>' + A.esc(i.layer) + '</span>' +
            '<h4>' + A.esc(i.title) + '</h4><p>' + A.esc(i.what) + '</p></div>' +
            '<div><div class="label" style="margin-bottom:10px">Live</div><div class="metric-list">' + metrics + '</div></div>' +
            '<div><div class="label" style="margin-bottom:8px">Why it matters</div><p class="why">' + A.esc(i.why) + '</p>' +
            '<div class="label" style="margin:12px 0 6px">Interface</div><div class="mini-pill" style="display:inline-block">' + A.esc(i.api) + '</div>' +
            (i.link ? '<div style="margin-top:14px"><a class="btn sm" href="' + i.link.href + '"' + (i.link.ext ? ' target="_blank" rel="noopener"' : '') + '>' + A.esc(i.link.label) + ' ' + A.ICON.arrow + '</a></div>' : '') +
            '</div>';
        $$('.fnode').forEach(function (n) { n.classList.toggle('sel', n.getAttribute('data-node') === selected); });
    }

    /* ------------------------------------------------------------ geometry */
    var paths = {};
    function rel(el) {
        var c = $('#flow').getBoundingClientRect(), r = el.getBoundingClientRect();
        return { x: r.left - c.left, y: r.top - c.top, w: r.width, h: r.height };
    }
    function node(id) { return $('.fnode[data-node="' + id + '"]'); }

    function layout() {
        var flow = $('#flow'), svg = $('#flow-svg');
        if (!flow) return;
        svg.setAttribute('width', flow.scrollWidth);
        svg.setAttribute('height', flow.scrollHeight);
        var ns = 'http://www.w3.org/2000/svg';

        // zones
        var zg = $('#zones'); zg.innerHTML = '';
        ZONES.forEach(function (z) {
            var rs = [];
            z.cols.forEach(function (c) { $$('[data-col="' + c + '"] .fnode').forEach(function (n) { rs.push(rel(n)); }); });
            if (!rs.length) return;
            var x1 = Math.min.apply(null, rs.map(function (r) { return r.x; })) - 10;
            var x2 = Math.max.apply(null, rs.map(function (r) { return r.x + r.w; })) + 10;
            var y1 = 14, y2 = flow.clientHeight - (flow.classList.contains('walking') ? 134 : 12);
            var rect = document.createElementNS(ns, 'rect');
            rect.setAttribute('x', x1); rect.setAttribute('y', y1); rect.setAttribute('width', x2 - x1); rect.setAttribute('height', y2 - y1);
            rect.setAttribute('rx', 14);
            rect.setAttribute('fill', z.strong ? 'rgba(255,255,255,0.018)' : 'transparent');
            rect.setAttribute('stroke', z.hex); rect.setAttribute('stroke-opacity', z.strong ? .22 : .14);
            rect.setAttribute('stroke-dasharray', z.strong ? '0' : '4 5');
            zg.appendChild(rect);
            var t = document.createElementNS(ns, 'text');
            t.setAttribute('x', x1 + 12); t.setAttribute('y', y1 + 18);
            t.setAttribute('class', 'zone-label'); t.setAttribute('fill', z.hex); t.setAttribute('fill-opacity', z.strong ? .85 : .7);
            t.textContent = z.label;
            zg.appendChild(t);
        });

        // edges
        var eg = $('#edges'); eg.innerHTML = ''; paths = {};
        EDGES.forEach(function (e) {
            var a = node(e[0]), b = node(e[1]);
            if (!a || !b) return;
            var ra = rel(a), rb = rel(b);
            var x1 = ra.x + ra.w, y1 = ra.y + ra.h / 2, x2 = rb.x, y2 = rb.y + rb.h / 2;
            var dx = Math.max(24, (x2 - x1) * 0.55);
            var d = 'M' + x1 + ' ' + y1 + ' C' + (x1 + dx) + ' ' + y1 + ', ' + (x2 - dx) + ' ' + y2 + ', ' + x2 + ' ' + y2;
            var base = document.createElementNS(ns, 'path');
            base.setAttribute('d', d); base.setAttribute('class', 'edge' + (e[3] === 'muted' ? ' muted' : ''));
            base.setAttribute('data-edge', e[0] + '>' + e[1]);
            eg.appendChild(base);
            if (e[3] !== 'muted') {
                var fl = document.createElementNS(ns, 'path');
                fl.setAttribute('d', d); fl.setAttribute('class', 'edge flowing');
                fl.setAttribute('data-edge', e[0] + '>' + e[1]);
                fl.style.stroke = e[2]; fl.setAttribute('stroke-opacity', .55);
                eg.appendChild(fl);
            }
            paths[e[0] + '>' + e[1]] = base;
        });
        if (walking) applyWalkFocus();
    }

    /* ------------------------------------------------------------ particles */
    function routeFor(d) {
        var prod = d.decisionType === 'UNDERWRITING' ? 'pc' : 'cc';
        var ag = AGENT_NODE[d.agentId];
        if (!ag) return null;
        var flagged = !!(st().flagById[d.decisionId] || st().evById[d.decisionId]);
        return flagged ? [prod, ag, 'ing', 'det', 'ev', 'exp'] : [prod, ag, 'ing', 'det', 'clr', 'exp'];
    }

    function fly(route, hex, onDone) {
        if (!route || document.hidden) { if (onDone) onDone(); return; }
        var ns = 'http://www.w3.org/2000/svg';
        var dot = document.createElementNS(ns, 'circle');
        dot.setAttribute('r', 4); dot.setAttribute('fill', hex); dot.setAttribute('class', 'particle');
        $('#particles').appendChild(dot);
        var i = 0;
        function seg() {
            if (i >= route.length - 1) { dot.remove(); if (onDone) onDone(); return; }
            var key = route[i] + '>' + route[i + 1];
            var p = paths[key];
            if (!p) { i++; seg(); return; }
            var len = p.getTotalLength(), dur = 520, t0 = null;
            $$('[data-edge="' + key + '"]').forEach(function (e) { e.classList.add('lit'); setTimeout(function () { e.classList.remove('lit'); }, dur + 200); });
            function step(ts) {
                if (!t0) t0 = ts;
                var k = Math.min(1, (ts - t0) / dur);
                var ease = k < .5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
                var pt = p.getPointAtLength(len * ease);
                dot.setAttribute('cx', pt.x); dot.setAttribute('cy', pt.y);
                if (k < 1) requestAnimationFrame(step);
                else {
                    var n = node(route[i + 1]);
                    if (n) { n.classList.remove('hit'); void n.offsetWidth; n.classList.add('hit'); }
                    i++; seg();
                }
            }
            requestAnimationFrame(step);
        }
        seg();
    }
    function flyDecision(d, delay) {
        setTimeout(function () { fly(routeFor(d), A.typeOf(d.decisionType).hex); }, delay || 0);
    }

    /* ------------------------------------------------------------ counts & panels */
    function setCount(key, v) { $$('[data-count="' + key + '"]').forEach(function (e) { e.textContent = A.fmt(v); }); }

    function renderCounts() {
        var s = st(), n = s.decisions.length;
        setCount('pc', countType('UNDERWRITING'));
        setCount('cc', countType('CLAIMS') + countType('FRAUD_FLAG'));
        Object.keys(AGENT_NODE).forEach(function (id) { setCount(AGENT_NODE[id], agentDecisions(id).length); });
        setCount('ing', n);
        setCount('det', s.flagged.length);
        setCount('ev', s.evidence.length);
        setCount('clr', Math.max(0, n - s.flagged.length));
        setCount('exp', s.exposure.length);
        setCount('bundle', s.evidence.length);
        setCount('summary', s.exposure.length);
        setCount('reviewer', s.evidence.length);
        $('#bar-det').style.width = n ? (s.flagged.length / n * 100) + '%' : '0';
        var avg = s.exposure.length ? s.exposure.reduce(function (a, x) { return a + x.evidenceRate; }, 0) / s.exposure.length : 0;
        $('#bar-exp').style.width = (avg * 100) + '%';
        var by = A.groupCount(s.evidence, function (e) { return e.exposureCategory; });
        $('#stack-ev').innerHTML = A.CAT_ORDER.map(function (c) {
            return by[c] ? '<i style="flex-grow:' + by[c] + ';background:' + A.catOf(c).hex + '" title="' + A.esc(A.catOf(c).short) + ': ' + by[c] + '"></i>' : '';
        }).join('') || '<i style="flex-grow:1;background:#1c1e25"></i>';
    }

    function renderKpis() {
        var s = st(), n = s.decisions.length;
        $('#k-dec').textContent = A.fmt(n);
        var last5 = s.decisions.filter(function (d) { return Date.now() - A.ts(d.timestamp) < 300000; }).length;
        $('#k-dec-f').innerHTML = '<span class="delta-up">+' + last5 + '</span> in last 5 min';
        $('#k-flag').innerHTML = A.fmt(s.flagged.length) + '<small>' + rate(s.flagged.length, n) + '</small>';
        var fr = s.flagged.filter(function (x) { var r = A.ruleOf(x); return r && r.key === 'fraud'; }).length;
        $('#k-flag-f').textContent = fr + ' fraud · ' + (s.flagged.length - fr) + ' outlier';
        $('#k-ev').textContent = A.fmt(s.evidence.length);
        var cats = Object.keys(A.groupCount(s.evidence, function (e) { return e.exposureCategory; })).length;
        $('#k-ev-f').textContent = cats + ' exposure categor' + (cats === 1 ? 'y' : 'ies');
        $('#k-gap').textContent = gaps();
        $('#k-ag').textContent = s.exposure.length;
        var top = s.exposure[0];
        $('#k-ag-f').textContent = top ? 'highest: ' + top.agentId.replace('qsr-', '') : '—';
        var by = A.groupCount(s.evidence, function (e) { return e.exposureCategory; });
        var best = Object.keys(by).sort(function (a, b) { return by[b] - by[a]; })[0];
        $('#k-top').innerHTML = best ? A.catChip(best) : '—';
        $('#k-top-f').textContent = best ? by[best] + ' of ' + s.evidence.length + ' records' : 'no evidence yet';
    }

    var streamSeen = null;
    function renderStream(newIds) {
        var rows = st().decisions.slice(0, 14);
        if (!rows.length) { $('#stream').innerHTML = '<div class="empty">' + A.ICON.empty + 'Waiting for the first decision…</div>'; return; }
        var fresh = {}; (newIds || []).forEach(function (id) { fresh[id] = 1; });
        $('#stream').innerHTML = rows.map(function (d) {
            var stt = A.statusOf(d);
            return '<div class="srow' + (fresh[d.decisionId] ? ' new' : '') + '" data-open-decision="' + A.esc(d.decisionId) + '">' +
                '<span class="t">' + A.time(d.timestamp) + '</span>' +
                '<span class="mid">' + A.agentHtml(d.agentId, true) + '<span class="mono nm2" style="font-size:11.5px;color:var(--text-2)">' + A.esc(d.agentId.replace('qsr-', '')) + '</span>' +
                '<span class="arrow">→</span>' + A.outcomeChip(d.outcome) + '<span class="hide-sm">' + A.jurChip(d.jurisdiction) + '</span></span>' +
                '<span style="display:flex;align-items:center;gap:10px">' + A.trackHtml(d) + A.chip(stt.label, stt.hex, { dot: true }) + '</span>' +
                '</div>';
        }).join('');
    }

    function renderStageCards() {
        var s = st(), min = 60000, n = 15;
        var cards = [
            { k: 'ingestion', v: A.fmt(s.decisions.length), u: 'decisions logged', p: 'Every Qusar decision captured and persisted as an immutable audit row.', data: A.buckets(s.decisions, 'timestamp', min, n) },
            { k: 'detection', v: A.fmt(s.flagged.length), u: 'flagged · ' + rate(s.flagged.length, s.decisions.length), p: 'Fraud and denial-outlier rules, plus jurisdiction disclosure tagging.', data: A.buckets(s.flagged, 'reviewedAt', min, n) },
            { k: 'evidence', v: A.fmt(s.evidence.length), u: 'evidence records', p: 'Flagged or unreviewed decisions mapped to an exposure category.', data: A.buckets(s.evidence, 'recordedAt', min, n) },
            { k: 'exposure', v: A.fmt(s.exposure.length), u: 'agents monitored', p: 'Per-agent evidence rate and dominant exposure, exportable.', data: A.buckets(s.evidence, 'recordedAt', min, n) }
        ];
        $('#stage-cards').innerHTML = cards.map(function (c) {
            var g = S[c.k];
            return '<a class="card stage-card" href="' + g.href + '" style="--c:' + g.hex + '">' +
                '<div class="num">STAGE ' + g.n + '</div><h3>' + A.esc(g.label) + '</h3><p>' + A.esc(c.p) + '</p>' +
                '<div class="v">' + c.v + '<small>' + A.esc(c.u) + '</small></div>' +
                A.spark(c.data, g.hex, 260, 38) +
                '<div class="label" style="margin-top:2px;font-size:9.5px">last 15 min</div>' +
                '<span class="go">Open stage ' + A.ICON.arrow + '</span></a>';
        }).join('');
    }

    /* ------------------------------------------------------------ walkthrough */
    var WALK = [
        { nodes: ['pc', 'cc', 'bc'], edges: [], sel: 'cc', title: 'Where insurance work happens',
          text: 'Guidewire runs the carrier\'s core: PolicyCenter for underwriting, ClaimCenter for claims and SIU. These are existing systems — not built by us.' },
        { nodes: ['pc', 'cc'].concat(AGENT_NODES), edges: ['pc>uw01', 'pc>uw04', 'cc>cl02', 'cc>cl07', 'cc>fr03'], sel: 'fr03', title: 'Qusar agents make the call',
          text: 'Guidewire\'s Qusar framework lets AI agents approve, deny or flag on their own inside those workflows. Qusar already governs how each agent decides.' },
        { nodes: AGENT_NODES.concat(['ing']), edges: ['uw01>ing', 'uw04>ing', 'cl02>ing', 'cl07>ing', 'fr03>ing'], sel: 'ing', title: 'The gap AEGIS fills',
          text: 'Once made, a decision\'s evidence sits scattered in each agent\'s own logs — no carrier-facing view of exposure, no exportable audit trail. AEGIS picks up every decision event here.' },
        { nodes: ['ing'], edges: ['ing>det'], sel: 'ing', title: 'Stage 01 · Ingestion',
          text: 'Each decision is validated, de-duplicated and persisted as an immutable audit row, then published in-process to detection — synchronously, no queue.' },
        { nodes: ['det', 'clr'], edges: ['ing>det', 'det>clr'], sel: 'det', title: 'Stage 02 · Detection & Compliance tagging',
          text: 'Two rules run instantly: fraud flags always route to review; denial-rate outliers (>50% over 5+) get flagged. Flagged decisions get their jurisdiction\'s standard attached — a tag, not a certification. Everything else is cleared.' },
        { nodes: ['ev'], edges: ['det>ev'], sel: 'ev', title: 'Stage 03 · Evidence Mapping',
          text: 'Every flagged decision becomes an evidence record mapped to an exposure category. A safety-net sweep records anything left unreviewed past 45s as a control gap.' },
        { nodes: ['exp'], edges: ['ev>exp', 'clr>exp'], sel: 'exp', title: 'Stage 04 · Exposure Aggregation',
          text: 'Evidence rolls up per agent: how often each agent produces evidence and in which exposure category. Cleared decisions count too, so the rate is honest.' },
        { nodes: ['bundle', 'summary', 'reviewer', 'scope'], edges: ['exp>bundle', 'exp>summary', 'exp>reviewer'], sel: 'bundle', title: 'What rolls out — and where AEGIS stops',
          text: 'An exportable audit bundle and per-agent exposure summary go to a human reviewer. AEGIS never certifies compliance, decides coverage or sets a premium.' }
    ];
    var walking = false, wi = 0;

    function applyWalkFocus() {
        var w = WALK[wi];
        $$('.fnode').forEach(function (n) { n.classList.toggle('focus', w.nodes.indexOf(n.getAttribute('data-node')) >= 0); });
        $$('[data-edge]').forEach(function (e) { e.classList.toggle('focus', w.edges.indexOf(e.getAttribute('data-edge')) >= 0); });
    }
    function showWalk() {
        var w = WALK[wi];
        $('#w-step').textContent = 'STEP ' + (wi + 1) + ' / ' + WALK.length;
        $('#w-title').textContent = w.title;
        $('#w-text').textContent = w.text;
        $('#w-dots').innerHTML = WALK.map(function (_, i) { return '<i class="' + (i <= wi ? 'on' : '') + '"></i>'; }).join('');
        $('#w-prev').disabled = wi === 0;
        $('#w-next').textContent = wi === WALK.length - 1 ? 'Finish' : 'Next';
        selected = w.sel; renderInspector();
        applyWalkFocus();
        // send a sample decision along the highlighted edges
        var sample = st().decisions.filter(function (d) { return st().evById[d.decisionId]; })[0] || st().decisions[0];
        w.edges.forEach(function (e, i) {
            var parts = e.split('>');
            setTimeout(function () { fly(parts, sample ? A.typeOf(sample.decisionType).hex : '#a855f7'); }, i * 120);
        });
    }
    function startWalk() { walking = true; wi = 0; $('#flow').classList.add('walking'); layout(); showWalk(); $('#flow').scrollIntoView({ behavior: 'smooth', block: 'center' }); }
    function endWalk() {
        walking = false; $('#flow').classList.remove('walking');
        $$('.focus').forEach(function (n) { n.classList.remove('focus'); });
        layout();
    }

    /* ------------------------------------------------------------ wire up */
    $$('.fnode[data-node]').forEach(function (n) {
        n.addEventListener('click', function () {
            var id = n.getAttribute('data-node');
            if (!INFO[id]) return;
            selected = id; renderInspector();
        });
    });
    $('#btn-walk').addEventListener('click', startWalk);
    $('#w-next').addEventListener('click', function () { if (wi < WALK.length - 1) { wi++; showWalk(); } else endWalk(); });
    $('#w-prev').addEventListener('click', function () { if (wi > 0) { wi--; showWalk(); } });
    $('#w-exit').addEventListener('click', endWalk);
    $('#btn-replay').addEventListener('click', function () {
        st().decisions.slice(0, 8).reverse().forEach(function (d, i) { flyDecision(d, i * 380); });
    });
    document.addEventListener('keydown', function (e) {
        if (!walking) return;
        if (e.key === 'ArrowRight') $('#w-next').click();
        if (e.key === 'ArrowLeft') $('#w-prev').click();
    });
    // An injected decision arrives through the normal sync as a new id, so it
    // already gets its own particle — just make sure the map is in view.
    document.addEventListener('aegis:injected', function () {
        $('#flow').scrollIntoView({ behavior: 'smooth', block: 'center' });
    });

    var resizeT;
    window.addEventListener('resize', function () { clearTimeout(resizeT); resizeT = setTimeout(layout, 120); });
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(layout);

    A.subscribe(function (s, ev) {
        renderCounts();
        renderKpis();
        renderStream(ev.newIds);
        renderStageCards();
        renderInspector();
        if (ev.first) {
            layout();
            s.decisions.slice(0, 6).reverse().forEach(function (d, i) { flyDecision(d, 400 + i * 450); });
        } else {
            ev.newIds.forEach(function (id, i) { flyDecision(s.byId[id], i * 300); });
        }
    });

    layout();
    renderInspector();
})();
