/* Stage 04 · Exposure Aggregation */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var HEX = A.STAGES.exposure.hex;
    var latest = null;

    function evByAgent(s, id) { return s.evidence.filter(function (e) { return e.agentId === id; }); }

    function renderKpis(s) {
        var x = s.exposure;
        $('#k-ag').textContent = x.length;
        $('#k-ag-f').textContent = x.reduce(function (a, b) { return a + b.totalDecisions; }, 0) + ' decisions rolled up';
        var avg = x.length ? x.reduce(function (a, b) { return a + b.evidenceRate; }, 0) / x.length : 0;
        $('#k-avg').textContent = x.length ? A.pct(avg) : '—';
        $('#k-avg-b').style.width = (avg * 100) + '%';
        var top = x[0];
        $('#k-top').innerHTML = top ? A.agentHtml(top.agentId) : '—';
        $('#k-top-f').textContent = top ? A.pct(top.evidenceRate) + ' evidence rate · ' + top.evidenceCount + ' records' : '';
        var by = A.groupCount(s.evidence, function (e) { return e.exposureCategory; });
        var best = Object.keys(by).sort(function (a, b) { return by[b] - by[a]; })[0];
        $('#k-cat').innerHTML = best ? A.catChip(best) : '—';
        $('#k-cat-f').textContent = best ? A.pct(by[best] / s.evidence.length) + ' of all evidence' : 'no evidence yet';
        $('#cat-legend').innerHTML = A.CAT_ORDER.slice(0, 3).map(function (c) { return '<span style="--c:' + A.catOf(c).hex + '"><i></i>' + A.catOf(c).short + '</span>'; }).join('');
    }

    function renderTable(s) {
        if (!s.exposure.length) { $('#rows').innerHTML = A.emptyRow(9, 'No agents yet — waiting for decisions.'); $('#foot').innerHTML = ''; return; }
        $('#rows').innerHTML = s.exposure.map(function (x, i) {
            var evs = evByAgent(s, x.agentId);
            var mix = A.groupCount(evs, function (e) { return e.exposureCategory; });
            var a = A.agentOf(x.agentId);
            var ticks = '';
            for (var k = 0; k < 20; k++) ticks += '<i class="' + (k < Math.round(x.evidenceRate * 20) ? 'on' : '') + '"></i>';
            var rateHex = x.evidenceRate >= .5 ? '#f87171' : x.evidenceRate >= .2 ? '#fbbf24' : HEX;
            return '<tr class="clickable" data-agent="' + A.esc(x.agentId) + '">' +
                '<td><span class="rank' + (i === 0 ? ' top' : '') + '">' + String(i + 1).padStart(2, '0') + '</span></td>' +
                '<td>' + A.agentHtml(x.agentId) + '</td>' +
                '<td>' + (a.type ? A.productChip(a.type) : '<span class="muted">—</span>') + '</td>' +
                '<td class="num">' + x.totalDecisions + '</td>' +
                '<td class="num">' + x.evidenceCount + '</td>' +
                '<td><span class="rate-cell"><span class="ticks" style="--c:' + rateHex + '">' + ticks + '</span><b>' + A.pct(x.evidenceRate) + '</b></span></td>' +
                '<td><div class="stack">' + (A.CAT_ORDER.map(function (c) { return mix[c] ? '<i style="flex-grow:' + mix[c] + ';background:' + A.catOf(c).hex + '"></i>' : ''; }).join('') || '<i style="flex-grow:1;background:#1c1e25"></i>') + '</div></td>' +
                '<td>' + (x.evidenceCount ? A.catChip(x.dominantExposureCategory) : '<span class="muted">No evidence yet</span>') + '</td>' +
                '<td style="width:110px">' + A.spark(A.buckets(evs, 'recordedAt', 60000, 15), A.typeOf(a.type).hex, 100, 24) + '</td></tr>';
        }).join('');
        var tot = s.exposure.reduce(function (a, b) { return a + b.totalDecisions; }, 0);
        var ev = s.exposure.reduce(function (a, b) { return a + b.evidenceCount; }, 0);
        $('#foot').innerHTML = '<span><b>' + s.exposure.length + '</b> agents</span><span><b>' + tot + '</b> decisions</span><span><b>' + ev + '</b> evidence records</span><span>overall rate <b>' + (tot ? A.pct(ev / tot) : '—') + '</b></span>';
    }

    function renderHeat(s) {
        var cats = A.CAT_ORDER.slice(0, 3).concat(['General AI Decision Exposure']);
        var agents = s.exposure.map(function (x) { return x.agentId; });
        var max = 1;
        var grid = agents.map(function (id) {
            var m = A.groupCount(evByAgent(s, id), function (e) { return e.exposureCategory; });
            cats.forEach(function (c) { max = Math.max(max, m[c] || 0); });
            return m;
        });
        var head = '<div class="hr"><div></div>' + cats.map(function (c) { return '<div class="hh">' + A.esc(A.catOf(c).short) + '</div>'; }).join('') + '</div>';
        $('#heat').innerHTML = head + agents.map(function (id, i) {
            return '<div class="hr">' + A.agentHtml(id) + cats.map(function (c) {
                var n = grid[i][c] || 0, hex = A.catOf(c).hex, k = n / max;
                return '<div class="cell" title="' + A.esc(id + ' · ' + A.catOf(c).short + ': ' + n) + '" style="background:' + (n ? 'color-mix(in srgb, ' + hex + ' ' + Math.round(12 + k * 60) + '%, #111216)' : '#111216') + ';color:' + (n ? '#fff' : 'var(--faint)') + '">' + (n || '·') + '</div>';
            }).join('') + '</div>';
        }).join('') || '<div class="empty">' + A.ICON.empty + 'No data yet.</div>';
    }

    function loadBundle() {
        $('#bundle').textContent = 'Loading…';
        A.fetchJson('/api/evidence/export').then(function (b) {
            var preview = {
                exportedAt: b.exportedAt,
                disclaimer: b.disclaimer,
                evidenceRecords: (b.evidenceRecords || []).slice(0, 2).concat((b.evidenceRecords || []).length > 2 ? ['… ' + (b.evidenceRecords.length - 2) + ' more records'] : []),
                exposureSummaryByAgent: (b.exposureSummaryByAgent || []).slice(0, 3).concat((b.exposureSummaryByAgent || []).length > 3 ? ['… ' + (b.exposureSummaryByAgent.length - 3) + ' more agents'] : [])
            };
            $('#bundle').innerHTML = A.jsonHtml(preview);
        }).catch(function (e) { $('#bundle').textContent = 'Could not load bundle: ' + e.message; });
    }

    function openAgent(id) {
        var s = latest, x = s.exposure.filter(function (r) { return r.agentId === id; })[0] || {};
        var a = A.agentOf(id), t = A.typeOf(a.type);
        var ds = s.decisions.filter(function (d) { return d.agentId === id; });
        var oc = A.groupCount(ds, function (d) { return d.outcome; });
        var evs = evByAgent(s, id);
        var body =
            '<div class="chips" style="margin-bottom:16px">' + (a.type ? A.typeChip(a.type) + A.productChip(a.type) : '') + (x.evidenceCount ? A.catChip(x.dominantExposureCategory) : '') + '</div>' +
            '<div class="grid g-3" style="gap:10px">' +
            '<div class="card kpi"><div class="label">Decisions</div><div class="val">' + (x.totalDecisions || ds.length) + '</div></div>' +
            '<div class="card kpi"><div class="label">Evidence</div><div class="val">' + (x.evidenceCount || 0) + '</div></div>' +
            '<div class="card kpi"><div class="label">Rate</div><div class="val">' + (x.totalDecisions ? A.pct(x.evidenceRate) : '—') + '</div></div></div>' +
            '<div class="section-label label">Outcome breakdown</div>' +
            '<div class="stack" style="height:10px">' + Object.keys(oc).map(function (o) { return '<i style="flex-grow:' + oc[o] + ';background:' + (A.OUTCOMES[o] || '#a1a1aa') + '" title="' + o + ': ' + oc[o] + '"></i>'; }).join('') + '</div>' +
            '<div class="chips" style="margin-top:10px">' + Object.keys(oc).map(function (o) { return A.chip(o.replace(/_/g, ' ') + ' · ' + oc[o], A.OUTCOMES[o] || '#a1a1aa', { mono: true }); }).join('') + '</div>' +
            '<div class="section-label label">Evidence records (' + evs.length + ')</div>' +
            (evs.length ? '<div style="border:1px solid var(--line);border-radius:10px;overflow:hidden">' + evs.slice(0, 30).map(function (e) {
                return '<div class="srow" data-open-decision="' + A.esc(e.decisionId) + '" style="display:grid;grid-template-columns:70px 1fr auto;gap:10px;padding:9px 12px;border-bottom:1px solid #17181d;cursor:pointer">' +
                    '<span class="mono muted" style="font-size:11px">' + A.time(e.recordedAt) + '</span><span style="font-size:12px;color:var(--text-2);overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + A.esc(e.reason) + '</span>' +
                    A.catChip(e.exposureCategory) + '</div>';
            }).join('') + '</div>' : '<div class="muted">No evidence for this agent yet.</div>') +
            '<div class="callout" style="--c:var(--info);margin-top:16px">' + A.ICON.info + '<div>This profile is <b>informational</b>. Whether to retrain, reconfigure or reprice this agent stays with the carrier.</div></div>';
        A.openDrawer({ eyebrow: 'Qusar agent · ' + t.product, title: A.esc(id), body: body,
            foot: '<a class="btn sm" href="/pipeline/ingestion">All decisions</a><a class="btn sm" href="/pipeline/evidence">All evidence</a>' });
    }

    A.subscribe(function (s, ev) {
        latest = s;
        renderKpis(s); renderTable(s); renderHeat(s);
        if (ev.first) loadBundle();
    });

    $('#bundle-refresh').addEventListener('click', loadBundle);
    $('#rows').addEventListener('click', function (e) {
        var tr = e.target.closest('tr[data-agent]'); if (!tr) return;
        $$('#rows tr.sel').forEach(function (r) { r.classList.remove('sel'); });
        tr.classList.add('sel');
        openAgent(tr.getAttribute('data-agent'));
    });
})();
