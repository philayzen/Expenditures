const { parseEntryDate, computeDateRange, buildEmptyBuckets, groupBuckets } =
    require('../../static/scripts/utils.js');

// ── parseEntryDate ────────────────────────────────────────────────────────────

describe('parseEntryDate', () => {
    test('ISO date YYYY-MM-DD', () => {
        const d = parseEntryDate('2024-01-15');
        expect(d.getFullYear()).toBe(2024);
        expect(d.getMonth()).toBe(0);
        expect(d.getDate()).toBe(15);
    });

    test('ISO with time component', () => {
        const d = parseEntryDate('2024-03-20T14:29:13.000');
        expect(d.getFullYear()).toBe(2024);
        expect(d.getMonth()).toBe(2);
        expect(d.getDate()).toBe(20);
    });

    test('German DD.MM.YYYY', () => {
        const d = parseEntryDate('15.01.2024');
        expect(d.getFullYear()).toBe(2024);
        expect(d.getMonth()).toBe(0);
        expect(d.getDate()).toBe(15);
    });

    test('empty string returns a Date instance', () => {
        expect(parseEntryDate('')).toBeInstanceOf(Date);
    });

    test('null/undefined returns a Date instance', () => {
        expect(parseEntryDate(null)).toBeInstanceOf(Date);
        expect(parseEntryDate(undefined)).toBeInstanceOf(Date);
    });
});

// ── computeDateRange ──────────────────────────────────────────────────────────

describe('computeDateRange', () => {
    // March 15 2024 (2024 is a leap year → Feb has 29 days)
    const today = new Date(2024, 2, 15);

    test('prev-month: first and last day of February 2024', () => {
        const { since, until } = computeDateRange('prev-month', today);
        expect(since).toBe('2024-02-01');
        expect(until).toBe('2024-02-29');
    });

    test('this-year: Jan 1 to today', () => {
        const { since, until } = computeDateRange('this-year', today);
        expect(since).toBe('2024-01-01');
        expect(until).toBe('2024-03-15');
    });

    test('prev-year: full previous calendar year', () => {
        const { since, until } = computeDateRange('prev-year', today);
        expect(since).toBe('2023-01-01');
        expect(until).toBe('2023-12-31');
    });

    test('last-365: 365 days ago to today', () => {
        const { since, until } = computeDateRange('last-365', today);
        expect(until).toBe('2024-03-15');
        const sinceDate = new Date(since);
        const diffDays  = Math.round((today - sinceDate) / (1000 * 60 * 60 * 24));
        expect(diffDays).toBe(365);
    });

    test('unknown type returns null', () => {
        expect(computeDateRange('unknown', today)).toBeNull();
    });

    test('prev-month at start of year wraps to previous year', () => {
        const jan1 = new Date(2024, 0, 10);
        const { since, until } = computeDateRange('prev-month', jan1);
        expect(since).toBe('2023-12-01');
        expect(until).toBe('2023-12-31');
    });
});

// ── buildEmptyBuckets ─────────────────────────────────────────────────────────

describe('buildEmptyBuckets', () => {
    test('>61 days → monthly mode, correct bucket count', () => {
        const since = new Date(2024, 0, 1);   // Jan 1
        const until = new Date(2024, 2, 31);  // Mar 31 → 90 days → 3 months
        const { buckets, useMonths } = buildEmptyBuckets(since, until);
        expect(useMonths).toBe(true);
        expect(buckets).toHaveLength(3);
        expect(buckets[0]).toMatchObject({ year: 2024, month: 0, value: 0 });
        expect(buckets[1]).toMatchObject({ year: 2024, month: 1, value: 0 });
        expect(buckets[2]).toMatchObject({ year: 2024, month: 2, value: 0 });
    });

    test('≤61 days → daily mode, exact day count', () => {
        const since = new Date(2024, 0, 1);   // Jan 1
        const until = new Date(2024, 0, 10);  // Jan 10 → 10 days inclusive
        const { buckets, useMonths } = buildEmptyBuckets(since, until);
        expect(useMonths).toBe(false);
        expect(buckets).toHaveLength(10);
        expect(buckets[0]).toMatchObject({ month: 0, day: 1, year: 2024, value: 0 });
        expect(buckets[9]).toMatchObject({ month: 0, day: 10, year: 2024, value: 0 });
    });

    test('daily buckets span across months', () => {
        const since = new Date(2024, 0, 30);  // Jan 30
        const until = new Date(2024, 1, 2);   // Feb 2 → 4 days
        const { buckets } = buildEmptyBuckets(since, until);
        expect(buckets).toHaveLength(4);
        expect(buckets[0]).toMatchObject({ month: 0, day: 30 });
        expect(buckets[3]).toMatchObject({ month: 1, day: 2 });
    });

    test('all bucket values initialised to 0', () => {
        const since = new Date(2024, 0, 1);
        const until = new Date(2024, 5, 30);
        const { buckets } = buildEmptyBuckets(since, until);
        expect(buckets.every(b => b.value === 0)).toBe(true);
    });

    test('monthly buckets span year boundary', () => {
        const since = new Date(2023, 11, 1);  // Dec 2023
        const until = new Date(2024, 1,  1);  // Feb 2024 → 3 months
        const { buckets } = buildEmptyBuckets(since, until);
        expect(buckets).toHaveLength(3);
        expect(buckets[0]).toMatchObject({ year: 2023, month: 11 });
        expect(buckets[2]).toMatchObject({ year: 2024, month: 1  });
    });
});

