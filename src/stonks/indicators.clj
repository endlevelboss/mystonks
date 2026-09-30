(ns stonks.indicators
  "Momentum indicators. Pure functions over seqs of numbers or candle maps.")

(defn ema
  "Exponential moving average of window n, seeded with the SMA of the first n
  values. Returns (count xs) - n + 1 values, the first aligned with xs[n-1]."
  [n xs]
  (let [xs (map double xs)
        [seed-xs more] (split-at n xs)]
    (when (= n (count seed-xs))
      (let [alpha (/ 2.0 (inc n))]
        (reductions (fn [prev x] (+ prev (* alpha (- x prev))))
                    (/ (reduce + seed-xs) n)
                    more)))))

(defn rsi
  "Wilder's Relative Strength Index of window n over closing prices, 0-100.
  Returns (count closes) - n values, the first aligned with closes[n]."
  [n closes]
  (let [changes (map - (rest closes) closes)
        gains (map #(max % 0.0) changes)
        losses (map #(max (- %) 0.0) changes)
        smooth (fn [xs]
                 (let [[seed more] (split-at n xs)]
                   (when (= n (count seed))
                     (reductions (fn [avg x] (/ (+ (* avg (dec n)) x) n))
                                 (/ (reduce + seed) (double n))
                                 more))))
        ->rsi (fn [g l] (if (zero? l) 100.0 (- 100.0 (/ 100.0 (+ 1.0 (/ g l))))))]
    (map ->rsi (smooth gains) (smooth losses))))

(defn macd
  "MACD line (fast EMA - slow EMA), its signal EMA and the histogram
  (MACD - signal). :macd starts at xs[slow-1], :signal and :histogram at
  xs[slow+signal-2]."
  ([xs] (macd xs {}))
  ([xs {:keys [fast slow signal] :or {fast 12 slow 26 signal 9}}]
   (let [line (map - (drop (- slow fast) (ema fast xs)) (ema slow xs))
         sig (ema signal line)]
     {:macd line
      :signal sig
      :histogram (map - (drop (dec signal) line) sig)})))

(defn- dated
  "Pair values with the dates of candles, skipping the first `offset` candles."
  [candles offset values]
  (map (fn [c v] {:date (:date c) :value v}) (drop offset candles) values))

(defn rsi-state [value]
  (cond (nil? value) :unknown
        (>= value 70) :overbought
        (<= value 30) :oversold
        :else :neutral))

(defn last-cross
  "The latest date the histogram changed sign: MACD crossing its signal line
  upwards (:bullish) or downwards (:bearish)."
  [histogram]
  (->> (partition 2 1 histogram)
       (keep (fn [[a b]]
               (cond (and (neg? (:value a)) (not (neg? (:value b)))) {:date (:date b) :type :bullish}
                     (and (not (neg? (:value a))) (neg? (:value b))) {:date (:date b) :type :bearish})))
       last))

(defn indicators
  "RSI(14) and MACD(12, 26, 9) over the closes of candles, as [{:date :value}]
  series plus a summary of their latest state."
  [candles]
  (let [closes (map :close candles)
        n 14
        {:keys [fast slow signal] :as params} {:fast 12 :slow 26 :signal 9}
        m (macd closes params)
        rsi-series (dated candles n (rsi n closes))
        hist (dated candles (+ slow signal -2) (:histogram m))
        latest-rsi (:value (last rsi-series))
        latest-hist (:value (last hist))]
    {:rsi {:period n
           :series rsi-series
           :latest latest-rsi
           :state (rsi-state latest-rsi)}
     :macd {:params params
            :macd (dated candles (dec slow) (:macd m))
            :signal (dated candles (+ slow signal -2) (:signal m))
            :histogram hist
            :latest {:macd (last (:macd m)) :signal (last (:signal m)) :histogram latest-hist}
            :state (cond (nil? latest-hist) :unknown
                         (neg? latest-hist) :bearish
                         :else :bullish)
            :last-cross (last-cross hist)}}))
