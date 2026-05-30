// ── Element IDs ───────────────────────────────────────────────────────────────
const API_BASE = "http://localhost:5000";

const IDS = {
    reweTable:    "rewe-expenditures-table",
    bankTable:    "bank-expenditures-table",
    reweCatSpan:  "rewe_category",
    bankCatSpan:  "bank_category",
    reweCatChart: "reweCategoryChart",
    bankCatChart: "bankCategoryChart",
    reweBarChart: "reweTimeChart",
    bankBarChart: "bankTimeChart",
    reweSearch:   "rewe-search",
    bankSearch:   "bank-search",
    reweTab:      "rewe-tab",
    sinceInput:   "since",
    untilInput:   "until",
};

// ── Theme tokens ─────────────────────────────────────────────────────────────
const cssVar = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

// Chart colours are Chart.js/canvas concerns — defined here so they're easy to find.
const CHART = {
    barBg:          'rgba(61, 90, 62, 0.55)',
    barBorder:      '#3d5a3e',
    avgLine:        '#a07850',
    doughnutBorder: '#f5f0e8',
    legendText:     '#1e2420',
};

const palette = [
    '#3d5a3e', '#5a7f5b', '#c9b99a', '#1e2420',
    '#8aab8b', '#a07850', '#e2d5c0', '#2a3e2b',
];
const chartInstances = {};
let expenseTypes

// ── Data store ────────────────────────────────────────────────────────────────
let rewe_individual_data = null;
let rewe_name_data       = null;
let rewe_cat_data        = null;
let bank_individual_data = null;
let bank_name_data       = null;
let bank_cat_data        = null;
let bank_type_map        = null;

// ── Init ──────────────────────────────────────────────────────────────────────
window.onload = async function () {
    expenseTypes = await fetch(`${API_BASE}/get_expense_types`).then(r => r.json());
    document.getElementById(IDS.reweSearch).addEventListener("input", filterTables);
    document.getElementById(IDS.bankSearch).addEventListener("input", filterTables);
    document.querySelectorAll(`#${IDS.reweTable} th[data-col], #${IDS.bankTable} th[data-col]`).forEach(th => {
        th.style.cursor = 'pointer';
        th.onclick = () => {
            const target = th.closest('table').id === IDS.reweTable ? 'rewe' : 'bank';
            const col = th.dataset.col;
            const state = sortState[target];
            const { type } = COL_CONFIG[col] || { type: 'text' };
            if (type === 'date') {
                state.dir = (state.col === col && state.dir === -1) ? 1 : -1;
            } else {
                state.dir = type === 'number' ? -1 : 1;
            }
            state.col = col;
            refresh_tables();
        };
    });
    window.addEventListener('resize', () => {
        const target = document.getElementById(IDS.reweTab).classList.contains("active") ? "rewe" : "bank";
        requestAnimationFrame(() => syncCardHeights(target));
    });
    await update_refresh();
    showContent('bank');
};

// ── Search filter ─────────────────────────────────────────────────────────────
function filterTables() {
    filterTable(IDS.reweSearch, IDS.reweTable);
    filterTable(IDS.bankSearch, IDS.bankTable);
}

function filterTable(searchId, tableId) {
    const query = document.getElementById(searchId).value.toLowerCase();
    document.querySelectorAll(`#${tableId} tbody tr`).forEach(row => {
        const name_cell = row.querySelector("td.name_field");
        const cat_cell = row.querySelector("td.cat_field");
        let is_in_name = name_cell && name_cell.textContent.toLowerCase().includes(query)
        let is_in_cat = cat_cell && cat_cell.textContent.toLowerCase().includes(query)
        row.style.display = (is_in_name || is_in_cat) ? "" : "none";
    });
}

// ── Quick date-range setters ──────────────────────────────────────────────────
function setDateRange(type) {
    const range = computeDateRange(type);
    if (!range) return;
    document.getElementById(IDS.sinceInput).value = range.since;
    document.getElementById(IDS.untilInput).value = range.until;
    update_refresh();
}

