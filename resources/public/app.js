// Renders candles, trend analysis and RSI/MACD from the Stonks API with
// Lightweight Charts (v4). v4 has no multi-pane support, so the indicators are
// separate charts whose time scales and crosshairs are kept in sync.
const LC = LightweightCharts;
const $ = (id) => document.getElementById(id);
const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

const COLORS = {
  up: '#26a69a', down: '#ef5350',
  sma20: '#f5a623', sma50: '#8e6cf0', regression: '#3b82f6',
  support: '#26a69a', resistance: '#ef5350',
  rsi: '#8e6cf0', macd: '#3b82f6', signal: '#f5a623',
};

const makeChart = (el, extra = {}) => LC.createChart(el, {
  autoSize: true,
  layout: { background: { color: css('--surface') }, textColor: css('--text') },
  grid: { vertLines: { color: css('--grid') }, horzLines: { color: css('--grid') } },
  // Same price-scale width on every chart so their time axes line up.
  rightPriceScale: { borderColor: css('--grid'), minimumWidth: 72 },
  timeScale: { borderColor: css('--grid') },
  crosshair: { mode: LC.CrosshairMode.Normal },
  ...extra,
});

const quiet = { priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false };

// --- Price chart --------------------------------------------------------------

const chart = makeChart($('chart'));

const candleSeries = chart.addCandlestickSeries({
  upColor: COLORS.up, downColor: COLORS.down, borderVisible: false,
  wickUpColor: COLORS.up, wickDownColor: COLORS.down,
});
candleSeries.priceScale().applyOptions({ scaleMargins: { top: 0.05, bottom: 0.25 } });

const volumeSeries = chart.addHistogramSeries({
  priceFormat: { type: 'volume' }, priceScaleId: 'volume',
  lastValueVisible: false, priceLineVisible: false,
});
volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

const line = (target, color, style = LC.LineStyle.Solid, width = 2) =>
  target.addLineSeries({ color, lineStyle: style, lineWidth: width, ...quiet });

const overlays = {
  sma20: line(chart, COLORS.sma20, LC.LineStyle.Solid, 1),
  sma50: line(chart, COLORS.sma50, LC.LineStyle.Solid, 1),
  regression: line(chart, COLORS.regression, LC.LineStyle.Solid, 2),
  support: line(chart, COLORS.support, LC.LineStyle.Dashed, 2),
  resistance: line(chart, COLORS.resistance, LC.LineStyle.Dashed, 2),
};

// --- RSI chart ----------------------------------------------------------------

const rsiChart = makeChart($('rsi-chart'), { timeScale: { visible: false } });
const rsiSeries = rsiChart.addLineSeries({
  color: COLORS.rsi, lineWidth: 2, priceLineVisible: false,
  autoscaleInfoProvider: () => ({ priceRange: { minValue: 0, maxValue: 100 } }),
});
rsiSeries.priceScale().applyOptions({ scaleMargins: { top: 0.05, bottom: 0.05 } });
for (const [price, color] of [[70, COLORS.down], [30, COLORS.up]]) {
  rsiSeries.createPriceLine({
    price, color, lineWidth: 1, lineStyle: LC.LineStyle.Dashed, axisLabelVisible: true,
  });
}

// --- MACD chart ---------------------------------------------------------------

const macdChart = makeChart($('macd-chart'));
const macdHist = macdChart.addHistogramSeries({ ...quiet });
const macdLine = macdChart.addLineSeries({ color: COLORS.macd, lineWidth: 2, priceLineVisible: false });
const signalLine = line(macdChart, COLORS.signal, LC.LineStyle.Solid, 2);

// --- Keeping the charts in step -------------------------------------------------

const charts = [chart, rsiChart, macdChart];

let syncingRange = false;
for (const src of charts) {
  src.timeScale().subscribeVisibleLogicalRangeChange((range) => {
    if (syncingRange || !range) return;
    syncingRange = true;
    for (const dst of charts) if (dst !== src) dst.timeScale().setVisibleLogicalRange(range);
    syncingRange = false;
  });
}

