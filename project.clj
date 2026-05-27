(defproject cljtap "0.1.0-SNAPSHOT"
  :description "αleanTAP-EP: Fitting's Proflog implemented in core.logic"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.logic "1.0.1"]]
  :source-paths ["src"]
  :test-paths ["test"]
  :test-selectors
  {:A    (fn [m] (re-find #"^test-A\d" (str (:name m))))
   :B    (fn [m] (re-find #"^test-B\d" (str (:name m))))
   :Bp   (fn [m] (re-find #"^test-Bp\d" (str (:name m))))
   :Bpp  (fn [m] (re-find #"^test-Bpp\d" (str (:name m))))
   :Bc   (fn [m] (re-find #"^test-Bc\d" (str (:name m))))
   :Bd   (fn [m] (re-find #"^test-Bd\d" (str (:name m))))
   :C    (fn [m] (re-find #"^test-C\d" (str (:name m))))
   :D    (fn [m] (re-find #"^test-D\d" (str (:name m))))
   :Dp   (fn [m] (re-find #"^test-Dp\d" (str (:name m))))
   :DI   (fn [m] (re-find #"^test-DI\d" (str (:name m))))
   :E    (fn [m] (re-find #"^test-E\d" (str (:name m))))
   :F    (fn [m] (re-find #"^test-F\d" (str (:name m))))
   :G    (fn [m] (re-find #"^test-G\d" (str (:name m))))
   :H    (fn [m] (re-find #"^test-H\d" (str (:name m))))
   :I    (fn [m] (re-find #"^test-I\d" (str (:name m))))
   :J    (fn [m] (re-find #"^test-J\d" (str (:name m))))
   :K    (fn [m] (re-find #"^test-K\d" (str (:name m))))
   :L    (fn [m] (re-find #"^test-L\d" (str (:name m))))
   :M    (fn [m] (re-find #"^test-M\d" (str (:name m))))
   :N    (fn [m] (re-find #"^test-N\d" (str (:name m))))
   :O    (fn [m] (re-find #"^test-O\d" (str (:name m))))
   :P    (fn [m] (re-find #"^test-P\d" (str (:name m))))
   :Q    (fn [m] (re-find #"^test-Q\d" (str (:name m))))
   :R    (fn [m] (re-find #"^test-R\d" (str (:name m))))
   :S    (fn [m] (re-find #"^test-S\d" (str (:name m))))
   :T    (fn [m] (re-find #"^test-T\d" (str (:name m))))
   :U    (fn [m] (re-find #"^test-U\d" (str (:name m))))
   :V    (fn [m] (re-find #"^test-V\d" (str (:name m))))
   :W    (fn [m] (re-find #"^test-W\d" (str (:name m))))
   :X    (fn [m] (re-find #"^test-X\d" (str (:name m))))
   :Y    (fn [m] (re-find #"^test-Y\d" (str (:name m))))
   :Z    (fn [m] (re-find #"^test-Z\d" (str (:name m))))
   :ADV  (fn [m] (re-find #"^test-ADV\d" (str (:name m))))
   :SUB  (fn [m] (re-find #"^test-SUB\d" (str (:name m))))
   :RV   (fn [m] (re-find #"^test-RV\d" (str (:name m))))
   :MV   (fn [m] (re-find #"^test-MV\d" (str (:name m))))
   :OC   (fn [m] (re-find #"^test-OC\d" (str (:name m))))
   :TC   (fn [m] (re-find #"^test-TC\d" (str (:name m))))
   :PA   (fn [m] (re-find #"^test-PA\d" (str (:name m))))
   :SO   (fn [m] (re-find #"^test-SO\d" (str (:name m))))
   :SS   (fn [m] (re-find #"^test-SS\d" (str (:name m))))
   :GP   (fn [m] (re-find #"^test-GP\d" (str (:name m))))
   :GV   (fn [m] (re-find #"^test-GV\d" (str (:name m))))
   :FD   (fn [m] (re-find #"^test-FD\d" (str (:name m))))
   :pelletier-prompt (fn [m & _] (:pelletier-prompt m))
   :pelletier-passing (fn [m & _] (:pelletier-passing m))
   :pelletier-exploratory (fn [m & _] (:pelletier-exploratory m))
   :pelletier-comparison (fn [m & _] (:pelletier-comparison m))
   :slow (fn [m & _] (:slow m))
   :constructor-recursive (fn [m & _] (:constructor-recursive m))}
  :aliases {"test-section"         ["run" "-m" "cljtap.run-section"]
            "test-all-timed"       ["run" "-m" "cljtap.run-section" "--all"]
            "test-vars"            ["run" "-m" "proflog.focused-test-runner"]
            "test-proflog-fast"    ["test"
                                    "proflog.ast-test"
                                    "proflog.adversarial-test"
                                    "proflog.language-test"
                                    "proflog.normalize-test"
                                    "proflog.pretty-test"
                                    "proflog.subst-test"
                                    "proflog.tabling-test"
                                    "proflog.existential-disequality-test"
                                    "proflog.gamma-test"
                                    "proflog.closed-term-gamma-test"
                                    "proflog.frontend-test"
                                    "proflog.formula-profile-test"
                                    "proflog.robinson-q-test"
                                    "proflog.kernel.dispatch-test"
                                    "proflog.kernel.first-order-test"
                                    "proflog.kernel.propositional-test"
                                    "proflog.pelletier-layering-test"
                                    "proflog.kernel-test"
                                    "proflog.proof-test"
                                    "proflog.sjas-correspondence-test"
                                    "proflog.equality-test"
                                    "proflog.oracle.herbrand-test"
                                    "proflog.program-test"
                                    "proflog.query-test"]
            "test-proflog-extended" ["test"
                                    "proflog.answers-test"
                                     "proflog.integration-families-test"
                                     "proflog.list-programs-test"
                                     "proflog.quantified-programs-test"
                                     "proflog.query-extended-test"
                                     "proflog.recursive-synthesis-test"
                                     "proflog.reverse-program-synthesis-test"
                                     "proflog.synthesis-modes-test"
                                     "proflog.nim-synthesis-test"]
            "test-proflog-parity" ["test"
                                   "proflog.parity-test"]
            "test-proflog-parity-experimental" ["test"
                                                "proflog.parity-experimental-test"]
            "test-proflog-pelletier" ["test"
                                      ":pelletier-passing"
                                      "proflog.pelletier-test"]
            "test-proflog-pelletier-prompt" ["test"
                                             ":pelletier-prompt"
                                             "proflog.pelletier-test"]
            "test-proflog-pelletier-exploratory" ["test"
                                                  ":pelletier-exploratory"
                                                  "proflog.pelletier-test"]
            "test-proflog-pelletier-comparison" ["test"
                                                 ":pelletier-comparison"
                                                 "proflog.pelletier-comparison-test"]
            "test-proflog-hard-families" ["test"
                                          "proflog.legacy-hard-families-test"]
            "test-proflog-fitting-programs" ["test"
                                             "proflog.fitting-programs-test"]
            "test-proflog-turing-completeness" ["test"
                                                "proflog.turing-completeness-test"
                                                "proflog.minsky-trace-performance-test"
                                                "proflog.combinatory-logic-test"]
            "test-proflog-minsky-trace-performance" ["test"
                                                      "proflog.minsky-trace-performance-test"]
            "test-proflog-combinatory-logic" ["test"
                                              "proflog.combinatory-logic-test"]
            "test-proflog-robinson-q" ["test"
                                        "proflog.robinson-q-test"]
            "test-proflog-kernel-finite-verifiers" ["test"
                                                    "proflog.kernel-finite-verifiers-test"]
            "test-proflog-relational-equality-fragment" ["test"
                                                         "proflog.kernel.relational-equality-fragment-test"]
            "test-proflog-sjas" ["test"
                                 "proflog.willard-sjas-test"]
            "test-proflog-sjas-focused" ["run" "-m" "proflog.focused-test-runner"
                                         "proflog.willard-sjas-test"]
            "test-proflog-sjas-slow" ["test"
                                      ":slow"
                                      "proflog.willard-sjas-test"]
            "test-proflog-legacy-subsumption" ["test"
                                               "proflog.legacy-subsumption-test"]
            "test-proflog-constructor-recursive" ["test"
                                                  ":constructor-recursive"
                                                  "proflog.list-programs-test"
                                                  "proflog.constructor-recursive-kernel-test"
                                                  "proflog.kernel.constructor-recursive-profile-test"
                                                  "proflog.kernel.constructor-recursive-test"]
            "test-proflog-core-logic-host" ["test"
                                            "proflog.core-logic-host-test"]
            "test-proflog-legacy-impurity" ["test"
                                            "proflog.legacy-impurity-test"]
            "probe-proflog-legacy-stream" ["run" "-m" "proflog.legacy-stream-probe"]
            "probe-proflog-list-kernel-matrix" ["run" "-m" "proflog.list-kernel-matrix-probe"]
            "probe-proflog-gv" ["run" "-m" "proflog.gv-probe"]
            "probe-proflog-turing-completeness" ["run" "-m" "proflog.turing-completeness-long-probe"]
            "probe-proflog-robinson-q" ["run" "-m" "proflog.robinson-q-probe"]
            "probe-proflog-relational-equality-fragment" ["run" "-m" "proflog.relational-equality-fragment-probe"]
            "probe-core-logic-host" ["run" "-m" "proflog.core-logic-host-probe"]
            "probe-core-logic-tabling" ["run" "-m" "proflog.core-logic-tabling-probe"]
            "probe-core-logic-count" ["run" "-m" "proflog.core-logic-count-probe"]
            "probe-relational-fuel-performance" ["run" "-m" "proflog.relational-fuel-performance-probe"]}
  :profiles {:core-logic-1.1.1
             {:dependencies ^:replace [[org.clojure/clojure "1.11.1"]
                                       [org.clojure/core.logic "1.1.1"]]}
             :core-logic-source-overlay
             {:source-paths ^:replace ["vendor/core.logic-1.1.1/src"
                                       "src"]
              :dependencies ^:replace [[org.clojure/clojure "1.11.1"]
                                       [org.clojure/core.logic "1.1.1"]]}})
