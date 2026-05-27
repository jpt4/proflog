(ns proflog.core-logic-host
  "Report which core.logic implementation the current JVM is actually using.

   Several ADR-32/37 experiments depended on distinguishing Maven artifacts,
   local source overlays, and instrumented development copies. Keeping that
   check in its own namespace prevents deployment diagnostics from becoming
   proof-search behavior."
  (:require [clojure.core.logic]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util Properties]))

(def core-logic-source-path "clojure/core/logic.clj")
(def core-logic-pom-path "META-INF/maven/org.clojure/core.logic/pom.properties")
(def core-logic-marker-var 'proflog-adr32-host-marker)

(defn resource-url
  "Return the classpath URL string for `path`, if present."
  [path]
  (some-> (io/resource path) str))

(defn pom-properties
  "Return core.logic Maven pom.properties as a plain map when the artifact
  exposes one on the classpath."
  []
  (when-let [resource (io/resource core-logic-pom-path)]
    (with-open [reader (io/reader resource)]
      (let [props (Properties.)]
        (.load props reader)
        (into {} (for [[k v] props]
                   [(str k) (str v)]))))))

(defn source-kind
  [source-url]
  (cond
    (nil? source-url) :missing
    (str/includes? source-url ".m2/repository") :maven-jar
    (str/includes? source-url "/vendor/") :local-source
    :else :classpath-source))

(defn host-info
  "Report the core.logic implementation visible to this runtime."
  []
  (let [source (resource-url core-logic-source-path)
        pom (pom-properties)
        marker (some-> (ns-resolve 'clojure.core.logic core-logic-marker-var)
                       var-get)]
    {:source source
     :source-kind (source-kind source)
     :pom (resource-url core-logic-pom-path)
     :group-id (get pom "groupId")
     :artifact-id (get pom "artifactId")
     :version (get pom "version")
     :marker marker}))

(defn format-host-info
  [{:keys [source source-kind pom group-id artifact-id version marker]}]
  (str "core.logic source: " source "\n"
       "core.logic source-kind: " (name source-kind) "\n"
       "core.logic pom: " (or pom "<none>") "\n"
       "core.logic group-id: " (or group-id "<none>") "\n"
       "core.logic artifact-id: " (or artifact-id "<none>") "\n"
       "core.logic version: " (or version "<none>") "\n"
       "core.logic marker: " (or marker "<none>")))
