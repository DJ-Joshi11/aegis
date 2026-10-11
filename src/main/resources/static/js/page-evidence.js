/* Stage 03 · Evidence Mapping */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var HEX = A.STAGES.evidence.hex;
    var state = { cat: '', src: '' };
    var latest = null;

    function renderKpis(s) {
        var ev = s.evidence, gaps = ev.filter(function (e) { return e.controlGapIdentified; }).length;
        $('#k-ev').textContent = A.fmt(ev.length);
        var cats = Object.keys(A.groupCount(ev, function (e) { return e.exposureCategory; })).length;
        $('#k-ev-f').textContent = 'across ' + cats + ' categor' + (cats === 1 ? 'y' : 'ies');
        $('#k-ev-s').innerHTML = A.spark(A.buckets(ev, 'recordedAt', 60000, 15), HEX);
        $('#k-fl').textContent = A.fmt(ev.length - gaps);
        $('#k-gap').textContent = gaps;
        var covered = s.flagged.filter(function (f) { return s.evById[f.decisionId]; }).length;
        $('#k-cov').textContent = s.flagged.length ? A.pct(covered / s.flagged.length) : '—';
        $('#t-a').textContent = ev.length - gaps;
        $('#t-b').textContent = gaps;
    }

    function renderMap(s) {
        var byCat = A.groupCount(s.evidence, function (e) { return e.exposureCategory; });
        var total = s.evidence.length || 1;
        var rows = [
            { type: 'UNDERWRITING', cat: 'Underwriting Risk Exposure' },
            { type: 'CLAIMS', cat: 'Claims Handling Exposure' },
            { type: 'FRAUD_FLAG', cat: 'Fraud & Integrity Exposure' },
            { type: null, cat: 'General AI Decision Exposure' }
        ];
        $('#map').innerHTML = rows.map(function (r) {
            var c = A.catOf(r.cat), n = byCat[r.cat] || 0;
            var t = r.type ? A.typeOf(r.type) : { label: 'Any other type', product: 'fallback', hex: '#a1a1aa' };
            return '<div class="map-row' + (state.cat === r.cat ? ' on' : '') + '" data-cat="' + A.esc(r.cat) + '" style="--c:' + c.hex + '">' +
                '<div class="src"><span class="chip" style="--c:' + t.hex + '"><span class="d"></span>' + A.esc(r.type || 'OTHER') + '</span></div>' +
                '<div class="wire' + (n ? '' : ' idle') + '"><div class="flow"></div><span class="lbl">' + A.esc(t.product) + '</span></div>' +
                '<div class="dst"><div class="top"><b>' + A.esc(c.short) + '</b><span class="n">' + n + '</span></div>' +
                '<div class="bar" style="--c:' + c.hex + '"><i style="width:' + (n / total * 100) + '%"></i></div></div></div>';
        }).join('');
    }

    function tagFor(e, s) {
        var f = s.flagById[e.decisionId];
        var d = s.byId[e.decisionId];
        var j = (f && f.jurisdiction) || (d && d.jurisdiction);
        return j ? A.stdChip(j) : '<span class="muted">—</span>';
    }

    function renderRows(s, newIds) {
        var fresh = {}; (newIds || []).forEach(function (id) { fresh[id] = 1; });
        var rows = s.evidence.filter(function (e) {
            if (state.cat && e.exposureCategory !== state.cat) return false;
            if (state.src === 'gap' && !e.controlGapIdentified) return false;
            if (state.src === 'flag' && e.controlGapIdentified) return false;
            return true;
        });
        $('#rec-count').textContent = rows.length + ' records';
        $('#rec-sub').textContent = state.cat ? 'filtered to ' + A.catOf(state.cat).short : 'newest first · click to inspect';
        if (!rows.length) { $('#rows').innerHTML = A.emptyRow(7, s.evidence.length ? 'Nothing matches this filter.' : 'No evidence yet — inject a fraud flag to create one.'); return; }
        $('#rows').innerHTML = rows.slice(0, 250).map(function (e) {
            return '<tr class="clickable' + (fresh[e.decisionId] ? ' flash' : '') + '" data-id="' + A.esc(e.decisionId) + '">' +
                '<td>' + A.catChip(e.exposureCategory) + '</td>' +
                '<td>' + (e.controlGapIdentified ? A.chip('Control gap', '#f87171', { dot: true }) : A.chip('Detection flag', HEX, { dot: true })) + '</td>' +
                '<td class="wrap">' + A.esc(e.reason) + '</td>' +
                '<td>' + A.agentHtml(e.agentId) + '</td>' +
                '<td class="id">#' + A.esc(A.short(e.decisionId)) + '</td>' +
                '<td>' + tagFor(e, s) + '</td>' +
                '<td><span class="mono" style="font-size:11.5px">' + A.time(e.recordedAt) + '</span> <span class="muted" style="font-size:11px">' + A.ago(e.recordedAt) + '</span></td></tr>';
        }).join('');
    }

    function renderAnat(s) {
        var e = s.evidence[0];
        if (!e) { $('#anat').innerHTML = '<div class="empty" style="grid-column:1/-1">' + A.ICON.empty + 'No evidence records yet.</div>'; return; }
        var F = [
            ['decisionId', '<span class="mono" style="font-size:11.5px">' + A.esc(e.decisionId) + '</span>', 'Links straight back to the immutable audit row from Stage 01.'],
            ['agentId', A.agentHtml(e.agentId), 'Which Qusar agent made the call.'],
            ['exposureCategory', A.catChip(e.exposureCategory), 'Broad bucket derived from the decision type.'],
            ['controlGapIdentified', A.chip(String(e.controlGapIdentified), e.controlGapIdentified ? '#f87171' : '#34d399', { mono: true }), 'True only when the review window was missed (Trigger B).'],
            ['reason', A.esc(e.reason), 'Why this record exists — copied from the flag or the sweep.'],
            ['recordedAt', '<span class="mono" style="font-size:11.5px">' + A.esc(e.recordedAt) + '</span>', 'When AEGIS created the evidence.'],
            ['amount / premium', '<span class="muted">— not a field</span>', 'Deliberately absent: AEGIS never moves money or prices risk.']
        ];
        $('#anat').innerHTML = F.map(function (f) {
            return '<div class="k">' + f[0] + '</div><div class="v">' + f[1] + '<span class="why">' + A.esc(f[2]) + '</span></div>';
        }).join('');
    }

    function renderX(s) {
        var total = s.evidence.length || 1;
        $('#xtab').innerHTML = A.CAT_ORDER.map(function (c) {
            var rs = s.evidence.filter(function (e) { return e.exposureCategory === c; });
            var g = rs.filter(function (e) { return e.controlGapIdentified; }).length;
            return '<tr><td>' + A.catChip(c) + '</td><td class="num">' + (rs.length - g) + '</td><td class="num">' + g + '</td><td class="num"><b>' + rs.length + '</b></td>' +
                '<td><div class="bar" style="--c:' + A.catOf(c).hex + '"><i style="width:' + (rs.length / total * 100) + '%"></i></div></td></tr>';
        }).join('');
    }

    A.subscribe(function (s, ev) {
        latest = s;
        renderKpis(s); renderMap(s); renderRows(s, ev.newIds); renderAnat(s); renderX(s);
    });

    $('#map').addEventListener('click', function (e) {
        var r = e.target.closest('[data-cat]'); if (!r) return;
        state.cat = state.cat === r.getAttribute('data-cat') ? '' : r.getAttribute('data-cat');
        renderMap(latest); renderRows(latest);
    });
    $('#clear-cat').addEventListener('click', function () { state.cat = ''; renderMap(latest); renderRows(latest); });
    $('#src-seg').addEventListener('click', function (e) {
        var b = e.target.closest('button'); if (!b) return;
        state.src = b.getAttribute('data-s');
        $$('#src-seg button').forEach(function (x) { x.classList.toggle('on', x === b); });
        renderRows(latest);
    });
    $('#rows').addEventListener('click', function (e) {
        var tr = e.target.closest('tr[data-id]'); if (!tr) return;
        $$('#rows tr.sel').forEach(function (r) { r.classList.remove('sel'); });
        tr.classList.add('sel');
        A.openDecision(tr.getAttribute('data-id'));
    });
})();