// ── Effective date helper ─────────────────────────────────────────────────────
function getEffectiveDates(individualData) {
    const sinceVal = document.getElementById(IDS.sinceInput).value;
    const untilVal = document.getElementById(IDS.untilInput).value;
    let since;

    if (sinceVal) {
        since = new Date(sinceVal);
    } else if (individualData && individualData.length > 0) {
        since = individualData.reduce((min, e) => {
            const d = parseEntryDate(e.date);
            return d < min ? d : min;
        }, parseEntryDate(individualData[0].date));
    } else {
        since = new Date();
        since.setFullYear(since.getFullYear() - 1);
    }

    return { since, until: untilVal ? new Date(untilVal) : new Date() };
}

// ── Data fetching ─────────────────────────────────────────────────────────────
async function update_data() {
    const since = document.getElementById(IDS.sinceInput).value;
    const until = document.getElementById(IDS.untilInput).value;

    let categoryTypes;
    [
        rewe_individual_data, rewe_name_data, rewe_cat_data,
        bank_individual_data, bank_name_data, bank_cat_data,
        categoryTypes,
    ] = await Promise.all([
        get_data("get_rewe_expenditures_individually",            since, until),
        get_data("get_rewe_expenditures_grouped_by_name_and_cat", since, until),
        get_data("get_rewe_expenditures_grouped_by_category",     since, until),
        get_data("get_bank_expenditures_individually",            since, until),
        get_data("get_bank_expenditures_grouped_by_name_and_cat", since, until),
        get_data("get_bank_expenditures_grouped_by_category",     since, until),
        get_data("get_category_types"),
    ]);

    bank_type_map = Object.fromEntries(categoryTypes.map(ct => [ct.category, ct.expense_type]));
}

async function update_refresh() {
    await update_data();
    refresh_tables();
}

async function get_data(endpoint, since = "", until = "") {
    const params = {};
    if (since) params.since = since;
    if (until) params.until = until;
    const url = new URL(`${API_BASE}/${endpoint}`);
    url.search = new URLSearchParams(params).toString();
    return fetch(url).then(r => r.json());
}

// ── Table refresh orchestration ───────────────────────────────────────────────
function refresh_tables() {
    const target = document.getElementById(IDS.reweTab).classList.contains("active") ? "rewe" : "bank";
    fillAllForTarget(target);
    filterTables();
}

function fillAllForTarget(target) {
    fillPieChart(target);
    fillBarChart(target, target === 'bank' ? bank_type_map : null);
    requestAnimationFrame(() => syncCardHeights(target));
    updateSortIndicators(target === 'rewe' ? IDS.reweTable : IDS.bankTable, target);

    const individualActive = document.getElementById(`${target}_group_individual`).classList.contains("active");
    const nameActive       = document.getElementById(`${target}_group_name`).classList.contains("active");

    if (individualActive)
        target === 'rewe' ? fill_rewe_table_individual() : fill_bank_table_individual();
    else if (nameActive)
        target === 'rewe' ? fill_rewe_table_name() : fill_bank_table_name();
}

// ── Generic table filler ──────────────────────────────────────────────────────
function fillTable(values, tableId, columnConfigs) {
    const tbody = document.querySelector(`#${tableId} tbody`);
    tbody.innerHTML = "";
    values.forEach(exp => {
        const row = document.createElement("tr");
        columnConfigs.forEach(cfg => {
            const td = document.createElement("td");
            let value = exp[cfg.field];
            if (cfg.formatter) value = cfg.formatter(value);
            if (cfg.style)     td.setAttribute('style', cfg.style);
            if (cfg.onclick)   td.onclick = cfg.onclick(exp);
            if (cfg.contentEditable) {
                td.setAttribute('contenteditable', 'true');
                td.dataset.original = value;
            }
            if (cfg.className) td.className = cfg.className;
            td.textContent = value;
            row.appendChild(td);
        });
        tbody.appendChild(row);
    });
}

// ── REWE table fillers ────────────────────────────────────────────────────────
function fill_rewe_table(values, tableId, renamable = false, hasDate = true) {
    let cols = hasDate ? [{ field: 'date', formatter: v => v.split("T")[0] }] : [];
    cols = cols.concat([
        {
            field: 'display_name',
            className: 'name_field',
            onclick: renamable ? exp => () => rename_popup(exp) : null,
        },
        { field: 'amount', style: 'text-align: center;' },
        { field: 'price',  formatter: v => Math.round(v * 100) / 100 },
        { field: 'category', className: 'cat_field' },
    ]);
    fillTable(values, tableId, cols);
}

