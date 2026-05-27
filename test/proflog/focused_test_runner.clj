(ns proflog.focused-test-runner
  "Run selected test namespaces one var at a time with progress and timings.

   Resource-heavy suites such as the SJAS regressions can spend minutes inside
   a single test var. Ordinary `clojure.test` namespace runs hide that progress
   until the namespace finishes, which makes it difficult to distinguish a true
   hang from a slow but advancing semantic check. This runner keeps the same
   test bodies and assertions but prints an explicit start/end line around each
   var."
  (:require [clojure.string :as str]
            [clojure.test :as t]))

(defn- test-vars
  "Return test vars from `ns-sym` in deterministic name order."
  [ns-sym]
  (require ns-sym)
  (->> (ns-publics ns-sym)
       vals
       (filter (comp :test meta))
       (sort-by (comp str :name meta))))

(defn- update-counter
  "Accumulate assertion outcomes while preserving normal clojure.test output."
  [counter event]
  (case (:type event)
    :pass (swap! counter update :pass inc)
    :fail (do (swap! counter update :fail inc)
              (t/with-test-out
                (println "FAIL:" (-> event :var meta :name))
                (when (:message event)
                  (println " " (:message event)))
                (println " expected:" (pr-str (:expected event)))
                (println "   actual:" (pr-str (:actual event)))))
    :error (do (swap! counter update :error inc)
               (t/with-test-out
                 (println "ERROR:" (-> event :var meta :name))
                 (when (:message event)
                   (println " " (:message event)))
                 (println " " (:actual event))))
    nil))

(defn- run-test-var
  "Run one test var, printing progress before and after evaluation."
  [counter var]
  (let [name-str (str (:name (meta var)))
        start (System/nanoTime)]
    (println (str ":TEST " name-str))
    (flush)
    (binding [t/report (partial update-counter counter)]
      (t/test-var var))
    (let [elapsed-ms (/ (- (System/nanoTime) start) 1000000.0)]
      (println (format ":DONE %s %.3f ms" name-str elapsed-ms))
      (flush))))

(defn- run-namespace
  "Run all tests in one namespace and return assertion counters."
  [ns-sym]
  (let [vars (test-vars ns-sym)
        counter (atom {:pass 0 :fail 0 :error 0})]
    (println (str ":NAMESPACE " ns-sym " " (count vars) " tests"))
    (flush)
    (doseq [var vars]
      (run-test-var counter var))
    @counter))

(defn -main
  "Entry point for `lein test-vars <ns>...`."
  [& args]
  (when (empty? args)
    (println "Usage: lein test-vars <test.namespace>...")
    (System/exit 2))
  (let [results (map (comp run-namespace symbol) args)
        totals (apply merge-with + results)
        failures (+ (:fail totals 0) (:error totals 0))]
    (println (format ":SUMMARY pass=%d fail=%d error=%d"
                     (:pass totals 0)
                     (:fail totals 0)
                     (:error totals 0)))
    (shutdown-agents)
    (System/exit (if (zero? failures) 0 1))))
