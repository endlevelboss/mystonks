# Stonks

Technical analysis playground in Clojure. Currently: generated dummy stocks,
a candlestick chart in the browser, and trend detection.

## Run

```bash
clojure -M:run
```

Open http://localhost:8080 (set `PORT` to change it).

From a REPL (`clojure -M:dev`): `(require 'stonks.core) (stonks.core/start!)`.

## Test

```bash
clojure -M:test
```

## Layout

| File | Purpose |
|------|---------|
| `src/stonks/data.clj` | Seeded dummy OHLCV generator; preset symbols `DUMMY`, `UPCO`, `DWNR`, `FLAT` |
| `src/stonks/trend.clj` | Linear regression trend, SMAs, swing pivots, support/resistance lines, `analyze` |
| `src/stonks/server.clj` | Routes: `/`, `/api/symbols`, `/api/stocks/:symbol/candles`, `/api/stocks/:symbol/analysis?window=N` |
| `src/stonks/views.clj` | HTML page (hiccup) |
| `resources/public/app.js` | Chart rendering with TradingView Lightweight Charts (v4, via CDN) |

## How the trend is decided

- **Trend**: least-squares line through closes. `up`/`down` if the slope is at least
  0.05% of mean price per day *and* r² ≥ 0.3; otherwise `sideways`.
- **SMA 50 signal**: `up` if the close is above a rising SMA 50 (compared with 10 bars ago), `down` for the opposite.
- **Support / resistance**: lines through the two most recent swing lows / highs
  (a bar whose low/high is the extreme within ±5 bars), extended to today.