function fill_rewe_table_individual() { fill_rewe_table(sortData(rewe_individual_data, 'rewe'), IDS.reweTable, true, true);  }
function fill_rewe_table_name()       { fill_rewe_table(sortData(rewe_name_data,       'rewe'), IDS.reweTable, true, false); }

// ── Bank table fillers ────────────────────────────────────────────────────────
function fill_bank_table(values, tableId, renamable = false, hasDate = true, hasSource = true, hasPurpose=false) {
    let cols = hasDate ? [{ field: 'date', formatter: v => v.split("T")[0] }] : [];
    if (hasSource) cols.push({ field: 'source' });
    cols = cols.concat([
        {
            field: 'display_name',
            className: 'name_field',
            onclick: renamable ? exp => () => rename_popup(exp) : null,
        },
        { field: 'price',    style: 'text-align: center;', formatter: v => Math.round(v * 100) / 100 },
        { field: 'category', className: 'cat_field' },
    ]);
    fillTable(values, tableId, cols);
}

function fill_bank_table_individual() { fill_bank_table(sortData(bank_individual_data, 'bank'), IDS.bankTable, true, true,  true, true);  }
function fill_bank_table_name()       { fill_bank_table(sortData(bank_name_data,       'bank'), IDS.bankTable, true, false, false, false); }

// ── Pie charts ────────────────────────────────────────────────────────────────
function fillPieChart(target) {
    const catData   = target === 'rewe' ? rewe_cat_data        : bank_cat_data;
    const indivData = target === 'rewe' ? rewe_individual_data : bank_individual_data;
    const chartId   = target === 'rewe' ? IDS.reweCatChart     : IDS.bankCatChart;
    const spanId    = target === 'rewe' ? IDS.reweCatSpan      : IDS.bankCatSpan;

    const { since, until } = getEffectiveDates(indivData);
    const avgDays = Math.max(1, Math.round((until - since) / (1000 * 60 * 60 * 24)));

    const labels = catData.map(e => e.category || "Uncategorised");
    const values = catData.map(e => parseFloat(e.price) || 0);

    buildPieChart(labels, values, document.getElementById(chartId).getContext('2d'), chartId, avgDays);

    const total = values.reduce((s, v) => s + v, 0);
    document.getElementById(spanId).innerHTML =
        `Spending by Category<br><span style="font-weight:400;font-size:0.75rem;letter-spacing:0.04em;color:#a07850;">${total.toFixed(2)}€</span>`;
}

function buildPieChart(labels, values, ctx, chartId, avgDays) {
    if (chartInstances[chartId]) chartInstances[chartId].destroy();
    const fontMono  = cssVar('--font-mono') || "'DM Mono', monospace";

    chartInstances[chartId] = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: palette.slice(0, labels.length),
                borderColor: CHART.doughnutBorder,
                borderWidth: 3,
                hoverOffset: 8,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '62%',
            plugins: {
                legend: {
                    display: false,
                },
                tooltip: {
                    callbacks: {
                        label: ctx => {
                            const val = ctx.parsed;
                            const lines = [` €${val.toFixed(2)}`];
                            if (avgDays > 0) lines.push(` avg/month: €${(val / (avgDays / 30.44)).toFixed(2)}`);
                            return lines;
                        },
                    },
                    bodyFont: { family: fontMono },
                },
            },
        },
    });
}

// ── Bar charts ────────────────────────────────────────────────────────────────
function fillBarChart(target, typeMap = null) {
    const indivData = target === 'rewe' ? rewe_individual_data : bank_individual_data;
    if (!indivData) return;

    const chartId  = target === 'rewe' ? IDS.reweBarChart : IDS.bankBarChart;
    const getVal   = target === 'rewe'
        ? entry => (parseFloat(entry.price) || 0) * (parseInt(entry.amount) || 1)
        : entry => parseFloat(entry.price) || 0;

    const { since, until } = getEffectiveDates(indivData);
    buildBarChart(indivData, chartId, since, until, getVal, typeMap);
}

