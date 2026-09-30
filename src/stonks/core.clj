(ns stonks.core
  (:require [org.httpkit.server :as http]
            [stonks.server :as server])
  (:gen-class))

(defonce ^:private stop-fn (atom nil))

(defn stop! []
  (when-let [stop @stop-fn]
    (stop :timeout 100)
    (reset! stop-fn nil)))

(defn start!
  "Start the web server. The handler var is passed so REPL reloads take effect."
  ([] (start! {}))
  ([{:keys [port] :or {port 8080}}]
   (stop!)
   (reset! stop-fn (http/run-server #'server/app {:port port}))
   (println (str "Stonks running at http://localhost:" port))))

(defn -main [& _]
  (start! {:port (or (some-> (System/getenv "PORT") parse-long) 8080)}))
