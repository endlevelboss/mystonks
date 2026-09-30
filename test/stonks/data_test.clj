(ns stonks.data-test
  (:require [clojure.test :refer [deftest is testing]]
            [stonks.data :as data])
  (:import (java.time DayOfWeek LocalDate)))

(deftest generate-series-test
  (let [candles (data/generate-series {:days 120 :seed 1})]
    (testing "length"
      (is (= 120 (count candles))))
    (testing "no weekends, strictly increasing dates"
      (is (not-any? #(#{DayOfWeek/SATURDAY DayOfWeek/SUNDAY}
                      (.getDayOfWeek (LocalDate/parse (:date %))))
                    candles))
      (is (apply distinct? (map :date candles)))
      (is (= (map :date candles) (sort (map :date candles)))))
    (testing "OHLC invariants"
      (doseq [{:keys [open high low close volume]} candles]
        (is (<= low (min open close)))
        (is (>= high (max open close)))
        (is (pos? low))
        (is (pos? volume))))
    (testing "reproducible for the same seed"
      (is (= candles (data/generate-series {:days 120 :seed 1})))
      (is (not= candles (data/generate-series {:days 120 :seed 2}))))))

(deftest presets-test
  (doseq [sym (keys data/symbols)]
    (is (= 250 (count (data/series sym))) sym))
  (is (nil? (data/series "NOPE"))))