function buildBarChart(individualData, chartId, sinceDate, untilDate, getEntryValue, typeMap = null) {
    const { buckets, useMonths } = buildEmptyBuckets(sinceDate, untilDate);
    const maxBars    = useMonths ? 12 : 10;
    const typeTotals = {};

    individualData.forEach(entry => {
        const ed  = parseEntryDate(entry.date);
        const val = getEntryValue(entry);
        let idx;
        if (useMonths) {
            idx = buckets.findIndex(b => b.year === ed.getFullYear() && b.month === ed.getMonth());
        } else {
            const iso = `${ed.getFullYear()}-${String(ed.getMonth() + 1).padStart(2, '0')}-${String(ed.getDate()).padStart(2, '0')}`;
            idx = buckets.findIndex(b => b.iso === iso);
        }
        if (idx >= 0) {
            buckets[idx].value += val;
            if (typeMap) {
                const type = typeMap[entry.category] || 'Unassigned';
                if (!typeTotals[type]) typeTotals[type] = new Array(buckets.length).fill(0);
                typeTotals[type][idx] += val;
            }
        }
    });

    const grouped  = groupBuckets(buckets, useMonths, maxBars);
    const labels   = grouped.map(b => b.label);
    const values   = grouped.map(b => b.value);
    const fontMono = cssVar('--font-mono') || "'DM Mono', monospace";

    const stacked = typeMap !== null && Object.keys(typeTotals).length > 0;
    let datasets;

    if (stacked) {
        const groupSize = Math.max(1, Math.ceil(buckets.length / maxBars));
        const positionOf = Object.fromEntries(
            (expenseTypes || []).map(et => [et.value, et.position])
        );
        datasets = Object.entries(typeTotals)
            .filter(([, arr]) => arr.some(v => v > 0))
            .sort(([a], [b]) => (positionOf[a] ?? Infinity) - (positionOf[b] ?? Infinity))
            .map(([type, arr], i) => {
                const groupedArr = [];
                for (let j = 0; j < arr.length; j += groupSize) {
                    const slice = arr.slice(j, j + groupSize);
                    groupedArr.push(Math.round(slice.reduce((s, v) => s + v, 0) * 100) / 100);
                }
                return {
                    label: type,
                    data: groupedArr,
                    backgroundColor: palette[i % palette.length],
                    borderWidth: 0,
                    stack: 'spending',
                };
            });
    } else {
        datasets = [{
            label: 'Spending',
            data: values,
            backgroundColor: CHART.barBg,
            borderColor: CHART.barBorder,
            borderWidth: 1,
        }];
    }

    if (grouped.length > 5) {
        const avg = Math.round(values.reduce((s, v) => s + v, 0) / values.length * 100) / 100;
        datasets.push({
            type: 'line',
            label: `Avg €${avg.toFixed(2)}`,
            data: new Array(values.length).fill(avg),
            borderColor: CHART.avgLine,
            borderWidth: 2,
            borderDash: [6, 3],
            pointRadius: 0,
            fill: false,
            tension: 0,
        });
    }

    const legendContainer = document.getElementById(chartId + 'Legend');
    if (legendContainer) {
        legendContainer.innerHTML = '';
        if (stacked) {
            datasets
                .filter(d => d.stack === 'spending')
                .forEach(d => {
                    const item = document.createElement('div');
                    item.className = 'bar-legend-item';
                    item.innerHTML = `<span class="bar-legend-swatch" style="background:${d.backgroundColor}"></span><span>${d.label}</span>`;
                    legendContainer.appendChild(item);
                });
        }
    }

    const ctx = document.getElementById(chartId).getContext('2d');
    if (chartInstances[chartId]) chartInstances[chartId].destroy();
    chartInstances[chartId] = new Chart(ctx, {
        type: 'bar',
        data: { labels, datasets },
        options: {
            responsive: true,
            plugins: {
                legend: {
                    display: !stacked && grouped.length > 5,
                    labels: { font: { family: fontMono, size: 12 } },
                },
                tooltip: {
                    callbacks: {
                        label: ctx => stacked
                            ? ` ${ctx.dataset.label}: €${ctx.parsed.y.toFixed(2)}`
                            : ` €${ctx.parsed.y.toFixed(2)}`,
                    },
                    bodyFont: { family: fontMono },
                },
            },
            scales: {
                y: {
                    stacked,
                    beginAtZero: true,
                    ticks: { font: { family: fontMono, size: 11 }, callback: v => `€${v}` },
                },
                x: {
                    stacked,
                    ticks: { font: { family: fontMono, size: 11 }, maxRotation: 45 },
                },
            },
        },
    });
}

