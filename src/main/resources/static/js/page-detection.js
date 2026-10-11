/* Stage 02 · Detection & Compliance */
(function () {
    'use strict';
    var A = window.AEGIS, $ = A.$, $$ = A.$$;
    var HEX = A.STAGES.detection.hex;
    var PROD = { thr: 50, min: 5 };
    var state = { thr: 50, min: 5, rule: '', std: '' };
    var latest = null;

    function agentStats(s) {
        var m = {};
        s.decisions.forEach(function (d) {
            var x = m[d.agentId] || (m[d.agentId] = { id: d.agentId, total: 0, den: 0 });
            x.total++; if (d.outcome === 'DENIED') x.den++;
        });
        return Object.keys(m).map(function (k) { return m[k]; }).sort(function (a, b) { return (b.den / b.total) - (a.den / a.total); });
    }
    function inZone(x, thr, min) { return x.total >= min && x.den / x.total > thr / 100; }

    function renderKpis(s) {
        var n = s.decisions.length, f = s.flagged.length;
        $('#k-rev').textContent = A.fmt(n);
        $('#k-flag').textContent = A.fmt(f);
        var fr = s.flagged.filter(function (x) { return A.ruleOf(x).key === 'fraud'; }).length;
        $('#k-flag-f').textContent = fr + ' fraud · ' + (f - fr) + ' outlier';
        $('#k-flag-s').innerHTML = A.spark(A.buckets(s.flagged, 'reviewedAt', 60000, 15), HEX);
        $('#k-rate').textContent = n ? A.pct(f / n, 1) : '—';
        $('#k-rate-b').style.width = n ? (f / n * 100) + '%' : '0';
        $('#k-clr').textContent = A.fmt(Math.max(0, n - f));
        $('#r1').textContent = fr;
        $('#r2').textContent = f - fr;
    }

    function renderMeters() {
        var s = latest; if (!s) return;
        var stats = agentStats(s);
        var zone = stats.filter(function (x) { return inZone(x, state.thr, state.min); }).length;
        var prodZone = stats.filter(function (x) { return inZone(x, PROD.thr, PROD.min); }).length;
        var changed = state.thr !== PROD.thr || state.min !== PROD.min;
        $('#sim-res').innerHTML = zone + (changed ? ' <span style="font-size:11px;color:var(--dim)">vs ' + prodZone + ' in prod</span>' : '');
        $('#thr-l').textContent = state.thr + '%';
        $('#min-l').textContent = state.min;
        $('#meters').innerHTML = stats.map(function (x) {
            var r = x.den / x.total, a = A.agentOf(x.id);
            var z = inZone(x, state.thr, state.min);
            var small = x.total < state.min;
            var status = z ? A.chip('Outlier zone', HEX, { dot: true }) : small ? A.chip('Sample < ' + state.min, '#6b7280') : A.chip('Within threshold', '#34d399', { dot: true });
            var col = z ? 'linear-gradient(90deg, rgba(245,158,11,.35), rgba(245,158,11,.85))' : 'linear-gradient(90deg, ' + a.hex + '33, ' + a.hex + '88)';
            return '<div class="meter-row">' +
                '<div>' + A.agentHtml(x.id) + '<div class="meter-sub">' + x.den + ' denied / ' + x.total + ' decisions</div></div>' +
                '<div class="meter"><div class="fill" style="width:' + (r * 100) + '%;background:' + col + '"></div>' +
                '<div class="thr" style="left:' + state.thr + '%" data-l="' + state.thr + '%"></div><span class="txt">' + A.pct(r) + '</span></div>' +
                '<div style="text-align:right">' + status + '</div></div>';
        }).join('') || '<div class="empty">' + A.ICON.empty + 'No decisions yet.</div>';
    }

    function renderStds(s) {
        var by = A.groupCount(s.flagged, function (f) { return A.standardFor(f.jurisdiction).name; });
        var max = Math.max.apply(null, Object.keys(by).map(function (k) { return by[k]; }).concat([1]));
        $('#stds').innerHTML = A.STANDARDS.map(function (st) {
            var n = by[st.name] || 0;
            var jurs = A.JURISDICTIONS.filter(function (j) { return j.indexOf(st.prefix) === 0; });
            return '<div class="std' + (state.std === st.name ? ' on' : '') + '" data-std="' + A.esc(st.name) + '" style="--c:' + st.hex + '">' +
                '<div class="nm"><span class="chip" style="--c:' + st.hex + '"><span class="d"></span>' + A.esc(st.name) + '</span>' +
                jurs.map(function (j) { return '<span class="pref">' + j + '</span>'; }).join('') + '</div>' +
                '<div class="cnt">' + n + '</div>' +
                '<div class="full">' + A.esc(st.full) + '</div>' +
                '<div class="bar" style="--c:' + st.hex + '"><i style="width:' + (n / max * 100) + '%"></i></div></div>';
        }).join('');
    }

    function renderQueue(s, newIds) {
        var fresh = {}; (newIds || []).forEach(function (id) { fresh[id] = 1; });
        var rows = s.flagged.filter(function (f) {
            if (state.rule && A.ruleOf(f).key !== state.rule) return false;
            if (state.std && A.standardFor(f.jurisdiction).name !== state.std) return false;
            return true;
        });
        $('#q-count').textContent = rows.length + ' flagged';
        if (!rows.length) { $('#rows').innerHTML = A.emptyRow(8, s.flagged.length ? 'Nothing matches this filter.' : 'Nothing flagged yet — try “Test a fraud flag”.'); return; }
        $('#rows').innerHTML = rows.slice(0, 250).map(function (f) {
            return '<tr class="clickable' + (fresh[f.decisionId] ? ' flash' : '') + '" data-id="' + A.esc(f.decisionId) + '">' +
                '<td class="wrap" style="color:var(--text);font-weight:500">' + A.esc(f.flagReason) + '</td>' +
                '<td>' + A.ruleChip(f) + '</td>' +
                '<td>' + A.stdChip(f.jurisdiction) + '</td>' +
                '<td>' + A.agentHtml(f.agentId) + '</td>' +
                '<td class="id">#' + A.esc(A.short(f.decisionId)) + '</td>' +
                '<td>' + A.outcomeChip(f.outcome) + '</td>' +
                '<td>' + A.jurChip(f.jurisdiction) + '</td>' +
                '<td><span class="mono" style="font-size:11.5px">' + A.time(f.reviewedAt) + '</span> <span class="muted" style="font-size:11px">' + A.ago(f.reviewedAt) + '</span></td></tr>';
        }).join('');
    }

    A.subscribe(function (s, ev) {
        latest = s;
        renderKpis(s); renderMeters(); renderStds(s); renderQueue(s, ev.newIds);
    });

    function setSim() {
        state.thr = +$('#thr').value; state.min = +$('#min').value; renderMeters();
    }
    $('#thr').addEventListener('input', setSim);
    $('#min').addEventListener('input', setSim);
    $('#reset-sim').addEventListener('click', function () { $('#thr').value = PROD.thr; $('#min').value = PROD.min; setSim(); });
    $('#rule-seg').addEventListener('click', function (e) {
        var b = e.target.closest('button'); if (!b) return;
        state.rule = b.getAttribute('data-r');
        $$('#rule-seg button').forEach(function (x) { x.classList.toggle('on', x === b); });
        if (latest) renderQueue(latest);
    });
    $('#stds').addEventListener('click', function (e) {
        var d = e.target.closest('[data-std]'); if (!d) return;
        state.std = state.std === d.getAttribute('data-std') ? '' : d.getAttribute('data-std');
        renderStds(latest); renderQueue(latest);
        $('#queue-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
    $('#rows').addEventListener('click', function (e) {
        var tr = e.target.closest('tr[data-id]'); if (!tr) return;
        $$('#rows tr.sel').forEach(function (r) { r.classList.remove('sel'); });
        tr.classList.add('sel');
        A.openDecision(tr.getAttribute('data-id'));
    });
})();