// For each chart, the series the crosshair snaps to and its value per date.
const crosshairTargets = new Map([
  [chart, { series: candleSeries, values: new Map() }],
  [rsiChart, { series: rsiSeries, values: new Map() }],
  [macdChart, { series: macdLine, values: new Map() }],
]);

const pad2 = (n) => String(n).padStart(2, '0');
const timeKey = (t) => (typeof t === 'string' ? t : `${t.year}-${pad2(t.month)}-${pad2(t.day)}`);

let syncingCrosshair = false;
for (const src of charts) {
  src.subscribeCrosshairMove((param) => {
    if (syncingCrosshair) return;
    syncingCrosshair = true;
    for (const dst of charts) {
      if (dst === src) continue;
      const { series, values } = crosshairTargets.get(dst);
      const value = param.time && values.get(timeKey(param.time));
      if (value != null) dst.setCrosshairPosition(value, param.time, series);
      else dst.clearCrosshairPosition();
    }
    syncingCrosshair = false;
  });
}

// --- Data -----------------------------------------------------------------------

const toPoints = (xs) => (xs || []).map((p) => ({ time: p.date, value: p.value }));

// Every chart gets a point for every candle date (whitespace where an indicator
// has no value yet), so their logical ranges refer to the same bars.
function padded(dates, points, toItem = (p) => ({ value: p.value })) {
  const byDate = new Map(points.map((p) => [p.date, p]));
  return dates.map((d) => (byDate.has(d) ? { time: d, ...toItem(byDate.get(d)) } : { time: d }));
}

const valueMap = (points) => new Map(points.map((p) => [p.date, p.value]));

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  return res.json();
}

// --- Summary --------------------------------------------------------------------

const ARROWS = { up: '▲', down: '▼', sideways: '▶', unknown: '?' };
const fmtPct = (x) => `${x >= 0 ? '+' : ''}${x.toFixed(3)}%/day`;

function badge(dir) {
  return `<span class="badge ${dir}">${ARROWS[dir] || ''} ${dir.toUpperCase()}</span>`;
}

// Indicator states shown in the trend colours: good for longs green, bad red.
const STATE_CLASS = {
  oversold: 'up', overbought: 'down', neutral: 'sideways',
  bullish: 'up', bearish: 'down', unknown: 'unknown',
};
const stateBadge = (state) => `<span class="badge ${STATE_CLASS[state]}">${state.toUpperCase()}</span>`;

function lineSummary(label, l) {
  if (!l) return `<div><dt>${label}</dt><dd>not enough pivots</dd></div>`;
  return `<div><dt>${label}</dt><dd>${badge(l.direction)} ${fmtPct(l.pctPerDay)}</dd></div>`;
}

function indicatorSummary({ rsi, macd }) {
  const cross = macd.lastCross
    ? `, crossed ${macd.lastCross.type === 'bullish' ? 'above' : 'below'} signal ${macd.lastCross.date}`
    : '';
  return `
    <div><dt>RSI ${rsi.period}</dt><dd>${rsi.latest == null ? '–' : rsi.latest.toFixed(1)} ${stateBadge(rsi.state)}</dd></div>
    <div><dt>MACD</dt><dd>${stateBadge(macd.state)}${cross}</dd></div>`;
}

function renderSummary(info, a, ind, candles) {
  const last = candles[candles.length - 1];
  const r = a.regression;
  $('summary').innerHTML = `
    <div class="headline">
      <span class="sym">${info.symbol}</span>
      <span class="name">${info.name}</span>
      <span class="price">${last.close.toFixed(2)} ${info.currency || ''}</span>
      <span class="trend">Trend ${badge(a.trend)}</span>
    </div>
    <dl>
      <div><dt>Period</dt><dd>${a.from} → ${a.to} (${a.window} days)</dd></div>
      <div><dt>Regression</dt><dd>${fmtPct(r.pctPerDay)}, r² ${r.r2.toFixed(2)}</dd></div>
      <div><dt>SMA 50 signal</dt><dd>${badge(a.smaSignal)}</dd></div>
      ${lineSummary('Support', a.support)}
      ${lineSummary('Resistance', a.resistance)}
      ${indicatorSummary(ind)}
    </dl>`;
}