// ── Edit-mode UI ──────────────────────────────────────────────────────────────
function set_edit_mode(target) {
    document.querySelectorAll(`#${target}-expenditures-table tbody tr`).forEach(tr => {
        const cell = tr.querySelector("td.cat_field");
        cell.dataset.original = cell.textContent;
        cell.setAttribute('contenteditable', 'true');
        const name = tr.querySelector("td.name_field");
        if (name) { name.style.pointerEvents = 'none'; name.style.cursor = 'default'; }
    });
    document.getElementById(`${target}-edit-categories`).style.display = "none";
    document.getElementById(`${target}-mode`).style.display = "none";
    document.getElementById(`${target}-save-categories`).style.display = "inline-block";
    document.getElementById(`${target}-cancel-categories`).style.display = "inline-block";
}

function set_normal_mode(target) {
    document.querySelectorAll(`#${target}-expenditures-table tbody tr`).forEach(tr => {
        tr.querySelector("td.cat_field").setAttribute('contenteditable', 'false');
        const name = tr.querySelector("td.name_field");
        if (name) { name.style.pointerEvents = ''; name.style.cursor = ''; }
    });
    document.getElementById(`${target}-edit-categories`).style.display = "inline-block";
    document.getElementById(`${target}-mode`).style.display = "flex";
    document.getElementById(`${target}-save-categories`).style.display = "none";
    document.getElementById(`${target}-cancel-categories`).style.display = "none";
}

function editCategories(source)  { set_edit_mode(source.id.includes("rewe") ? "rewe" : "bank"); }
function cancelEditCategories(source) { set_normal_mode(source.id.includes("rewe") ? "rewe" : "bank"); }
async function saveCategories(source) {
    const target = source.id.includes("rewe") ? "rewe" : "bank";
    set_normal_mode(target);
    await update_db_from_table(target);
    await update_refresh();
}

async function update_db_from_table(target) {
    const tableId  = `${target}-expenditures-table`;
    const headers  = document.querySelectorAll(`#${tableId} thead th`);
    const displayed_headers = Array.from(headers).filter((x)=>!x.style.display || x.style.display != 'none')
    const nameCol  = Array.from(displayed_headers).findIndex(th => ["name", "recipient"].includes(th.dataset.col));
    const catCol   = Array.from(displayed_headers).findIndex(th => th.dataset.col === "category");
    const data     = [];

    document.querySelectorAll(`#${tableId} tbody tr`).forEach(r => {
        const cat = r.cells[catCol];
        if (cat && cat.textContent.trim() !== cat.dataset.original)
            data.push({ name: r.cells[nameCol].textContent.trim(), category: cat.textContent.trim() });
    });

    await fetch(`${API_BASE}/update_categories`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ target, data }),
    });
}

// ── Tab switching ─────────────────────────────────────────────────────────────
const sortState = {
    rewe: { col: 'date', dir: -1 },
    bank: { col: 'date', dir: -1 },
};

const COL_CONFIG = {
    date:      { field: 'date',         type: 'date'   },
    name:      { field: 'display_name', type: 'text'   },
    recipient: { field: 'display_name', type: 'text'   },
    source:    { field: 'source',       type: 'text'   },
    amount:    { field: 'amount',       type: 'number' },
    price:     { field: 'price',        type: 'number' },
    category:  { field: 'category',     type: 'text'   },
};

function sortData(data, target) {
    if (!data) return data;
    const { col, dir } = sortState[target];
    const { field, type } = COL_CONFIG[col] || { field: col, type: 'text' };
    return [...data].sort((a, b) => {
        const va = a[field], vb = b[field];
        if (type === 'number') return dir * ((parseFloat(va) || 0) - (parseFloat(vb) || 0));
        return dir * String(va ?? '').localeCompare(String(vb ?? ''));
    });
}

function updateSortIndicators(tableId, target) {
    const { col, dir } = sortState[target];
    document.querySelectorAll(`#${tableId} th[data-col]`).forEach(th => {
        const text = th.textContent.replace(/\s*[↑↓]$/, '').trim();
        th.textContent = th.dataset.col === col ? `${text} ${dir === 1 ? '↑' : '↓'}` : text;
    });
}

