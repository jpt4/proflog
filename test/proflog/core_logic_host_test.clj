(ns proflog.core-logic-host-test
  (:require [clojure.test :refer [deftest is]]
            [proflog.core-logic-host :as host]))

(deftest core-logic-host-is-auditable
  (let [{:keys [source source-kind artifact-id version]} (host/host-info)]
    (is (string? source))
    (is (re-find #"clojure/core/logic\.clj" source))
    (is (contains? #{:maven-jar :local-source :classpath-source} source-kind))
    (is (or (nil? artifact-id)
            (= "core.logic" artifact-id)))
    (is (or (nil? version)
            (re-find #"^\d+\.\d+\.\d+" version)))))

