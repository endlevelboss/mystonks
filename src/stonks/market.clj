(ns stonks.market
  "Which symbols exist and where their candles come from: real tickers are
  downloaded once and cached on disk, dummy ones are generated."
  (:require [stonks.data :as data]
            [stonks.store :as store]))

(def real-symbols
  "Real tickers (Yahoo Finance symbols; Oslo Børs uses the .OL suffix)."
  {"EQNR.OL" {:name "Equinor ASA"}})

(def symbols
  (merge (update-vals data/symbols (fn [cfg] {:name (:name cfg) :source :dummy}))
         (update-vals real-symbols #(assoc % :source :yahoo))))

(defonce ^:private cache (atom {}))

(defn- remember! [sym series]
  (swap! cache assoc sym series)
  series)

(defn series
  "{:symbol :name :source ... :candles [...]} for sym, or nil if unknown.
  Real tickers are read from disk, and downloaded if not stored yet."
  [sym]
  (when-let [{:keys [name source]} (get symbols sym)]
    (case source
      :dummy {:symbol sym :name name :source :dummy :candles (data/series sym)}
      :yahoo (or (get @cache sym)
                 (locking cache
                   (or (get @cache sym)
                       (remember! sym (or (store/read-series sym)
                                          (store/update! sym)))))))))

(defn refresh!
  "Download new data for a real ticker. Returns the series, or nil for dummy
  and unknown symbols."
  [sym]
  (when (= :yahoo (get-in symbols [sym :source]))
    (locking cache
      (remember! sym (store/update! sym)))))

(defn info
  "Series metadata without the candles."
  [{:keys [candles] :as series}]
  (assoc (dissoc series :candles)
         :count (count candles)
         :first-date (:date (first candles))
         :last-date (:date (peek candles))))