// ── groupBuckets ──────────────────────────────────────────────────────────────

describe('groupBuckets', () => {
    test('no grouping when count < maxBars (monthly)', () => {
        const buckets = [
            { month: 0, year: 2024, value: 10 },
            { month: 1, year: 2024, value: 20 },
        ];
        const grouped = groupBuckets(buckets, true, 20);
        expect(grouped).toHaveLength(2);
        expect(grouped[0]).toEqual({ label: "Jan '24", value: 10 });
        expect(grouped[1]).toEqual({ label: "Feb '24", value: 20 });
    });

    test('monthly labels with same-year span', () => {
        // 3 buckets forced into 1 group
        const buckets = [
            { month: 0, year: 2024, value: 10 },
            { month: 1, year: 2024, value: 20 },
            { month: 2, year: 2024, value: 30 },
        ];
        const grouped = groupBuckets(buckets, true, 1);
        expect(grouped).toHaveLength(1);
        expect(grouped[0].label).toBe("Jan–Mar '24");
        expect(grouped[0].value).toBe(60);
    });

    test('monthly labels spanning two years', () => {
        const buckets = [
            { month: 11, year: 2023, value: 5 },
            { month: 0,  year: 2024, value: 7 },
        ];
        const grouped = groupBuckets(buckets, true, 1);
        expect(grouped[0].label).toBe("Dec '23–Jan '24");
        expect(grouped[0].value).toBe(12);
    });

    test('no grouping when count < maxBars (daily)', () => {
        const buckets = [
            { month: 3, day: 15, year: 2024, value: 5 },
            { month: 3, day: 16, year: 2024, value: 3 },
        ];
        const grouped = groupBuckets(buckets, false, 20);
        expect(grouped[0]).toEqual({ label: 'Apr 15', value: 5 });
        expect(grouped[1]).toEqual({ label: 'Apr 16', value: 3 });
    });

    test('daily labels with same-month range', () => {
        const buckets = [
            { month: 3, day: 10, year: 2024, value: 1 },
            { month: 3, day: 11, year: 2024, value: 2 },
            { month: 3, day: 12, year: 2024, value: 3 },
        ];
        const grouped = groupBuckets(buckets, false, 1);
        expect(grouped[0].label).toBe('Apr 10–12');
        expect(grouped[0].value).toBe(6);
    });

    test('daily labels crossing a month boundary', () => {
        const buckets = [
            { month: 0, day: 30, year: 2024, value: 1 },
            { month: 1, day:  1, year: 2024, value: 1 },
        ];
        const grouped = groupBuckets(buckets, false, 1);
        expect(grouped[0].label).toBe('Jan 30–Feb 1');
        expect(grouped[0].value).toBe(2);
    });

    test('result length never exceeds maxBars', () => {
        const buckets = Array.from({ length: 25 }, (_, i) => ({
            month: i % 12, year: 2024 + Math.floor(i / 12), value: 1,
        }));
        const grouped = groupBuckets(buckets, true, 20);
        expect(grouped.length).toBeLessThanOrEqual(20);
    });

    test('values are summed and rounded to 2 decimal places', () => {
        const buckets = [
            { month: 0, year: 2024, value: 0.1 },
            { month: 1, year: 2024, value: 0.2 },
        ];
        const grouped = groupBuckets(buckets, true, 1);
        expect(grouped[0].value).toBe(0.3);
    });
});
