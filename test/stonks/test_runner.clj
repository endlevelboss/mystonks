(ns stonks.test-runner
  "Minimal test runner (no git needed): runs every stonks.*-test namespace."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn- test-namespaces []
  (->> (file-seq (io/file "test"))
       (map #(.getPath ^java.io.File %))
       (filter #(str/ends-with? % "_test.clj"))
       (map #(-> %
                 (str/replace #"^test[/\\]" "")
                 (str/replace #"\.clj$" "")
                 (str/replace #"[/\\]" ".")
                 (str/replace "_" "-")
                 symbol))
       sort))

(defn -main [& _]
  (let [nss (test-namespaces)]
    (apply require nss)
    (let [{:keys [fail error]} (apply t/run-tests nss)]
      (shutdown-agents)
      (System/exit (if (zero? (+ fail error)) 0 1)))))
