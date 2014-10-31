(defproject simtest "0.1.0-SNAPSHOT"
  :description "Simulation test"
  :plugins        [[lein-modules "0.3.9"]
                   [lein-expand-resource-paths "0.0.1"]]
  :dependencies   [[com.datomic/simulant         _]
                   [org.clojure/data.generators  _]
                   [org.clojure/tools.cli        _]
                   [org.craigandera/causatum     _]]
  :resource-paths ["resources"]
  :repl-options   {:init (do (set! *print-length* 200) (set! *print-level* 15))}
  :main           simtest.cli
  :profiles       {:dev {:dependencies [[org.apache.commons/commons-math3 "3.3"]]}})
