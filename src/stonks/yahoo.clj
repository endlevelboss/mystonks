(ns stonks.yahoo
  "Daily candles from Yahoo Finance's chart API (unofficial, no API key).
  Oslo Børs tickers use the .OL suffix, e.g. EQNR.OL."
  (:require [jsonista.core :as j])
  (:import (java.net URI URLEncoder)
           (java.net.http HttpClient HttpClient$Redirect HttpRequest
                          HttpResponse$BodyHandlers)
           (java.time Duration Instant LocalDate ZoneId ZoneOffset)))

(def ^:private base-url "https://query1.finance.yahoo.com/v8/finance/chart/")

(def ^:private earliest "1990-01-01")

(defn- round-to [decimals x]
  (when x
    (let [m (Math/pow 10 decimals)]
      (/ (Math/round (* m (double x))) m))))

(defn- epoch-second [date]
  (.toEpochSecond (.atStartOfDay (LocalDate/parse date) ZoneOffset/UTC)))

(defn parse-chart
  "Turn a decoded chart API response (string keys) into
  {:meta {:symbol :name :currency :exchange} :candles [...]}.
  Days with missing prices are dropped; duplicate dates keep the last entry."
  [body]
  (let [{:strs [result error]} (get body "chart")
        r (first result)]
    (when (or error (nil? r))
      (throw (ex-info (str "Yahoo error: " (or (get error "description") "no result"))
                      {:error error})))
    (let [meta (get r "meta")
          zone (ZoneId/of (get meta "exchangeTimezoneName" "UTC"))
          decimals (get meta "priceHint" 2)
          quote (first (get-in r ["indicators" "quote"]))
          adj (get-in r ["indicators" "adjclose" 0 "adjclose"])
          col (fn [k i] (get-in quote [k i]))
          candles (keep-indexed
                   (fn [i ts]
                     (let [[o h l c] (map #(col % i) ["open" "high" "low" "close"])]
                       (when (and o h l c)
                         {:date (str (.toLocalDate (.atZone (Instant/ofEpochSecond (long ts)) zone)))
                          :open (round-to decimals o)
                          :high (round-to decimals h)
                          :low (round-to decimals l)
                          :close (round-to decimals c)
                          :volume (long (or (col "volume" i) 0))
                          :adj-close (round-to 4 (get adj i))})))
                   (get r "timestamp"))]
      {:meta {:symbol (get meta "symbol")
              :name (or (get meta "longName") (get meta "shortName"))
              :currency (get meta "currency")
              :exchange (get meta "fullExchangeName")}
       :candles (vec (vals (into (sorted-map) (map (juxt :date identity)) candles)))})))

(def ^:private client
  (delay (-> (HttpClient/newBuilder)
             (.followRedirects HttpClient$Redirect/NORMAL)
             (.connectTimeout (Duration/ofSeconds 10))
             (.build))))

(defn fetch-daily
  "Download daily candles for ticker from :from (a yyyy-MM-dd string, default
  the full history) until now."
  ([ticker] (fetch-daily ticker {}))
  ([ticker {:keys [from] :or {from earliest}}]
   (let [url (str base-url (URLEncoder/encode ^String ticker "UTF-8")
                  "?interval=1d&events=div%2Csplit"
                  "&period1=" (epoch-second from)
                  "&period2=" (.getEpochSecond (Instant/now)))
         req (-> (HttpRequest/newBuilder (URI/create url))
                 (.header "User-Agent" "Mozilla/5.0")
                 (.timeout (Duration/ofSeconds 30))
                 (.GET)
                 (.build))
         resp (.send ^HttpClient @client req (HttpResponse$BodyHandlers/ofString))
         body (.body resp)]
     (when-not (= 200 (.statusCode resp))
       (throw (ex-info (str "Yahoo returned HTTP " (.statusCode resp) " for " ticker)
                       {:status (.statusCode resp)
                        :body (subs body 0 (min 500 (count body)))})))
     (parse-chart (j/read-value body)))))
