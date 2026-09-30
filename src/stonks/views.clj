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
        (for [[group source] [["Oslo Børs" :yahoo] ["Test data" :dummy]]]
          [:optgroup {:label group}
           (for [[sym {:keys [name]}] (sort-by key symbols)
                 :when (= source (:source (symbols sym)))]
             [:option {:value sym} (str sym " — " name)])])]]
      [:label "Trend window "
       [:select#window
        (for [[n label] [[nil "All"] [500 "Last 500 days (~2y)"] [250 "Last 250 days (~1y)"]
                         [120 "Last 120 days"] [60 "Last 60 days"] [20 "Last 20 days"]]]
          [:option {:value (str n) :selected (= n 250)} label])]]
      [:button#refresh {:type "button" :hidden true} "Update data"]]]
    [:main
     [:section#summary.summary]
     [:div.charts
      [:div#chart.chart]
      [:ul.legend
       [:li [:span.swatch.sma20] "SMA 20"]
       [:li [:span.swatch.sma50] "SMA 50"]
       [:li [:span.swatch.regression] "Regression"]
       [:li [:span.swatch.support] "Support"]
       [:li [:span.swatch.resistance] "Resistance"]]
      [:div.pane
       [:div.pane-label "RSI 14 "
        [:span.muted "· overbought above 70, oversold below 30"]]
       [:div#rsi-chart.chart.small]]
      [:div.pane
       [:div.pane-label "MACD 12, 26, 9 "
        [:span.swatch.macd] "MACD " [:span.swatch.signal] "Signal "
        [:span.muted "· bars: MACD − signal"]]
       [:div#macd-chart.chart.small]]]
     [:p#source.source]]
    (include-js "/app.js")]))
