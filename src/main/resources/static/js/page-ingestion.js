/* Stage 01 · Ingestion */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var HEX = A.STAGES.ingestion.hex;
    var state = { q: '', type: '', agent: '', outcome: '', status: '', jur: '', sort: 'timestamp', dir: -1, selected: null };
    var MAX_ROWS = 250;

    function filtered(s) {
        var q = state.q.toLowerCase();
        return s.decisions.filter(function (d) {
            if (state.type && d.decisionType !== state.type) return false;
            if (state.agent && d.agentId !== state.agent) return false;
            if (state.outcome && d.outcome !== state.outcome) return false;
            if (state.jur && d.jurisdiction !== state.jur) return false;
            if (state.status && A.statusOf(d).key !== state.status) return false;
            if (q && (d.decisionId + ' ' + d.agentId + ' ' + d.reasoningTrail + ' ' + d.outcome).toLowerCase().indexOf(q) < 0) return false;
            return true;
        }).sort(function (a, b) {
            var x = a[state.sort], y = b[state.sort];
            if (state.sort === 'timestamp') { x = A.ts(x); y = A.ts(y); }
            return (x > y ? 1 : x < y ? -1 : 0) * state.dir;
        });
    }

    function renderTable(s, newIds) {
        var fresh = {}; (newIds || []).forEach(function (id) { fresh[id] = 1; });
        var rows = filtered(s);
        $('#in-view').textContent = A.fmt(rows.length) + ' in view';
        if (!rows.length) { $('#rows').innerHTML = A.emptyRow(9, s.decisions.length ? 'No decisions match these filters.' : 'Waiting for the first decision…'); }
        else {
            $('#rows').innerHTML = rows.slice(0, MAX_ROWS).map(function (d) {
                var st = A.statusOf(d);
                return '<tr class="clickable' + (fresh[d.decisionId] ? ' flash' : '') + (state.selected === d.decisionId ? ' sel' : '') + '" data-id="' + A.esc(d.decisionId) + '">' +
                    '<td><span class="mono" style="font-size:11.5px">' + A.time(d.timestamp) + '</span> <span class="muted" style="font-size:11px">' + A.ago(d.timestamp) + '</span></td>' +
                    '<td class="id">#' + A.esc(A.short(d.decisionId)) + '</td>' +
                    '<td>' + A.agentHtml(d.agentId) + '</td>' +
                    '<td>' + A.productChip(d.decisionType) + '</td>' +
                    '<td>' + A.typeChip(d.decisionType) + '</td>' +
                    '<td>' + A.outcomeChip(d.outcome) + '</td>' +
                    '<td>' + A.jurChip(d.jurisdiction) + '</td>' +
                    '<td>' + A.trackHtml(d) + '</td>' +
                    '<td>' + A.chip(st.label, st.hex, { dot: true }) + '</td></tr>';
            }).join('');
        }
        var by = A.groupCount(rows, function (d) { return A.statusOf(d).key; });
        $('#foot').innerHTML = '<span><b>' + A.fmt(rows.length) + '</b> decisions</span>' +
            '<span><b>' + (by.evidence || 0) + '</b> evidence</span><span><b>' + (by.flagged || 0) + '</b> flagged, pending</span>' +
            '<span><b>' + (by.cleared || 0) + '</b> cleared</span>' +
            (rows.length > MAX_ROWS ? '<span>showing newest ' + MAX_ROWS + '</span>' : '');
        $$('th.sortable').forEach(function (th) {
            var on = th.getAttribute('data-sort') === state.sort;
            th.querySelector('.arr').textContent = on ? (state.dir < 0 ? '▼' : '▲') : '';
        });
    }

    function renderKpis(s) {
        var n = s.decisions.length;
        $('#k-total').textContent = A.fmt(n);
        var last5 = s.decisions.filter(function (d) { return Date.now() - A.ts(d.timestamp) < 300000; }).length;
        $('#k-total-f').innerHTML = '<span class="delta-up">+' + last5 + '</span> in last 5 min';
        var perMin = A.buckets(s.decisions, 'timestamp', 60000, 15);
        $('#k-total-s').innerHTML = A.spark(perMin, HEX);
        var avg = perMin.reduce(function (a, b) { return a + b; }, 0) / 15;
        $('#k-rate').innerHTML = avg.toFixed(1) + '<small>/min</small>';
        var mx = Math.max.apply(null, perMin.concat([1]));
        $('#k-bars').innerHTML = perMin.map(function (v) { return '<i style="height:' + Math.max(2, v / mx * 34) + 'px" title="' + v + ' decisions"></i>'; }).join('');

        var mix = A.groupCount(s.decisions, function (d) { return d.decisionType; });
        var keys = Object.keys(A.TYPES);
        $('#k-mix').innerHTML = keys.map(function (k) { return '<span style="color:' + A.TYPES[k].hex + '">' + (mix[k] || 0) + '</span>'; }).join('<span class="muted"> · </span>');
        $('#k-mix-s').innerHTML = keys.map(function (k) { return mix[k] ? '<i style="flex-grow:' + mix[k] + ';background:' + A.TYPES[k].hex + '"></i>' : ''; }).join('');
        $('#k-mix-f').innerHTML = '<span class="legend">' + keys.map(function (k) { return '<span style="--c:' + A.TYPES[k].hex + '"><i></i>' + A.TYPES[k].label + '</span>'; }).join('') + '</span>';

        var pc = mix.UNDERWRITING || 0, cc = (mix.CLAIMS || 0) + (mix.FRAUD_FLAG || 0);
        $('#k-src').innerHTML = A.chip('PolicyCenter ' + pc, '#3b82f6') + ' ' + A.chip('ClaimCenter ' + cc, '#3b82f6');
        $('#k-src-f').textContent = 'BillingCenter: no agent feed in this build';
    }

    function renderSchema(s) {
        var d = s.decisions[0] || {};
        var F = [
            ['decisionId', 'String', 'auto', 'UUID generated if blank · natural key, duplicates rejected'],
            ['agentId', 'String', 'req', d.agentId],
            ['decisionType', 'String', 'req', d.decisionType],
            ['outcome', 'String', 'req', d.outcome],
            ['timestamp', 'Instant', 'auto', d.timestamp || 'now() if blank'],
            ['jurisdiction', 'String', 'req', d.jurisdiction],
            ['reasoningTrail', 'String', 'req', d.reasoningTrail]
        ];
        $('#schema').innerHTML = F.map(function (f) {
            return '<div class="f"><code>' + f[0] + '</code><span>' + (f[2] === 'req' ? A.chip('required', '#f87171', { mono: true }) : A.chip('auto', '#34d399', { mono: true })) + '</span>' +
                '<span class="ex" title="' + A.esc(f[3]) + '"><span class="ty">' + f[1] + '</span> · ' + A.esc(f[3] || '—') + '</span></div>';
        }).join('');
    }

    function renderDist(s) {
        var by = A.groupCount(s.decisions, function (d) { return d.agentId; });
        var mx = Math.max.apply(null, Object.keys(by).map(function (k) { return by[k]; }).concat([1]));
        $('#by-agent').innerHTML = Object.keys(by).sort(function (a, b) { return by[b] - by[a]; }).map(function (id) {
            var a = A.agentOf(id);
            return '<div class="hbar">' + A.agentHtml(id) + '<div class="bar" style="--c:' + a.hex + '"><i style="width:' + (by[id] / mx * 100) + '%"></i></div><span class="n">' + by[id] + '</span></div>';
        }).join('') || '<div class="muted">No data yet.</div>';
        var bj = A.groupCount(s.decisions, function (d) { return d.jurisdiction; });
        $('#by-jur').innerHTML = A.JURISDICTIONS.map(function (j) {
            return '<div class="jur' + (state.jur === j ? ' on' : '') + '" data-jur="' + j + '"><b>' + j + '</b><span>' + (bj[j] || 0) + ' decisions</span></div>';
        }).join('');
    }

    function fillFilters(s) {
        var ag = $('#f-agent'), cur = ag.value;
        var agents = Object.keys(A.groupCount(s.decisions, function (d) { return d.agentId; })).sort();
        ag.innerHTML = '<option value="">All agents</option>' + agents.map(function (a) { return '<option>' + A.esc(a) + '</option>'; }).join('');
        ag.value = cur;
        var oc = $('#f-outcome'), co = oc.value;
        oc.innerHTML = '<option value="">Any</option>' + Object.keys(A.OUTCOMES).map(function (o) { return '<option value="' + o + '">' + o.replace(/_/g, ' ') + '</option>'; }).join('');
        oc.value = co;
    }

    var latest = null;
    function renderAll(s, newIds) { latest = s; renderTable(s, newIds); renderKpis(s); renderSchema(s); renderDist(s); }

    A.subscribe(function (s, ev) { if (ev.first) fillFilters(s); renderAll(s, ev.newIds); });

    function rerender() { if (latest) { renderTable(latest); renderDist(latest); } }
    $('#q').addEventListener('input', function (e) { state.q = e.target.value; rerender(); });
    [['#f-type', 'type'], ['#f-agent', 'agent'], ['#f-outcome', 'outcome'], ['#f-status', 'status']].forEach(function (p) {
        $(p[0]).addEventListener('change', function (e) { state[p[1]] = e.target.value; rerender(); });
    });
    $('#clear').addEventListener('click', function () {
        state.q = state.type = state.agent = state.outcome = state.status = state.jur = '';
        $('#q').value = ''; ['#f-type', '#f-agent', '#f-outcome', '#f-status'].forEach(function (s) { $(s).value = ''; });
        rerender();
    });
    $$('th.sortable').forEach(function (th) {
        th.addEventListener('click', function () {
            var k = th.getAttribute('data-sort');
            if (state.sort === k) state.dir *= -1; else { state.sort = k; state.dir = k === 'timestamp' ? -1 : 1; }
            rerender();
        });
    });
    $('#rows').addEventListener('click', function (e) {
        var tr = e.target.closest('tr[data-id]'); if (!tr) return;
        state.selected = tr.getAttribute('data-id');
        $$('#rows tr.sel').forEach(function (r) { r.classList.remove('sel'); });
        tr.classList.add('sel');
        A.openDecision(state.selected);
    });
    $('#by-jur').addEventListener('click', function (e) {
        var j = e.target.closest('[data-jur]'); if (!j) return;
        state.jur = state.jur === j.getAttribute('data-jur') ? '' : j.getAttribute('data-jur');
        rerender();
        $('#rows').closest('.card').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    document.addEventListener('aegis:drawer-closed', function () { state.selected = null; });
})();
