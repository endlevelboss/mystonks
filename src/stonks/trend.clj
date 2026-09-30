(ns stonks.trend
  "Trend identification. Pure functions over vectors of candle maps.")

(defn linear-regression
  "Least-squares fit of ys against their indices 0..n-1.
  Returns {:slope :intercept :r2}."
  [ys]
  (let [ys (mapv double ys)
        n (count ys)]
    (if (< n 2)
      {:slope 0.0 :intercept (or (first ys) 0.0) :r2 0.0}
      (let [mx (/ (dec n) 2.0)
            my (/ (reduce + ys) n)
            xs (range n)
            sxy (reduce + (map (fn [x y] (* (- x mx) (- y my))) xs ys))
            sxx (reduce + (map (fn [x] (let [d (- x mx)] (* d d))) xs))
            slope (/ sxy sxx)
            intercept (- my (* slope mx))
            ss-tot (reduce + (map (fn [y] (let [d (- y my)] (* d d))) ys))
            ss-res (reduce + (map (fn [x y]
                                    (let [d (- y (+ intercept (* slope x)))] (* d d)))
                                  xs ys))]
        {:slope slope
         :intercept intercept
         :r2 (if (zero? ss-tot) 1.0 (- 1.0 (/ ss-res ss-tot)))}))))

(def ^:dynamic *min-pct-per-day*
  "Slopes flatter than this (in % of mean price per day) count as sideways."
  0.05)

(def ^:dynamic *min-r2*
  "Fits weaker than this count as sideways: the line doesn't explain the price."
  0.3)

(defn- direction [pct-per-day]
  (cond (>= pct-per-day *min-pct-per-day*) :up
        (<= pct-per-day (- *min-pct-per-day*)) :down
        :else :sideways))

(defn regression-trend
  "Fit a line through closing prices and classify it as :up, :down or :sideways."
  [candles]
  (let [closes (mapv :close candles)
        n (count closes)
        {:keys [slope intercept r2]} (linear-regression closes)
        mean (/ (reduce + closes) (max n 1))
        pct (if (zero? mean) 0.0 (* 100.0 (/ slope mean)))]
    {:direction (if (< r2 *min-r2*) :sideways (direction pct))
     :slope slope
     :pct-per-day pct
     :r2 r2
     :line [{:date (:date (first candles)) :value intercept}
            {:date (:date (peek candles)) :value (+ intercept (* slope (dec n)))}]}))

(defn sma
  "Simple moving average of window n. Returns (count xs) - n + 1 values."
  [n xs]
  (map #(/ (reduce + %) (double n)) (partition n 1 xs)))

(defn sma-series
  "SMA of closes as [{:date :value}] aligned to the candle dates."
  [n candles]
  (map (fn [c v] {:date (:date c) :value v})
       (drop (dec n) candles)
       (sma n (map :close candles))))

(defn sma-signal
  "Secondary trend signal: last close vs a rising/falling SMA.
  :up if price is above an SMA that has risen over the last `lookback` bars."
  [candles n lookback]
  (let [s (vec (sma n (map :close candles)))]
    (if (<= (count s) lookback)
      :unknown
      (let [now (peek s)
            before (nth s (- (count s) 1 lookback))
            close (:close (peek candles))]
        (cond (and (> close now) (> now before)) :up
              (and (< close now) (< now before)) :down
              :else :sideways)))))

(defn pivots
  "Swing highs/lows: bars whose high (low) is the extreme of the ±k bars around
  them. Returns [{:index :date :type (:high|:low) :price}] in date order."
  [candles k]
  (let [v (vec candles)
        n (count v)]
    (vec
     (for [i (range k (- n k))
           :let [window (subvec v (- i k) (+ i k 1))
                 c (v i)]
           [type price] [[:high (:high c)] [:low (:low c)]]
           :when (if (= type :high)
                   (= price (apply max (map :high window)))
                   (= price (apply min (map :low window))))]
       {:index i :date (:date c) :type type :price price}))))

(defn- line-through
  "Line through two pivots, extended to the last candle."
  [candles p1 p2]
  (let [last-i (dec (count candles))
        slope (/ (- (:price p2) (:price p1)) (double (- (:index p2) (:index p1))))
        end (+ (:price p1) (* slope (- last-i (:index p1))))
        pct (* 100.0 (/ slope (:price p1)))]
    {:direction (direction pct)
     :pct-per-day pct
     :points [p1 p2]
     :line [{:date (:date p1) :value (:price p1)}
            {:date (:date (nth candles last-i)) :value end}]}))

(defn trendlines
  "Support (through the two latest pivot lows) and resistance (through the two
  latest pivot highs). Either is nil when there aren't two pivots of that kind."
  ([candles] (trendlines candles 5))
  ([candles k]
   (let [ps (pivots candles k)
         last-two (fn [t] (take-last 2 (filter #(= t (:type %)) ps)))
         mk (fn [[a b]] (when b (line-through (vec candles) a b)))]
     {:support (mk (last-two :low))
      :resistance (mk (last-two :high))
      :pivots ps})))

(defn analyze
  "Full trend analysis. With :window, regression and trendlines only use the
  last `window` candles, while SMAs use the whole series."
  ([candles] (analyze candles {}))
  ([candles {:keys [window pivot-k] :or {pivot-k 5}}]
   (let [candles (vec candles)
         recent (if (and window (< window (count candles)))
                  (subvec candles (- (count candles) window))
                  candles)
         reg (regression-trend recent)
         {:keys [support resistance pivots]} (trendlines recent pivot-k)]
     {:trend (:direction reg)
      :window (count recent)
      :from (:date (first recent))
      :to (:date (peek recent))
      :regression reg
      :support support
      :resistance resistance
      :pivots pivots
      :sma-signal (sma-signal candles 50 10)
      :sma20 (sma-series 20 candles)
      :sma50 (sma-series 50 candles)})))
