(ns stonks.store
  "Local cache of downloaded daily candles: one EDN file per ticker in data/."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [stonks.yahoo :as yahoo])
  (:import (java.time Instant LocalDate)))

(def ^:dynamic *data-dir* "data")

(def ^:private overlap-days
  "Days re-downloaded before the last stored date, so a candle saved while the
  market was open gets replaced by the final one."
  7)

(defn file-for [ticker]
  (io/file *data-dir* (str ticker ".edn")))

(defn read-series
  "The stored series {:symbol :name ... :candles [...]} for ticker, or nil."
  [ticker]
  (let [f (file-for ticker)]
    (when (.exists f)
      (edn/read-string (slurp f)))))

(defn write-series!
  "Save series as EDN with one candle per line, so the file stays readable and
  diffs are small."
  [ticker series]
  (let [f (file-for ticker)]
    (io/make-parents f)
    (with-open [w (io/writer f)]
      (binding [*out* w *print-length* nil *print-level* nil]
        (println "{")
        (doseq [[k v] (dissoc series :candles)]
          (println (pr-str k) (pr-str v)))
        (println ":candles")
        (println "[")
        (doseq [c (:candles series)]
          (println (pr-str c)))
        (println "]}")))))

(defn merge-candles
  "Combine candle seqs by date, later ones winning, sorted by date."
  [old new]
  (vec (vals (into (sorted-map) (map (juxt :date identity)) (concat old new)))))

(defn update!
  "Download what's new for ticker (the full history the first time), merge it
  into the stored file and return the updated series."
  [ticker]
  (let [old (read-series ticker)
        from (some-> old :candles peek :date LocalDate/parse
                     (.minusDays overlap-days) str)
        {:keys [meta candles]} (yahoo/fetch-daily ticker (if from {:from from} {}))
        series (merge old meta {:source :yahoo
                                :fetched-at (str (Instant/now))
                                :candles (merge-candles (:candles old) candles)})]
    (write-series! ticker series)
    series))

(defn -main
  "Update the stored data for each ticker given: clojure -M:fetch EQNR.OL"
  [& tickers]
  (doseq [t tickers]
    (let [{:keys [candles]} (update! t)]
      (println (format "%s: %d days, %s to %s" t (count candles)
                       (:date (first candles)) (:date (peek candles))))))
  (shutdown-agents))
