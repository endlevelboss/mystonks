(ns stonks.views
  (:require [hiccup.page :refer [html5 include-css include-js]]))

(defn index-page [symbols]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Stonks"]
    (include-css "/style.css")
    (include-js "https://unpkg.com/lightweight-charts@4.2.3/dist/lightweight-charts.standalone.production.js")]
   [:body
    [:header
     [:h1 "Stonks"]
     [:div.controls
      [:label "Symbol "
       [:select#symbol
        (for [[sym {:keys [name]}] (sort-by key symbols)]
          [:option {:value sym} (str sym " — " name)])]]
      [:label "Trend window "
       [:select#window
        [:option {:value ""} "All"]
        (for [n [120 60 20]]
          [:option {:value n} (str "Last " n " days")])]]]]
    [:main
     [:section#summary.summary]
     [:div#chart.chart]
     [:ul.legend
      [:li [:span.swatch.sma20] "SMA 20"]
      [:li [:span.swatch.sma50] "SMA 50"]
      [:li [:span.swatch.regression] "Regression"]
      [:li [:span.swatch.support] "Support"]
      [:li [:span.swatch.resistance] "Resistance"]]]
    (include-js "/app.js")]))
