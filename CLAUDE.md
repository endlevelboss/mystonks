# Stonks

Clojure playground for technical analysis of stocks, aimed at finding trading
strategies. The user can only trade on **Oslo Børs**, so real data should be
Oslo tickers. See README.md for layout and API routes.

## Commands

- `clojure -M:run` — web server on http://localhost:8080 (`PORT` env var overrides)
- `clojure -M:test` — all tests, via the small runner in `test/stonks/test_runner.clj`
  (any `test/**/*_test.clj` is picked up automatically)
- `clojure -M:fetch EQNR.OL` — download/update a ticker into `data/`

## Conventions

- Analysis code (`trend.clj` etc.) is pure functions over a vector of candle maps
  `{:date "yyyy-MM-dd" :open :high :low :close :volume}` (+ `:adj-close` for real data).
  Keep it that way so strategies/backtesting can reuse it.
- Real data: Yahoo Finance chart API (unofficial, no key), Oslo tickers use the
  `.OL` suffix. Stored as EDN in `data/<TICKER>.edn`, one candle per line; updates
  re-download the last 7 days and merge. Add tickers in `real-symbols` in
  `src/stonks/market.clj`.
- `:close` is the traded price (use for charts/TA); `:adj-close` is dividend/split
  adjusted (use for return calculations — Equinor pays large dividends).
- Front end is plain JS + TradingView Lightweight Charts **v4** API from unpkg
  (no ClojureScript build). JSON keys are camelCased by the server.
- Tests must not hit the network; use fixtures like `test/stonks/yahoo_test.clj`.

## Known data quirks

- EQNR.OL history from Yahoo starts 2000-01-03, but Statoil was listed June 2001;
  treat data before mid-2001 as unreliable.
- The latest candle can be intraday/partial when fetched during trading hours.

## Ideas not done yet

More indicators (RSI, MACD, Bollinger), pattern detection, strategy definitions
and a backtester with P&L stats, more Oslo tickers.