function activate(btn, tab) {
    btn.closest('.toggle-group').querySelectorAll('button').forEach(b => {
        b.classList.remove('active');
        b.setAttribute('aria-pressed', 'false');
    });
    btn.classList.add('active');
    btn.setAttribute('aria-pressed', 'true');
    refresh_tables();
    const tableId = tab === 'rewe' ? IDS.reweTable : IDS.bankTable;
    const headers = document.querySelectorAll(`#${tableId} thead th`);
    headers[0].style.display = btn.id === `${tab}_group_individual` ? "" : "none";
    if (tab === "bank") headers[1].style.display = btn.id === `${tab}_group_individual` ? "" : "none";
}

function showContent(name) {
    document.getElementsByName("Content").forEach(el => {
        el.style.display = el.id === `${name}Content` ? "block" : "none";
    });
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.getElementById(`${name}-tab`).classList.add('active');
    refresh_tables();
}

// ── Card height sync ──────────────────────────────────────────────────────────
function syncCardHeights(target) {
    const chartCard = document.getElementById(
        target === 'rewe' ? IDS.reweCatChart : IDS.bankCatChart
    ).closest('.card');
    const tableCard = document.getElementById(
        target === 'rewe' ? IDS.reweTable : IDS.bankTable
    ).closest('.card');
    if (!chartCard || !tableCard) return;
    tableCard.style.height = chartCard.offsetHeight + 'px';
}

// ── Popup ─────────────────────────────────────────────────────────────────────
function rename_popup(entry) {
    const original_names = entry.name || entry.recipient || entry.alt_names;

    let html = `<p style="font-weight:bold;">Original names:</p>`;
    if (typeof original_names === 'string') {
        html += `<p>${original_names}</p>`;
    } else {
        for (const name of original_names) html += `<p>${name}</p>`;
    }
    if (entry.purpose != undefined) {
        html += `<p>Purpose: ${entry.purpose}</p>`;
    }
    html += `<input type="text" id="new-name" placeholder="new name">
        <button id="save-rename-btn">Save</button>`;

    const content = document.getElementById("popup-content");
    content.innerHTML = html;
    document.getElementById("save-rename-btn").addEventListener("click", () => {
        submit_rename(entry, document.getElementById("new-name").value);
    });
    document.getElementById("overlay-box").style.display = "flex";
}

async function submit_rename(entry, new_name) {
    fetch(`${API_BASE}/update_display_names`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ entries: [entry], new_name }),
    }).then(async () => {
        close_popup();
        await update_refresh();
    }).catch(err => {
        console.error("Error renaming:", err);
        alert("An error occurred while renaming. Please try again.");
    });
}

function close_popup() {
    document.getElementById("overlay-box").style.display = "none";
    document.getElementById("popup-content").innerHTML = "";
    document.getElementById("overlay-popup").style.minWidth = "";
}

// ── Type manager popup ────────────────────────────────────────────────────────

async function openTypeManager() {
    const categoryTypes = await fetch(`${API_BASE}/get_category_types`).then(r => r.json());
    const expenseTypeValues = expenseTypes.map((f)=>f.value)
    const frag = document.getElementById("type-manager-tmpl").content.cloneNode(true);

    frag.querySelector("tbody").innerHTML = categoryTypes.map(ct => `
        <tr class="type-manager-row">
            <td>${ct.category}</td>
            <td>
                <select class="type-select" data-category="${ct.category}">
                    ${expenseTypeValues.map(t =>
                        `<option value="${t}"${t === ct.expense_type ? ' selected' : ''}>${t}</option>`
                    ).join('')}
                </select>
            </td>
        </tr>
    `).join('');

    const content = document.getElementById("popup-content");
    content.innerHTML = "";
    content.appendChild(frag);
    document.getElementById("overlay-popup").style.minWidth = "480px";
    document.getElementById("overlay-box").style.display = "flex";
}

async function saveTypeAssignments() {
    const data = Array.from(document.querySelectorAll("#type-manager-table .type-select"))
        .map(sel => ({ category: sel.dataset.category, type: sel.value }));

    await fetch(`${API_BASE}/update_category_types`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ data }),
    });
    close_popup();
}
