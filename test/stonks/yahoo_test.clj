(ns stonks.yahoo-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as j]
            [stonks.yahoo :as yahoo]))

(def ^:private response
  ;; Trimmed real response for EQNR.OL; the third day has missing prices.
  (j/read-value
   "{\"chart\":{\"result\":[{\"meta\":{\"currency\":\"NOK\",\"symbol\":\"EQNR.OL\",
      \"fullExchangeName\":\"Oslo\",\"exchangeTimezoneName\":\"Europe/Oslo\",
      \"longName\":\"Equinor ASA\",\"priceHint\":2},
     \"timestamp\":[1790233200,1790319600,1790578800],
     \"indicators\":{\"quote\":[{\"open\":[408.1000061035156,410.3999938964844,null],
                              \"high\":[419.29998779296875,411.8999938964844,null],
                              \"low\":[406.70001220703125,400.70001220703125,null],
                              \"close\":[416.29998779296875,404.70001220703125,null],
                              \"volume\":[2142514,2088062,null]}],
                   \"adjclose\":[{\"adjclose\":[416.29998779296875,404.70001220703125,null]}]}}],
     \"error\":null}}"))

(deftest parse-chart-test
  (let [{:keys [meta candles]} (yahoo/parse-chart response)]
    (is (= {:symbol "EQNR.OL" :name "Equinor ASA" :currency "NOK" :exchange "Oslo"} meta))
    (testing "days without prices are dropped"
      (is (= 2 (count candles))))
    (testing "timestamps become exchange-local dates, prices are rounded"
      (is (= {:date "2026-09-24" :open 408.1 :high 419.3 :low 406.7 :close 416.3
              :volume 2142514 :adj-close 416.3}
             (first candles))))))

(deftest parse-chart-error-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No data found"
                        (yahoo/parse-chart
                         {"chart" {"result" nil
                                   "error" {"code" "Not Found"
                                            "description" "No data found"}}}))))
