// Pure utility functions — no DOM, no global state, no side effects.

function parseEntryDate(dateStr) {
    if (!dateStr) return new Date();
    if (/^\d{4}-/.test(dateStr)) return new Date(dateStr);
    const [d, m, y] = dateStr.split('.');
    return new Date(parseInt(y), parseInt(m) - 1, parseInt(d));
}

// Returns { since, until } as 'YYYY-MM-DD' strings, or null for unknown type.
// Accepts an optional `today` date for testability.
function computeDateRange(type, today = new Date()) {
    const pad = n => String(n).padStart(2, '0');
    const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    if (type === 'prev-month') {
        const first = new Date(today.getFullYear(), today.getMonth() - 1, 1);
        const last  = new Date(today.getFullYear(), today.getMonth(), 0);
        return { since: fmt(first), until: fmt(last) };
    }
    if (type === 'this-year')  return { since: `${today.getFullYear()}-01-01`, until: fmt(today) };
    if (type === 'prev-year') {
        const y = today.getFullYear() - 1;
        return { since: `${y}-01-01`, until: `${y}-12-31` };
    }
    if (type === 'last-365') {
        const d = new Date(today.getTime() - 365 * 24 * 60 * 60 * 1000);
        return { since: fmt(d), until: fmt(today) };
    }
    return null;
}

// Returns { buckets, useMonths }.
// Monthly buckets when range > 61 days, daily otherwise.
function buildEmptyBuckets(sinceDate, untilDate) {
    const diffDays  = Math.round((untilDate - sinceDate) / (1000 * 60 * 60 * 24));
    const useMonths = diffDays > 61;
    const buckets   = [];

    if (useMonths) {
        let d = new Date(sinceDate.getFullYear(), sinceDate.getMonth(), 1);
        const end = new Date(untilDate.getFullYear(), untilDate.getMonth(), 1);
        while (d <= end) {
            buckets.push({ year: d.getFullYear(), month: d.getMonth(), value: 0 });
            d = new Date(d.getFullYear(), d.getMonth() + 1, 1);
        }
    } else {
        let d = new Date(sinceDate);
        d.setHours(0, 0, 0, 0);
        while (d <= untilDate) {
            const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
            buckets.push({ iso, month: d.getMonth(), day: d.getDate(), year: d.getFullYear(), value: 0 });
            d = new Date(d.getTime() + 24 * 60 * 60 * 1000);
        }
    }

    return { buckets, useMonths };
}

// Groups rawBuckets so the result has at most maxBars entries. Returns [{ label, value }].
function groupBuckets(rawBuckets, useMonths, maxBars = 20) {
    const MON       = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const groupSize = Math.max(1, Math.ceil(rawBuckets.length / maxBars));
    const grouped   = [];

    for (let i = 0; i < rawBuckets.length; i += groupSize) {
        const group = rawBuckets.slice(i, i + groupSize);
        const value = Math.round(group.reduce((s, b) => s + b.value, 0) * 100) / 100;
        const f = group[0], l = group[group.length - 1];
        let label;

        if (useMonths) {
            label = groupSize === 1
                ? `${MON[f.month]} '${String(f.year).slice(2)}`
                : f.year === l.year
                    ? `${MON[f.month]}–${MON[l.month]} '${String(f.year).slice(2)}`
                    : `${MON[f.month]} '${String(f.year).slice(2)}–${MON[l.month]} '${String(l.year).slice(2)}`;
        } else {
            label = groupSize === 1
                ? `${MON[f.month]} ${f.day}`
                : f.month === l.month
                    ? `${MON[f.month]} ${f.day}–${l.day}`
                    : `${MON[f.month]} ${f.day}–${MON[l.month]} ${l.day}`;
        }

        grouped.push({ label, value });
    }

    return grouped;
}

// Node/Vitest compatibility — exposes functions when running outside the browser.
if (typeof module !== 'undefined' && module.exports !== undefined) {
    module.exports = { parseEntryDate, computeDateRange, buildEmptyBuckets, groupBuckets };
}
