/* Analytics · Inference & Insights — renders GET /api/insights + the human review queue */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var POLL_MS = 10000;
    var ACCENT = '#ec4899';          // the one highlighted series in a chart
    var CONTEXT = '#3a3d47';         // other series, recessive
    var PEER = '#9ca3af';            // reference line
    var SINGLE = '#818cf8';          // single-series bars (analytics accent)

    var SEV = {
        HIGH: { label: 'High', hex: '#f87171' },
        MEDIUM: { label: 'Medium', hex: '#fbbf24' },
        LOW: { label: 'Low', hex: '#60a5fa' }
    };
    var KIND = {
        DRIFT: 'Drift', OUTLIER: 'Peer outlier', OVERTURN: 'Error rate', DISPARITY: 'Fair treatment',
        CALIBRATION: 'Calibration', PORTFOLIO: 'Portfolio', REASONING: 'Explainability',
        CONFIDENCE: 'Confidence', OPPORTUNITY: 'Opportunity'
    };
    var BAND = {
        HIGH: { label: 'High', hex: '#f87171' },
        ELEVATED: { label: 'Elevated', hex: '#fbbf24' },
        NORMAL: { label: 'Normal', hex: '#34d399' }
    };
    var POSTURE = {
        FIX_FIRST: { label: 'Fix before scaling', hex: '#f87171' },
        WATCH: { label: 'Watch', hex: '#fbbf24' },
        STEADY: { label: 'Steady', hex: '#9ca3af' },
        SCALE: { label: 'Ready to scale AI', hex: '#34d399' }
    };
    var ISSUE = {
        THIN_REASONING: 'Thin reasoning (under 40 characters)',
        NO_QUANTITATIVE_BASIS: 'Adverse outcome with no figures cited',
        OUTCOME_CONTRADICTS_REASONING: 'Outcome contradicts its own reasoning',
        LOW_CONFIDENCE_AUTO_DECISION: 'Decided alone at low confidence'
    };
    var WEIGHT_LABEL = {
        peerOutlier: 'Peer outlier', drift: 'Drift', overturn: 'Overturn rate (CI lower bound)',
        reasoning: 'Reasoning gaps', lowConfidence: 'Low-confidence auto-decisions'
    };
    var WEIGHTS = { peerOutlier: 0.30, drift: 0.25, overturn: 0.25, reasoning: 0.10, lowConfidence: 0.10 };

    var state = { report: null, queue: [], sev: '', disp: 'agent', tlAgent: null, busy: false };

    var ICON_ALERT = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l9.5 17h-19z"/><path d="M12 10v4M12 17h.01"/></svg>';
    var ICON_OK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12l5 5L20 7"/></svg>';

    function pct(v, d) { return v == null ? '—' : A.pct(v, d); }
    function sevChip(s) { var x = SEV[s] || SEV.LOW; return A.chip(x.label, x.hex, { dot: true }); }
    function bandChip(b) { var x = BAND[b] || BAND.NORMAL; return A.chip(x.label, x.hex, { dot: true }); }
    function pFmt(p) { return p == null ? '—' : p < 0.001 ? '< 0.001' : p.toFixed(3); }
    function tip(title, rows) {
        return A.esc('<div class="t">' + A.esc(title) + '</div>' + rows.map(function (r) {
            return '<div class="row">' + (r.c ? '<span class="key" style="--c:' + r.c + '"></span>' : '') +
                '<b>' + A.esc(r.v) + '</b><span>' + A.esc(r.k) + '</span></div>';
        }).join(''));
    }
    function agentById(id) {
        return (state.report ? state.report.agents : []).filter(function (a) { return a.agentId === id; })[0];
    }

    /* ------------------------------------------------------------ data */
    function load() {
        if (state.busy) return;
        state.busy = true;
        if (state.report) $('#content').classList.add('loading');
        Promise.all([
            A.fetchJson('/api/insights'),
            A.fetchJson('/api/reviews/queue?limit=12').catch(function () { return []; })
        ]).then(function (r) {
            state.report = r[0];
            state.queue = r[1] || [];
            if (!state.tlAgent && state.report.agents.length) {
                var drifting = state.report.agents.filter(function (a) { return a.drift && a.drift.drifting && a.drift.direction === 'UP'; })[0];
                state.tlAgent = (drifting || state.report.agents[0]).agentId;
            }
            render();
        }).catch(function (e) {
            A.toast('Insights unavailable: ' + A.esc(e.message), '#f87171');
        }).finally(function () {
            state.busy = false;
            $('#content').classList.remove('loading');
        });
    }

    function render() {
        var r = state.report;
        renderKpis(r);
        renderFindings(r);
        renderQueue();
        renderAgents(r);
        renderTimeline(r);
        renderCalibration(r);
        renderPortfolio(r);
        renderDisparity(r);
        renderReasoning(r);
        renderMethod(r);
        $('#gen-at').textContent = 'ANALYSED ' + A.fmt(r.summary.decisionsAnalysed) + ' DECISIONS · ' + A.time(r.generatedAt);
        $('#disclaimer-text').textContent = r.disclaimer;
    }

    /* ------------------------------------------------------------ KPIs */
    function renderKpis(r) {
        var s = r.summary;
        $('#k-risk').innerHTML = s.agentsAtRisk + '<small>/ ' + s.agentsMonitored + ' agents</small>';
        $('#k-risk-f').textContent = s.driftingAgents + ' drifting · ' + s.highFindings + ' high-severity findings';
        $('#k-err').innerHTML = s.reviewed ? A.esc(pct(s.overturnRate, 1)) : '—';
        $('#k-err-f').innerHTML = s.reviewed
            ? '<span class="ci">95% CI ' + pct(s.overturnCiLow) + '–' + pct(s.overturnCiHigh) + '</span> · ' + s.overturned + ' of ' + s.reviewed + ' reviews overturned'
            : 'no human verdicts yet';
        $('#k-val').textContent = A.money(s.overturnedValue);
        $('#k-val-f').textContent = 'AI claim & fraud decisions a person reversed';
        $('#k-hrs').innerHTML = A.esc(String(s.reviewWorkloadHours)) + '<small>h</small>';
        $('#k-hrs-f').textContent = pct(s.automationRate) + ' of decisions needed no person · ' + r.assumptions.reviewMinutesPerCase + ' min/case';
    }

    /* ------------------------------------------------------------ findings */
    function renderFindings(r) {
        var list = r.findings.filter(function (f) { return !state.sev || f.severity === state.sev; });
        var counts = A.groupCount(r.findings, function (f) { return f.severity; });
        $('#f-sub').textContent = (counts.HIGH || 0) + ' high · ' + (counts.MEDIUM || 0) + ' medium · ' + (counts.LOW || 0) + ' low / opportunity';
        if (!list.length) {
            $('#findings').innerHTML = '<div class="empty">' + A.ICON.empty + 'Nothing at this severity. That is a good sign.</div>';
            return;
        }
        $('#findings').innerHTML = list.map(function (f, i) {
            var good = f.kind === 'OPPORTUNITY';
            var hex = good ? '#34d399' : (SEV[f.severity] || SEV.LOW).hex;
            return '<div class="finding fade-in" data-fi="' + r.findings.indexOf(f) + '" style="--c:' + hex + ';animation-delay:' + Math.min(i * 30, 300) + 'ms" tabindex="0">' +
                '<span class="bar-sev"></span><div>' +
                '<div class="top">' + (good ? A.chip('Opportunity', '#34d399', { dot: true }) : sevChip(f.severity)) +
                A.chip(KIND[f.kind] || f.kind, '#9ca3af') + '<b>' + A.esc(f.title) + '</b></div>' +
                '<p>' + A.esc(f.detail) + '</p>' +
                (f.evidence ? '<div class="ev">' + A.esc(f.evidence) + '</div>' : '') +
                '</div></div>';
        }).join('');
    }

    function openFinding(f) {
        if (f.agentId) { openAgent(f.agentId); return; }
        var target = f.kind === 'DISPARITY' ? '#disp' : f.kind === 'CALIBRATION' ? '#calib' : '#lines';
        if (f.kind === 'DISPARITY') { state.disp = 'fleet'; syncSeg('#d-seg', 'd', 'fleet'); renderDisparity(state.report); }
        var el = $(target);
        el.closest('.card').scrollIntoView({ behavior: 'smooth', block: 'center' });
        if (f.segment) {
            var hit = $('[data-seg="' + f.segment + '"]');
            if (hit) { hit.style.outline = '2px solid ' + SINGLE; setTimeout(function () { hit.style.outline = ''; }, 2200); }
        }
    }

    /* ------------------------------------------------------------ review queue */
    function renderQueue() {
        $('#rq-count').textContent = state.queue.length + (state.queue.length >= 12 ? '+' : '');
        if (!state.queue.length) {
            $('#queue').innerHTML = '<div class="empty">' + A.ICON.empty + 'Queue clear — every adverse decision has a verdict.</div>';
            return;
        }
        $('#queue').innerHTML = state.queue.map(function (d) {
            return '<div class="rq" data-id="' + A.esc(d.decisionId) + '">' +
                '<div class="top">' + A.agentHtml(d.agentId, true) + A.outcomeChip(d.outcome) +
                (d.lineOfBusiness ? A.lobChip(d.lineOfBusiness) : '') +
                (d.amount != null && d.decisionType !== 'UNDERWRITING' ? '<span class="mini-pill">' + A.esc(A.money(d.amount)) + '</span>' : '') +
                '<span class="muted" style="margin-left:auto;font-size:11px">' + A.esc(A.ago(d.timestamp)) + '</span></div>' +
                '<div class="why">' + A.esc(d.reasoningTrail) + '</div>' +
                '<div class="acts">' +
                '<button class="btn sm uphold" data-verdict="UPHELD">' + ICON_OK + 'Uphold</button>' +
                '<button class="btn sm overturn" data-verdict="OVERTURNED"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>Overturn</button>' +
                '<span class="spacer"></span>' +
                '<button class="btn sm ghost" data-open-decision="' + A.esc(d.decisionId) + '">Details</button>' +
                '</div></div>';
        }).join('');
    }

    function submitVerdict(row, verdict) {
        var id = row.getAttribute('data-id');
        row.classList.add('done');
        A.fetchJson('/api/reviews/' + encodeURIComponent(id), {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ verdict: verdict, reviewer: 'console-reviewer', note: 'Recorded from the AEGIS insights console' })
        }).then(function () {
            A.toast('Verdict recorded: <b>' + (verdict === 'UPHELD' ? 'upheld' : 'overturned') + '</b> · re-running analysis', verdict === 'UPHELD' ? '#34d399' : '#f87171');
            load();
            A.refreshInsightsBadge();
        }).catch(function (e) {
            row.classList.remove('done');
            A.toast('Could not record verdict: ' + A.esc(e.message), '#f87171');
        });
    }

    /* ------------------------------------------------------------ agents */
    function ciBar(a, max) {
        var x = function (v) { return Math.max(0, Math.min(100, v / max * 100)); };
        var hex = a.outlier ? '#f87171' : SINGLE;
        return '<div class="ci-bar" style="--c:' + hex + '" data-tip="' + tip(a.agentId, [
            { c: hex, v: pct(a.adverseRate), k: a.adverseOutcome.toLowerCase() + ' rate (' + a.adverse + ' of ' + a.decisions + ')' },
            { v: pct(a.ciLow) + '–' + pct(a.ciHigh), k: '95% Wilson interval' },
            { c: PEER, v: a.peerRate == null ? 'no peers' : pct(a.peerRate), k: 'peer agents' },
            { v: a.zVsPeers == null ? '—' : 'z ' + a.zVsPeers + ', p ' + pFmt(a.pVsPeers), k: 'one-sided test' }
        ]) + '">' +
            '<span class="rail"></span><span class="rng" style="left:' + x(a.ciLow) + '%;width:' + (x(a.ciHigh) - x(a.ciLow)) + '%"></span>' +
            (a.peerRate != null ? '<span class="peer" style="left:' + x(a.peerRate) + '%"></span>' : '') +
            '<span class="pt" style="left:' + x(a.adverseRate) + '%"></span></div>';
    }

    function driftCell(a) {
        var d = a.drift;
        if (!d) return '<span class="muted" style="font-size:11.5px">needs more history</span>';
        var txt = Math.round(d.baselineRate * 100) + '% → ' + Math.round(d.recentRate * 100) + '%';
        var t = tip('Drift · ' + a.agentId, [
            { v: pct(d.baselineRate), k: 'earlier ' + d.baselineN + ' decisions' },
            { v: pct(d.recentRate), k: 'latest ' + d.recentN + ' decisions' },
            { v: 'z ' + d.z + ', p ' + pFmt(d.p), k: 'two-proportion test' },
            { v: String(d.psi), k: 'PSI on outcome mix' }
        ]);
        if (d.drifting && d.direction === 'UP') return '<span data-tip="' + t + '">' + A.chip('▲ ' + txt, '#f87171', { mono: true }) + '</span>';
        if (d.drifting) return '<span data-tip="' + t + '">' + A.chip('▼ ' + txt, '#60a5fa', { mono: true }) + '</span>';
        return '<span class="delta muted" data-tip="' + t + '">stable · PSI ' + d.psi.toFixed(2) + '</span>';
    }

    function overturnCell(a) {
        if (!a.reviewed) return '<span class="muted">no reviews</span>';
        var sig = a.overturnPVsRest != null && a.overturnPVsRest < 0.05 && a.overturnRate >= 0.2;
        return '<span class="delta" data-tip="' + tip('Human review verdicts', [
            { v: a.overturned + ' / ' + a.reviewed, k: 'overturned / reviewed' },
            { v: pct(a.overturnCiLow) + '–' + pct(a.overturnCiHigh), k: '95% Wilson interval' },
            { v: pFmt(a.overturnPVsRest), k: 'p vs rest of fleet' }
        ]) + '" style="color:' + (sig ? '#fca5a5' : 'var(--text-2)') + '">' + pct(a.overturnRate) +
            ' <span class="muted">(' + a.overturned + '/' + a.reviewed + ')</span></span>';
    }

    function renderAgents(r) {
        if (!r.agents.length) { $('#agents').innerHTML = A.emptyRow(9, 'No decisions to analyse yet.'); return; }
        var max = Math.max(0.6, Math.max.apply(null, r.agents.map(function (a) { return a.ciHigh; })));
        $('#agents').innerHTML = r.agents.map(function (a, i) {
            var band = BAND[a.riskBand];
            return '<tr class="clickable" data-agent="' + A.esc(a.agentId) + '">' +
                '<td class="mono muted">' + String(i + 1).padStart(2, '0') + '</td>' +
                '<td>' + A.agentHtml(a.agentId) + '</td>' +
                '<td><div class="risk-cell"><b>' + a.riskScore + '</b><div class="bar" style="--c:' + band.hex + '"><i style="width:' + a.riskScore + '%"></i></div>' + bandChip(a.riskBand) + '</div></td>' +
                '<td class="hide-md">' + A.typeChip(a.decisionType) + '</td>' +
                '<td class="num">' + a.decisions + '</td>' +
                '<td style="min-width:170px">' + ciBar(a, max) + '<div class="delta muted" style="margin-top:2px">' + pct(a.adverseRate) +
                (a.peerRate != null ? ' vs ' + pct(a.peerRate) + ' peers' : ' · only agent in its job') + '</div></td>' +
                '<td>' + driftCell(a) + '</td>' +
                '<td>' + overturnCell(a) + '</td>' +
                '<td class="delta">' + (a.reasoningIssueRate + a.lowConfidenceAutoRate > 0
                    ? pct(a.reasoningIssueRate) + ' <span class="muted">· ' + pct(a.lowConfidenceAutoRate) + ' low-conf</span>'
                    : '<span class="muted">none</span>') + '</td></tr>';
        }).join('');
        $('#weights').innerHTML = '<span>Risk score = ' + Object.keys(WEIGHTS).map(function (k) {
            return '<b>' + Math.round(WEIGHTS[k] * 100) + '%</b> ' + WEIGHT_LABEL[k].toLowerCase();
        }).join(' + ') + '</span><span>High ≥ <b>45</b> · Elevated ≥ <b>25</b></span>';
    }

    function openAgent(id) {
        var a = agentById(id);
        if (!a) return;
        var r = state.report;
        var comps = Object.keys(WEIGHTS).map(function (k) {
            var v = a.riskComponents[k] || 0, pts = Math.round(v * WEIGHTS[k] * 100);
            return '<div class="issue" style="grid-template-columns:200px 1fr 46px"><span>' + WEIGHT_LABEL[k] + ' <span class="muted mono">×' + WEIGHTS[k] + '</span></span>' +
                '<div class="bar" style="--c:' + SINGLE + '"><i style="width:' + Math.round(v * 100) + '%"></i></div><span class="n">+' + pts + '</span></div>';
        }).join('');
        var mixTotal = Object.keys(a.outcomeMix).reduce(function (s, k) { return s + a.outcomeMix[k]; }, 0);
        var mix = '<div class="stack" style="height:10px">' + Object.keys(a.outcomeMix).map(function (k) {
            return '<i style="flex-grow:' + a.outcomeMix[k] + ';background:' + (A.OUTCOMES[k] || '#a1a1aa') + '" data-tip="' + tip(k.replace(/_/g, ' '), [{ v: String(a.outcomeMix[k]), k: pct(a.outcomeMix[k] / mixTotal) + ' of decisions' }]) + '"></i>';
        }).join('') + '</div><div class="legend" style="margin-top:8px">' + Object.keys(a.outcomeMix).map(function (k) {
            return '<span style="--c:' + (A.OUTCOMES[k] || '#a1a1aa') + '"><i></i>' + A.esc(k.replace(/_/g, ' ').toLowerCase()) + ' ' + a.outcomeMix[k] + '</span>';
        }).join('') + '</div>';
        var mine = r.findings.filter(function (f) { return f.agentId === id; });
        var d = a.drift;
        A.openDrawer({
            eyebrow: 'Agent profile · inference',
            title: A.esc(a.agentId),
            body:
                '<div class="chips" style="margin-bottom:14px">' + A.typeChip(a.decisionType) + bandChip(a.riskBand) +
                A.chip('Risk ' + a.riskScore + ' / 100', '#9ca3af', { mono: true }) + '</div>' +
                '<dl class="kv">' +
                '<dt>Decisions analysed</dt><dd class="mono">' + a.decisions + '</dd>' +
                '<dt>' + (a.adverseOutcome === 'FLAGGED' ? 'Fraud-flag' : 'Denial') + ' rate</dt><dd class="mono">' + pct(a.adverseRate, 1) + ' <span class="muted">(95% CI ' + pct(a.ciLow) + '–' + pct(a.ciHigh) + ')</span></dd>' +
                '<dt>Peer agents</dt><dd class="mono">' + (a.peerRate == null ? '<span class="muted">none doing this job</span>' : pct(a.peerRate, 1) + ' <span class="muted">· z ' + a.zVsPeers + ', p ' + pFmt(a.pVsPeers) + '</span>') + '</dd>' +
                '<dt>Drift</dt><dd class="mono">' + (d ? pct(d.baselineRate) + ' → ' + pct(d.recentRate) + ' <span class="muted">· p ' + pFmt(d.p) + ' · PSI ' + d.psi + '</span>' : '<span class="muted">needs more history</span>') + '</dd>' +
                '<dt>Overturned on review</dt><dd class="mono">' + (a.reviewed ? a.overturned + ' / ' + a.reviewed + ' <span class="muted">(' + pct(a.overturnRate) + ', CI ' + pct(a.overturnCiLow) + '–' + pct(a.overturnCiHigh) + ')</span>' : '<span class="muted">no reviews yet</span>') + '</dd>' +
                '<dt>Avg confidence</dt><dd class="mono">' + (a.avgConfidence == null ? '—' : a.avgConfidence.toFixed(2)) + '</dd>' +
                '</dl>' +
                '<div class="section-label label">Behaviour over time</div>' + timelineSvg(r, a.agentId, 480, 170) +
                '<div class="chart-legend" style="padding:6px 0 0">' + legendHtml(a) + '</div>' +
                '<div class="section-label label">How the risk score is built</div>' + comps +
                '<div class="section-label label">Outcome mix</div>' + mix +
                '<div class="section-label label">Findings for this agent</div>' +
                (mine.length ? mine.map(function (f) {
                    return '<div class="callout" style="--c:' + (SEV[f.severity] || SEV.LOW).hex + ';margin-bottom:8px">' + ICON_ALERT +
                        '<div><b>' + A.esc(f.title) + '.</b> ' + A.esc(f.detail) + '<div class="mono muted" style="font-size:10.5px;margin-top:4px">' + A.esc(f.evidence || '') + '</div></div></div>';
                }).join('') : '<p class="muted">No findings — this agent looks healthy.</p>'),
            foot: '<button class="btn sm" data-focus-agent="' + A.esc(a.agentId) + '">Show on timeline</button><a class="btn sm" href="/pipeline/ingestion">Its decisions</a>'
        });
    }

    /* ------------------------------------------------------------ timeline chart */
    function peerSeries(r, a) {
        var out = [];
        for (var b = 0; b < r.timelineBuckets.length; b++) {
            var n = 0, k = 0;
            r.agents.forEach(function (o) {
                if (o.agentId === a.agentId || o.decisionType !== a.decisionType) return;
                var c = o.timelineCounts[b], v = o.timeline[b];
                if (c >= 3 && v != null) { n += c; k += v * c; }
            });
            out.push(n >= 3 ? k / n : null);
        }
        return out;
    }

    function bucketLabel(iso) {
        var d = new Date(iso);
        return d.toLocaleDateString([], { weekday: 'short' }) + ' ' + String(d.getHours()).padStart(2, '0') + ':00';
    }

    function linePath(vals, x, y) {
        var p = '', pen = false;
        vals.forEach(function (v, i) {
            if (v == null) { pen = false; return; }
            p += (pen ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1) + ' ';
            pen = true;
        });
        return p;
    }

    function timelineSvg(r, agentId, W, H) {
        var a = agentById(agentId);
        if (!a) return '';
        var B = r.timelineBuckets.length, padL = 34, padR = 10, padT = 10, padB = 22;
        var peers = peerSeries(r, a);
        var all = [];
        r.agents.forEach(function (o) { if (o.decisionType === a.decisionType) o.timeline.forEach(function (v) { if (v != null) all.push(v); }); });
        var max = Math.min(1, Math.max(0.6, Math.ceil((Math.max.apply(null, all.concat([0])) + 0.05) * 4) / 4));
        var x = function (i) { return padL + (W - padL - padR) * (B === 1 ? 0.5 : i / (B - 1)); };
        var y = function (v) { return padT + (H - padT - padB) * (1 - v / max); };
        var g = '<g class="grid">';
        for (var t = 0; t <= max + 1e-9; t += 0.25) {
            g += '<line x1="' + padL + '" x2="' + (W - padR) + '" y1="' + y(t) + '" y2="' + y(t) + '"/>' +
                '<text class="axis" x="' + (padL - 6) + '" y="' + (y(t) + 3) + '" text-anchor="end">' + Math.round(t * 100) + '%</text>';
        }
        g += '</g>';
        var xl = '';
        for (var i = 0; i < B; i += Math.max(1, Math.round(B / 4))) {
            xl += '<text class="axis" x="' + x(i) + '" y="' + (H - 6) + '" text-anchor="middle">' + A.esc(bucketLabel(r.timelineBuckets[i])) + '</text>';
        }
        var ctx = r.agents.filter(function (o) { return o.agentId !== a.agentId && o.decisionType === a.decisionType; })
            .map(function (o) { return '<path d="' + linePath(o.timeline, x, y) + '" fill="none" stroke="' + CONTEXT + '" stroke-width="1.25"/>'; }).join('');
        var peer = '<path d="' + linePath(peers, x, y) + '" fill="none" stroke="' + PEER + '" stroke-width="1.5" stroke-dasharray="4 4"/>';
        var main = '<path d="' + linePath(a.timeline, x, y) + '" fill="none" stroke="' + ACCENT + '" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>';
        var dots = a.timeline.map(function (v, i) {
            return v == null ? '' : '<circle cx="' + x(i) + '" cy="' + y(v) + '" r="3" fill="' + ACCENT + '" stroke="#111216" stroke-width="2"/>';
        }).join('');
        var step = (W - padL - padR) / Math.max(1, B - 1);
        var hits = r.timelineBuckets.map(function (iso, i) {
            var rows = [{ c: ACCENT, v: a.timeline[i] == null ? 'n < 3' : pct(a.timeline[i]), k: A.agentOf(a.agentId).short + ' ' + A.agentOf(a.agentId).tag + ' · ' + a.timelineCounts[i] + ' decisions' }];
            if (peers.some(function (v) { return v != null; })) rows.push({ c: PEER, v: peers[i] == null ? '—' : pct(peers[i]), k: 'peer agents' });
            return '<rect class="hit" x="' + (x(i) - step / 2) + '" y="' + padT + '" width="' + step + '" height="' + (H - padT - padB) + '" data-tip="' +
                tip(bucketLabel(iso) + ' window', rows) + '"/>';
        }).join('');
        return '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="' + A.esc(a.adverseOutcome.toLowerCase() + ' rate over time for ' + a.agentId) + '">' +
            g + xl + ctx + peer + main + dots + hits + '</svg>';
    }

    function legendHtml(a) {
        return '<span style="--c:' + ACCENT + '"><i></i>' + A.esc(a.agentId) + '</span>' +
            '<span style="--c:' + PEER + '"><i class="dash"></i>peer average</span>' +
            '<span style="--c:' + CONTEXT + '"><i></i>other agents, same job</span>';
    }

    function renderTimeline(r) {
        $('#tl-pick').innerHTML = r.agents.map(function (a) {
            return '<button data-tl="' + A.esc(a.agentId) + '" class="' + (a.agentId === state.tlAgent ? 'on' : '') + '">' + A.esc(a.agentId.replace('qsr-', '')) + '</button>';
        }).join('');
        var a = agentById(state.tlAgent);
        if (!a) { $('#timeline').innerHTML = ''; return; }
        $('#timeline').innerHTML = timelineSvg(r, a.agentId, 720, 230);
        $('#tl-legend').innerHTML = legendHtml(a);
        var d = a.drift;
        $('#tl-sub').textContent = a.adverseOutcome.toLowerCase() + ' rate per window' +
            (d ? ' · earlier ' + pct(d.baselineRate) + ' → latest ' + pct(d.recentRate) + (d.drifting ? ' (drift)' : '') : '');
    }

    /* ------------------------------------------------------------ calibration */
    function renderCalibration(r) {
        var bins = r.calibration, W = 420, H = 200, padL = 34, padB = 40, padT = 18;
        var max = Math.max(0.4, Math.ceil(Math.max.apply(null, bins.map(function (b) { return b.overturnRate; }).concat([0])) * 10) / 10 + 0.05);
        var bw = (W - padL - 10) / bins.length;
        var y = function (v) { return padT + (H - padT - padB) * (1 - v / max); };
        var g = '<g class="grid">';
        for (var t = 0; t <= max + 1e-9; t += 0.1) {
            if (Math.round(t * 100) % 20) continue;
            g += '<line x1="' + padL + '" x2="' + W + '" y1="' + y(t) + '" y2="' + y(t) + '"/><text class="axis" x="' + (padL - 6) + '" y="' + (y(t) + 3) + '" text-anchor="end">' + Math.round(t * 100) + '%</text>';
        }
        g += '</g>';
        var bars = bins.map(function (b, i) {
            var x0 = padL + i * bw + bw * 0.22, w = bw * 0.56, top = y(b.overturnRate), h = Math.max(0, y(0) - top);
            var path = h > 4
                ? 'M' + x0 + ' ' + y(0) + ' V' + (top + 4) + ' Q' + x0 + ' ' + top + ' ' + (x0 + 4) + ' ' + top + ' H' + (x0 + w - 4) + ' Q' + (x0 + w) + ' ' + top + ' ' + (x0 + w) + ' ' + (top + 4) + ' V' + y(0) + ' Z'
                : 'M' + x0 + ' ' + y(0) + ' h' + w + ' v-' + Math.max(1, h) + ' h-' + w + ' Z';
            return '<g data-tip="' + tip('Confidence ' + b.label, [
                { c: SINGLE, v: b.reviewed ? pct(b.overturnRate, 1) : '—', k: 'overturned on review' },
                { v: b.overturned + ' / ' + b.reviewed, k: 'overturned / reviewed' },
                { v: b.reviewed ? b.avgConfidence.toFixed(2) : '—', k: 'average stated confidence' + (b.reviewed < 5 ? ' · too few reviews to read' : '') }
            ]) + '"><rect x="' + (padL + i * bw) + '" y="' + padT + '" width="' + bw + '" height="' + (H - padT - padB) + '" fill="transparent"/>' +
                '<path d="' + path + '" fill="' + SINGLE + '" opacity="' + (b.reviewed >= 5 ? 1 : 0.3) + '"/>' +
                (b.reviewed ? '<text x="' + (x0 + w / 2) + '" y="' + (top - 6) + '" text-anchor="middle" fill="#e8e9ee" font-family="JetBrains Mono, monospace" font-size="11">' + pct(b.overturnRate) + '</text>' : '') +
                '<text class="axis" x="' + (x0 + w / 2) + '" y="' + (H - padB + 16) + '" text-anchor="middle">' + A.esc(b.label) + '</text>' +
                '<text class="axis" x="' + (x0 + w / 2) + '" y="' + (H - padB + 30) + '" text-anchor="middle">n=' + b.reviewed + (b.reviewed < 5 ? ' · low' : '') + '</text></g>';
        }).join('');
        $('#calib').innerHTML = '<svg class="chart" viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="Overturn rate by agent confidence band">' + g + bars + '</svg>';
        var cal = r.findings.filter(function (f) { return f.kind === 'CALIBRATION'; })[0];
        $('#calib-note').innerHTML = cal
            ? '<div class="callout" style="--c:#f87171">' + ICON_ALERT + '<div><b>' + A.esc(cal.title) + '.</b> ' + A.esc(cal.detail) + '</div></div>'
            : '<div class="callout" style="--c:#34d399">' + ICON_OK + '<div><b>No calibration problem detected.</b> Higher-confidence decisions are not overturned more often than lower-confidence ones.</div></div>';
    }

    /* ------------------------------------------------------------ portfolio */
    function renderPortfolio(r) {
        if (!r.portfolio.length) { $('#lines').innerHTML = '<div class="empty">' + A.ICON.empty + 'No decisions yet.</div>'; return; }
        $('#lines').innerHTML = r.portfolio.map(function (l) {
            var p = POSTURE[l.posture] || POSTURE.STEADY;
            var lob = A.lobOf(l.lineOfBusiness);
            return '<div class="line" data-seg="' + A.esc(l.lineOfBusiness) + '">' +
                '<div class="h"><b>' + A.esc(lob.label) + '</b>' + A.chip(p.label, p.hex, { dot: true }) + '<span class="n">' + l.decisions + ' decisions</span></div>' +
                '<div class="m">' +
                '<div><span>No person needed</span><b>' + pct(l.automationRate) + '</b></div>' +
                '<div><span>Overturned on review</span><b>' + (l.reviewed ? pct(l.overturnRate) : '—') + '</b></div>' +
                '<div><span>Reviewer hours</span><b>' + l.reviewHours + '</b></div>' +
                '<div><span>Denial rate</span><b>' + pct(l.denialRate) + '</b></div>' +
                '<div><span>Fraud flag rate</span><b>' + (l.byType.FRAUD_FLAG ? pct(l.fraudFlagRate) : '—') + '</b></div>' +
                '<div><span>Value reversed</span><b>' + A.money(l.overturnedValue) + '</b></div>' +
                '</div>' +
                '<div class="bar" style="--c:' + (l.automationRate >= 0.85 ? '#34d399' : l.automationRate >= 0.7 ? '#fbbf24' : '#f87171') + '" data-tip="' + tip(lob.label, [
                    { v: pct(l.automationRate), k: 'decided by AI with no person involved' },
                    { v: pct(l.humanTouchRate), k: 'needed a person (referral, flag or review)' }
                ]) + '"><i style="width:' + Math.round(l.automationRate * 100) + '%"></i></div>' +
                l.signals.map(function (s) {
                    var hex = s.level === 'HIGH' ? '#f87171' : s.level === 'WARN' ? '#fbbf24' : '#34d399';
                    return '<div class="sig" style="--c:' + hex + '">' + (s.level === 'GOOD' ? ICON_OK : ICON_ALERT) + '<span>' + A.esc(s.text) + '</span></div>';
                }).join('') +
                '</div>';
        }).join('');
    }

    /* ------------------------------------------------------------ disparity */
    function renderDisparity(r) {
        var rows = r.disparities.filter(function (s) { return state.disp === 'agent' ? s.agentId : !s.agentId; });
        rows = rows.slice(0, 8);
        var x = function (v) { return Math.max(0, Math.min(100, v * 100)); };
        if (!rows.length) {
            $('#disp').innerHTML = '<div class="empty">' + A.ICON.empty + 'Not enough decisions per segment yet (8+ needed).</div>';
        } else {
            $('#disp').innerHTML = rows.map(function (s) {
                var hex = s.flagged ? '#f87171' : s.impactRatio < 0.8 ? '#fbbf24' : '#9ca3af';
                var who = s.agentId ? s.agentId.replace('qsr-', '') : s.decisionType.toLowerCase() + ' · all agents';
                return '<div class="disp-row" data-seg="' + A.esc(s.segment) + '" data-tip="' + tip(s.segment + ' · ' + (s.agentId || 'fleet'), [
                    { c: hex, v: s.impactRatio.toFixed(2), k: 'impact ratio (favourable rate ÷ best segment)' },
                    { v: pct(s.adverseRate), k: 'adverse-outcome rate, n = ' + s.decisions },
                    { v: 'z ' + s.z + ', p ' + pFmt(s.p), k: 'vs the rest of this ' + (s.agentId ? 'agent' : 'job') }
                ]) + '">' +
                    '<div class="who"><b>' + A.esc(s.segment) + '</b><span>' + A.esc(who) + ' · ' + (s.dimension === 'jurisdiction' ? 'jurisdiction' : 'line') + '</span></div>' +
                    '<div class="disp-track" style="--c:' + hex + '"><span class="rail"></span><span class="line80" style="left:80%"></span>' +
                    '<span class="stem" style="left:' + x(s.impactRatio) + '%;width:' + (100 - x(s.impactRatio)) + '%"></span>' +
                    '<span class="dot" style="left:' + x(s.impactRatio) + '%"></span></div>' +
                    '<div class="r" style="color:' + (s.flagged ? '#fca5a5' : 'var(--text-2)') + '">' + s.impactRatio.toFixed(2) + '</div></div>';
            }).join('');
        }
        $('#disp-ax').innerHTML = [0, 0.2, 0.4, 0.6, 0.8, 1].map(function (v) { return '<span style="left:' + (v * 100) + '%">' + v.toFixed(1) + '</span>'; }).join('');
        var flagged = r.disparities.filter(function (s) { return s.flagged; }).length;
        $('#disp-note').innerHTML = flagged
            ? '<span style="color:#fca5a5">' + flagged + ' segment' + (flagged === 1 ? '' : 's') + '</span> fall below the 0.80 line <i>and</i> are statistically significant (p &lt; ' + r.assumptions.alpha + '). Dashed line = four-fifths guideline.'
            : 'No segment is both below the 0.80 line and statistically significant. Dashed line = four-fifths guideline.';
    }

    /* ------------------------------------------------------------ reasoning */
    function renderReasoning(r) {
        var rs = r.reasoning;
        var max = Math.max(1, Math.max.apply(null, Object.keys(rs.issueCounts).map(function (k) { return rs.issueCounts[k]; })));
        $('#reasoning').innerHTML =
            '<div class="kpi" style="padding:0 0 10px"><div class="val sm">' + pct(rs.issueRate, 1) + '<small>of ' + A.fmt(rs.checked) + ' decisions have a gap</small></div></div>' +
            Object.keys(rs.issueCounts).map(function (k) {
                return '<div class="issue" data-tip="' + tip(ISSUE[k] || k, [{ c: SINGLE, v: String(rs.issueCounts[k]), k: 'decisions' }]) + '"><span>' + A.esc(ISSUE[k] || k) + '</span>' +
                    '<div class="bar" style="--c:' + SINGLE + '"><i style="width:' + (rs.issueCounts[k] / max * 100) + '%"></i></div><span class="n">' + rs.issueCounts[k] + '</span></div>';
            }).join('') +
            '<div class="section-label label">Latest examples</div>' +
            (rs.examples.length ? rs.examples.slice(0, 4).map(function (e) {
                return '<div class="ex"><div class="chips">' + A.agentHtml(e.agentId, true) + A.outcomeChip(e.outcome) +
                    e.issues.map(function (i) { return A.chip((ISSUE[i] || i).split(' (')[0], '#9ca3af'); }).join('') +
                    '<a href="#" class="btn sm ghost" style="margin-left:auto" data-open-decision="' + A.esc(e.decisionId) + '">Open</a></div>' +
                    '<div class="q">“' + A.esc(e.reasoningTrail) + '”</div></div>';
            }).join('') : '<p class="muted">No reasoning gaps found.</p>');
    }

    /* ------------------------------------------------------------ methodology */
    function renderMethod(r) {
        var s = r.assumptions;
        var rows = [
            ['Peer outlier', 'two-proportion z-test', 'Each agent\'s adverse-outcome rate vs. every other agent doing the same job. Flag when one-sided <code>p &lt; ' + s.alpha + '</code> on at least ' + s.minSample + ' decisions. Rates carry a 95% Wilson score interval.'],
            ['Drift', 'z-test + PSI', 'The agent\'s latest 30% of decisions vs. its own earlier history. Drift when the adverse rate moves ≥ 12 points with <code>p &lt; 0.05</code>, or the Population Stability Index of its outcome mix exceeds <code>' + s.psiThreshold + '</code>. Catches a model update that a fixed lifetime threshold misses.'],
            ['Fair treatment', 'four-fifths rule', 'Favourable-outcome rate per jurisdiction and line of business, divided by the best segment. Flag below <code>' + s.impactRatioThreshold + '</code> when also significant (<code>p &lt; ' + s.alpha + '</code>). A screen for a human to investigate, not a legal finding.'],
            ['Error rate', 'human verdicts', 'Share of reviewed adverse decisions a person overturned, with a Wilson interval and a z-test against the rest of the fleet. The only ground truth AEGIS has, so it comes from people, never from AEGIS.'],
            ['Calibration', 'binned overturn rate', 'Overturn rate by the agent\'s own stated confidence. If ≥ 0.90-confidence decisions are reversed as often as the rest, confidence can\'t be used to skip review.'],
            ['Reasoning quality', 'rule checks', 'Under 40 characters; an adverse outcome citing no figures; text contradicting the outcome; a decision taken alone below <code>' + s.lowConfidenceThreshold + '</code> confidence.'],
            ['Risk score', 'weighted sum', '30% peer outlier + 25% drift + 25% overturn (scored on the CI lower bound, so a few reviews can\'t inflate it) + 10% reasoning + 10% low-confidence. Weights are shown, never hidden.'],
            ['Workload', 'stated assumption', 'Decisions referred, flagged or reviewed × <code>' + s.reviewMinutesPerCase + '</code> minutes each. Change it in <code>aegis.insights.review-minutes-per-case</code>.']
        ];
        $('#method-body').innerHTML = rows.map(function (x) {
            return '<div class="k">' + x[0] + '<span>' + x[1] + '</span></div><div class="v">' + x[2] + '</div>';
        }).join('');
    }

    /* ------------------------------------------------------------ wiring */
    function syncSeg(sel, attr, val) {
        $$(sel + ' button').forEach(function (b) { b.classList.toggle('on', b.getAttribute('data-' + attr) === val); });
    }

    document.addEventListener('click', function (e) {
        var t;
        if ((t = e.target.closest('#f-seg button'))) { state.sev = t.getAttribute('data-f'); syncSeg('#f-seg', 'f', state.sev); renderFindings(state.report); return; }
        if ((t = e.target.closest('#d-seg button'))) { state.disp = t.getAttribute('data-d'); syncSeg('#d-seg', 'd', state.disp); renderDisparity(state.report); return; }
        if ((t = e.target.closest('#tl-pick button'))) { state.tlAgent = t.getAttribute('data-tl'); renderTimeline(state.report); return; }
        if ((t = e.target.closest('[data-verdict]'))) { submitVerdict(t.closest('.rq'), t.getAttribute('data-verdict')); return; }
        if ((t = e.target.closest('[data-focus-agent]'))) {
            state.tlAgent = t.getAttribute('data-focus-agent'); A.closeDrawers(); renderTimeline(state.report);
            $('#timeline').closest('.card').scrollIntoView({ behavior: 'smooth', block: 'center' }); return;
        }
        if ((t = e.target.closest('tr[data-agent]'))) { openAgent(t.getAttribute('data-agent')); return; }
        if ((t = e.target.closest('.finding'))) { openFinding(state.report.findings[+t.getAttribute('data-fi')]); return; }
        if (e.target.closest('#refresh')) { load(); }
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target.classList && e.target.classList.contains('finding')) {
            openFinding(state.report.findings[+e.target.getAttribute('data-fi')]);
        }
    });
    document.addEventListener('aegis:injected', function () { load(); });

    load();
    setInterval(function () { if (!A.isPaused() && !document.hidden) load(); }, POLL_MS);
})();
