(ns stonks.trend-test
  (:require [clojure.test :refer [deftest is testing]]
            [stonks.data :as data]
            [stonks.trend :as trend]))

(defn- ->candles
  "Candles from a seq of closes, with high/low 1 above/below."
  [closes]
  (vec (map-indexed (fn [i c] {:date (format "2025-01-%02d" (inc i))
                               :open c :high (+ c 1) :low (- c 1) :close c})
                    closes)))

(defn- approx= [a b] (< (Math/abs (- (double a) (double b))) 1e-9))

(deftest linear-regression-test
  (let [{:keys [slope intercept r2]} (trend/linear-regression [1 3 5 7 9])]
    (is (approx= 2.0 slope))
    (is (approx= 1.0 intercept))
    (is (approx= 1.0 r2))))

(deftest regression-trend-test
  (is (= :up (:direction (trend/regression-trend (->candles (range 100 130))))))
  (is (= :down (:direction (trend/regression-trend (->candles (range 130 100 -1))))))
  (testing "zig-zag around a flat mean is sideways"
    (is (= :sideways (:direction (trend/regression-trend
                                  (->candles (take 30 (cycle [100 102 98 101 99])))))))))

(deftest sma-test
  (is (= [2.0 3.0 4.0] (trend/sma 3 [1 2 3 4 5])))
  (is (= [{:date "2025-01-03" :value 2.0}]
         (trend/sma-series 3 (->candles [1 2 3])))))

(deftest pivots-test
  (let [closes [10 11 12 15 12 11 10 8 10 11 12]
        ps (trend/pivots (->candles closes) 2)]
    (is (= [[3 :high] [7 :low]] (map (juxt :index :type) ps)))))

(deftest trendlines-test
  (testing "rising lows and highs give up-sloping support and resistance"
    (let [closes (for [i (range 60)]
                   (+ 100 (* 0.5 i) (* 5 (Math/sin (/ i 3.0)))))
          {:keys [support resistance]} (trend/trendlines (->candles closes) 3)]
      (is (= :up (:direction support)))
      (is (= :up (:direction resistance))))))

(deftest presets-classified-test
  (is (= :up (:trend (trend/analyze (data/series "UPCO")))))
  (is (= :down (:trend (trend/analyze (data/series "DWNR")))))
  (is (= :sideways (:trend (trend/analyze (data/series "FLAT"))))))
