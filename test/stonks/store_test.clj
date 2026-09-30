(ns stonks.store-test
  (:require [clojure.test :refer [deftest is]]
            [stonks.store :as store])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest merge-candles-test
  (is (= [{:date "2025-01-01" :close 1}
          {:date "2025-01-02" :close 22}
          {:date "2025-01-03" :close 3}]
         (store/merge-candles [{:date "2025-01-01" :close 1} {:date "2025-01-02" :close 2}]
                              [{:date "2025-01-03" :close 3} {:date "2025-01-02" :close 22}]))))

(deftest round-trip-test
  (let [dir (str (Files/createTempDirectory "stonks" (make-array FileAttribute 0)))
        series {:symbol "TEST.OL" :name "Test ASA" :currency "NOK" :source :yahoo
                :candles (vec (for [d (range 1 4)]
                                {:date (format "2025-01-0%d" d) :open 1.5 :high 2.0
                                 :low 1.0 :close 1.75 :volume 100 :adj-close 1.7}))}]
    (binding [store/*data-dir* dir]
      (is (nil? (store/read-series "TEST.OL")))
      (store/write-series! "TEST.OL" series)
      (is (= series (store/read-series "TEST.OL"))))))
