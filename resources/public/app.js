// Renders candles + trend analysis from the Stonks API with Lightweight Charts (v4).
const LC = LightweightCharts;
const $ = (id) => document.getElementById(id);
const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

const COLORS = {
  up: '#26a69a', down: '#ef5350',
  sma20: '#f5a623', sma50: '#8e6cf0', regression: '#3b82f6',
  support: '#26a69a', resistance: '#ef5350',
};

const chart = LC.createChart($('chart'), {
  autoSize: true,
  layout: { background: { color: css('--surface') }, textColor: css('--text') },
  grid: { vertLines: { color: css('--grid') }, horzLines: { color: css('--grid') } },
  rightPriceScale: { borderColor: css('--grid') },
  timeScale: { borderColor: css('--grid') },
  crosshair: { mode: LC.CrosshairMode.Normal },
});

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

const line = (color, style = LC.LineStyle.Solid, width = 2) => chart.addLineSeries({
  color, lineStyle: style, lineWidth: width,
  priceLineVisible: false, lastValueVisible: false, crosshairMarkerVisible: false,
});

const overlays = {
  sma20: line(COLORS.sma20, LC.LineStyle.Solid, 1),
  sma50: line(COLORS.sma50, LC.LineStyle.Solid, 1),
  regression: line(COLORS.regression, LC.LineStyle.Solid, 2),
  support: line(COLORS.support, LC.LineStyle.Dashed, 2),
  resistance: line(COLORS.resistance, LC.LineStyle.Dashed, 2),
};

const toPoints = (xs) => (xs || []).map((p) => ({ time: p.date, value: p.value }));

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  return res.json();
}

const ARROWS = { up: '▲', down: '▼', sideways: '▶', unknown: '?' };
const fmtPct = (x) => `${x >= 0 ? '+' : ''}${x.toFixed(3)}%/day`;

function badge(dir) {
  return `<span class="badge ${dir}">${ARROWS[dir] || ''} ${dir.toUpperCase()}</span>`;
}

function lineSummary(label, l) {
  if (!l) return `<div><dt>${label}</dt><dd>not enough pivots</dd></div>`;
  return `<div><dt>${label}</dt><dd>${badge(l.direction)} ${fmtPct(l.pctPerDay)}</dd></div>`;
}

function renderSummary(symbol, a, candles) {
  const last = candles[candles.length - 1];
  const r = a.regression;
  $('summary').innerHTML = `
    <div class="headline">
      <span class="sym">${symbol}</span>
      <span class="price">${last.close.toFixed(2)}</span>
      <span class="trend">Trend ${badge(a.trend)}</span>
    </div>
    <dl>
      <div><dt>Period</dt><dd>${a.from} → ${a.to} (${a.window} days)</dd></div>
      <div><dt>Regression</dt><dd>${fmtPct(r.pctPerDay)}, r² ${r.r2.toFixed(2)}</dd></div>
      <div><dt>SMA 50 signal</dt><dd>${badge(a.smaSignal)}</dd></div>
      ${lineSummary('Support', a.support)}
      ${lineSummary('Resistance', a.resistance)}
    </dl>`;
}

async function load() {
  const symbol = $('symbol').value;
  const windowDays = $('window').value;
  const q = windowDays ? `?window=${windowDays}` : '';
  const [candles, a] = await Promise.all([
    getJson(`/api/stocks/${symbol}/candles`),
    getJson(`/api/stocks/${symbol}/analysis${q}`),
  ]);

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

  renderSummary(symbol, a, candles);
  // Wait a frame so autoSize has measured the container before fitting.
  requestAnimationFrame(() => chart.timeScale().fitContent());
}

$('symbol').addEventListener('change', load);
$('window').addEventListener('change', load);
load().catch((e) => { $('summary').textContent = `Failed to load: ${e.message}`; });
