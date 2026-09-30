(ns stonks.server
  (:require [clojure.string :as str]
            [jsonista.core :as j]
            [reitit.ring :as ring]
            [ring.middleware.params :refer [wrap-params]]
            [stonks.data :as data]
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

(defn- with-candles
  "Wrap a handler (fn [req candles]) with symbol lookup and 404 handling."
  [handler]
  (fn [req]
    (let [sym (str/upper-case (get-in req [:path-params :symbol]))]
      (if-let [candles (data/series sym)]
        (handler req candles)
        (json 404 {:error (str "Unknown symbol " sym)})))))

(defn- parse-window [s]
  (when-let [n (some-> s parse-long)]
    (when (>= n 10) n)))

(def app
  (ring/ring-handler
   (ring/router
    [["/" {:get (fn [_] {:status 200
                         :headers {"Content-Type" "text/html; charset=utf-8"}
                         :body (views/index-page data/symbols)})}]
     ["/api"
      ["/symbols" {:get (fn [_]
                          (json 200 (for [[sym {:keys [name]}] (sort-by key data/symbols)]
                                      {:symbol sym :name name})))}]
      ["/stocks/:symbol"
       ["/candles" {:get (with-candles (fn [_ candles] (json 200 candles)))}]
       ["/analysis" {:get (with-candles
                            (fn [req candles]
                              (json 200 (trend/analyze
                                         candles
                                         {:window (parse-window
                                                   (get-in req [:query-params "window"]))}))))}]]]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler))
   {:middleware [wrap-params]}))
