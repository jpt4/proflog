(ns proflog.core-logic-host-probe
  "Command-line entrypoint for printing the core.logic host artifact report."
  (:gen-class)
  (:require [proflog.core-logic-host :as host]))

(defn -main
  [& _args]
  (println (host/format-host-info (host/host-info))))
