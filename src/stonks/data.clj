(ns stonks.data
  "Generates reproducible dummy daily OHLCV data for testing analysis code."
  (:import (java.time DayOfWeek LocalDate)
           (java.util Random)))

(defn trading-days
  "The first n weekdays starting at (and including, if a weekday) start-date."
  [start-date n]
  (->> (iterate #(.plusDays ^LocalDate % 1) (LocalDate/parse start-date))
       (remove #(#{DayOfWeek/SATURDAY DayOfWeek/SUNDAY} (.getDayOfWeek ^LocalDate %)))
       (take n)))

(defn- round2 [x]
  (/ (Math/round (* 100.0 (double x))) 100.0))

(defn- drift-seq
  "Expand regimes [[days drift] ...] into an infinite per-day drift seq.
  After the last regime its drift continues."
  [regimes]
  (concat (mapcat (fn [[days drift]] (repeat days drift)) regimes)
          (repeat (second (last regimes)))))

(def defaults
  {:start-date "2025-01-02"
   :days 250
   :start-price 100.0
   :seed 42
   :volatility 0.015
   :base-volume 1000000
   :regimes [[250 0.0]]})

(defn generate-series
  "Generate a vector of daily candles {:date :open :high :low :close :volume}
  from a seeded random walk whose drift changes according to :regimes."
  [opts]
  (let [{:keys [start-date days start-price seed volatility base-volume regimes]}
        (merge defaults opts)
        rng (Random. (long seed))
        gauss #(.nextGaussian rng)]
    (loop [dates (trading-days start-date days)
           drifts (drift-seq regimes)
           prev (double start-price)
           acc (transient [])]
      (if-let [date (first dates)]
        (let [open (round2 (* prev (+ 1.0 (* 0.3 volatility (gauss)))))
              ret (+ (first drifts) (* volatility (gauss)))
              close (round2 (* open (Math/exp ret)))
              high (round2 (* (max open close)
                              (+ 1.0 (* 0.6 volatility (Math/abs (double (gauss)))))))
              low (round2 (* (min open close)
                             (- 1.0 (* 0.6 volatility (Math/abs (double (gauss)))))))
              move (Math/abs (/ (- close prev) prev))
              volume (long (* base-volume
                              (+ 0.6 (* 0.4 (Math/abs (double (gauss)))))
                              (+ 1.0 (* 20.0 move))))]
          (recur (rest dates)
                 (rest drifts)
                 close
                 (conj! acc {:date (str date)
                             :open open :high high :low low :close close
                             :volume volume})))
        (persistent! acc)))))

(def symbols
  "Preset dummy stocks. Regimes are [trading-days daily-drift] segments."
  {"DUMMY" {:name "Dummy Corp (mixed regimes)"
            :seed 42
            :regimes [[60 0.003] [40 0.0] [70 -0.004] [80 0.0025]]}
   "UPCO"  {:name "Up & Co (bullish)"
            :seed 7
            :start-price 50.0
            :regimes [[250 0.002]]}
   "DWNR"  {:name "Downer Inc (bearish)"
            :seed 13
            :start-price 150.0
            :regimes [[250 -0.002]]}
   "FLAT"  {:name "Flatline Ltd (sideways)"
            :seed 99
            :start-price 80.0
            :volatility 0.01
            :regimes [[250 0.0]]}})

(def series
  "Candles for a preset symbol, or nil if unknown. Cached."
  (memoize
   (fn [symbol]
     (when-let [cfg (get symbols symbol)]
       (generate-series cfg)))))