function renderSource(info) {
  const real = info.source === 'yahoo';
  $('refresh').hidden = !real;
  $('source').textContent = real
    ? `Source: Yahoo Finance · ${info.count} trading days, ${info.firstDate} → ${info.lastDate}` +
      ` · downloaded ${new Date(info.fetchedAt).toLocaleString()}`
    : 'Source: generated test data';
}

// --- Loading --------------------------------------------------------------------

async function load() {
  const symbol = $('symbol').value;
  const windowDays = Number($('window').value) || null;
  const q = windowDays ? `?window=${windowDays}` : '';
  const [info, candles, a, ind] = await Promise.all([
    getJson(`/api/stocks/${symbol}/info`),
    getJson(`/api/stocks/${symbol}/candles`),
    getJson(`/api/stocks/${symbol}/analysis${q}`),
    getJson(`/api/stocks/${symbol}/indicators`),
  ]);
  const dates = candles.map((c) => c.date);

  candleSeries.setData(candles.map((c) => ({
    time: c.date, open: c.open, high: c.high, low: c.low, close: c.close,
  })));
  volumeSeries.setData(candles.map((c) => ({
    time: c.date, value: c.volume,
    color: (c.close >= c.open ? COLORS.up : COLORS.down) + '55',
  })));

  overlays.sma20.setData(toPoints(a.sma20));
  overlays.sma50.setData(toPoints(a.sma50));
  overlays.regression.setData(toPoints(a.regression.line));
  overlays.support.setData(toPoints(a.support && a.support.line));
  overlays.resistance.setData(toPoints(a.resistance && a.resistance.line));

  candleSeries.setMarkers(a.pivots.map((p) => ({
    time: p.date,
    position: p.type === 'high' ? 'aboveBar' : 'belowBar',
    color: p.type === 'high' ? COLORS.resistance : COLORS.support,
    shape: p.type === 'high' ? 'arrowDown' : 'arrowUp',
    size: 0.6,
  })));

  rsiSeries.setData(padded(dates, ind.rsi.series));
  macdLine.setData(padded(dates, ind.macd.macd));
  signalLine.setData(padded(dates, ind.macd.signal));
  macdHist.setData(padded(dates, ind.macd.histogram, (p) => ({
    value: p.value,
    color: (p.value >= 0 ? COLORS.up : COLORS.down) + '99',
  })));

  crosshairTargets.get(chart).values = new Map(candles.map((c) => [c.date, c.close]));
  crosshairTargets.get(rsiChart).values = valueMap(ind.rsi.series);
  crosshairTargets.get(macdChart).values = valueMap(ind.macd.macd);

  renderSummary(info, a, ind, candles);
  renderSource(info);
  // Wait a frame so autoSize has measured the containers before zooming;
  // the range sync carries the zoom over to the indicator charts.
  requestAnimationFrame(() => {
    const n = candles.length;
    if (windowDays && windowDays < n) {
      // Show the analysed window plus some context before it.
      chart.timeScale().setVisibleLogicalRange({ from: n - windowDays * 1.3, to: n + 2 });
    } else {
      chart.timeScale().fitContent();
    }
  });
}

function showError(e) {
  $('summary').textContent = `Failed: ${e.message}`;
}

async function refresh() {
  const button = $('refresh');
  button.disabled = true;
  button.textContent = 'Updating…';
  try {
    const res = await fetch(`/api/stocks/${$('symbol').value}/refresh`, { method: 'POST' });
    if (!res.ok) throw new Error((await res.json()).error || res.status);
    await load();
  } catch (e) {
    showError(e);
  } finally {
    button.disabled = false;
    button.textContent = 'Update data';
  }
}

$('symbol').addEventListener('change', () => load().catch(showError));
$('window').addEventListener('change', () => load().catch(showError));
$('refresh').addEventListener('click', refresh);
load().catch(showError);
