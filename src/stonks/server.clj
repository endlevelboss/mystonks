(ns stonks.server
  (:require [clojure.string :as str]
            [jsonista.core :as j]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [stonks.indicators :as indicators]
            [stonks.market :as market]
            [stonks.trend :as trend]
            [stonks.views :as views]))

(defn- camel [k]
  (let [[head & tail] (str/split (name k) #"-")]
    (apply str head (map str/capitalize tail))))

(def ^:private mapper (j/object-mapper {:encode-key-fn camel}))

(defn- json [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (j/write-value-as-string body mapper)})

(defn- symbol-param [req]
  (str/upper-case (get-in req [:path-params :symbol])))

(defn- wrap-errors
  "Report failures (e.g. the data source being unreachable) as JSON."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (json 502 {:error (ex-message e)})))))

(defn- with-series
  "Wrap a handler (fn [req series]) with symbol lookup and 404 handling."
  [handler]
  (wrap-errors
   (fn [req]
     (let [sym (symbol-param req)]
       (if-let [series (market/series sym)]
         (handler req series)
         (json 404 {:error (str "Unknown symbol " sym)}))))))

(defn- parse-window [s]
  (when-let [n (some-> s parse-long)]
    (when (>= n 10) n)))

(defn- refresh [req]
  (let [sym (symbol-param req)]
    (if-not (market/symbols sym)
      (json 404 {:error (str "Unknown symbol " sym)})
      (if-let [series (market/refresh! sym)]
        (json 200 (market/info series))
        (json 400 {:error (str sym " is generated test data")})))))

(def app
  (ring/ring-handler
   (ring/router
    [["/" {:get (fn [_] {:status 200
                         :headers {"Content-Type" "text/html; charset=utf-8"}
                         :body (views/index-page market/symbols)})}]
     ["/api"
      ["/symbols" {:get (fn [_]
                          (json 200 (for [[sym {:keys [name source]}] (sort-by key market/symbols)]
                                      {:symbol sym :name name :source source})))}]
      ["/stocks/:symbol"
       ["/info" {:get (with-series (fn [_ series] (json 200 (market/info series))))}]
       ["/candles" {:get (with-series (fn [_ series] (json 200 (:candles series))))}]
       ["/analysis" {:get (with-series
                            (fn [req series]
                              (json 200 (trend/analyze
                                         (:candles series)
                                         {:window (parse-window
                                                   (get-in req [:query-params "window"]))}))))}]
       ["/indicators" {:get (with-series
                              (fn [_ series]
                                (json 200 (indicators/indicators (:candles series)))))}]
       ["/refresh" {:post (wrap-errors refresh)}]]]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))
   {:middleware [wrap-params]}))
