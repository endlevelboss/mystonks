(ns stonks.indicators-test
  (:require [clojure.test :refer [deftest is testing]]
            [stonks.data :as data]
            [stonks.indicators :as ind]))

(defn- approx= [xs ys]
  (and (= (count xs) (count ys))
       (every? #(< (Math/abs (double %)) 1e-9) (map - xs ys))))

(deftest ema-test
  (testing "seeded with the SMA, then alpha = 2/(n+1)"
    (is (approx= [2.0 3.0 4.0] (ind/ema 3 [1 2 3 4 5]))))
  (is (approx= [7.0 7.0 7.0] (ind/ema 2 [7 7 7 7])))
  (is (nil? (ind/ema 5 [1 2 3]))))

(deftest rsi-test
  (testing "hand-checked Wilder smoothing"
    ;; changes +1 -1 +1: first avg gain/loss 0.5/0.5 -> 50,
    ;; then gain (0.5+1)/2 = 0.75, loss 0.25 -> RS 3 -> 75
    (is (approx= [50.0 75.0] (ind/rsi 2 [1 2 1 2]))))
  (is (approx= [100.0 100.0] (ind/rsi 3 [1 2 3 4 5])))
  (is (approx= [0.0 0.0] (ind/rsi 3 [5 4 3 2 1])))
  (testing "bounded 0-100 on real-ish data"
    (is (every? #(<= 0 % 100) (ind/rsi 14 (map :close (data/series "DUMMY")))))))

(deftest macd-test
  (let [xs (range 1 21)
        {:keys [macd signal histogram]} (ind/macd xs {:fast 3 :slow 5 :signal 2})]
    (testing "lengths match the documented alignment"
      (is (= (- 20 4) (count macd)))
      (is (= (- 20 5) (count signal) (count histogram))))
    (testing "a steady rise converges to a constant positive gap between EMAs"
      (is (approx= [1.0] [(last macd)]))))
  (testing "a flat series has zero MACD"
    (is (every? zero? (:macd (ind/macd (repeat 40 10.0)))))))

(deftest last-cross-test
  (is (= {:date "d3" :type :bullish}
         (ind/last-cross [{:date "d1" :value 1} {:date "d2" :value -1} {:date "d3" :value 0.5}])))
  (is (= {:date "d2" :type :bearish}
         (ind/last-cross [{:date "d1" :value 1} {:date "d2" :value -1} {:date "d3" :value -2}])))
  (is (nil? (ind/last-cross [{:date "d1" :value 1} {:date "d2" :value 2}]))))

(deftest indicators-test
  (let [candles (data/series "DUMMY")
        {:keys [rsi macd]} (ind/indicators candles)]
    (testing "series are dated and end on the last candle"
      (is (= (:date (peek candles)) (:date (last (:series rsi))) (:date (last (:histogram macd))))))
    (is (= (- (count candles) 14) (count (:series rsi))))
    (is (#{:overbought :oversold :neutral} (:state rsi)))
    (is (#{:bullish :bearish} (:state macd)))))
