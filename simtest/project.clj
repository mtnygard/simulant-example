(defproject com.cognitect/simtest "0.1.0-SNAPSHOT"
  :description "Simulation test"
  :plugins        [[lein-modules "0.3.11"]
                   [lein-expand-resource-paths "0.0.1"]]
  :dependencies   [[com.datomic/datomic-free         _  :exclusions [org.clojure/clojure joda-time]]
                   [com.datomic/simulant             _]
                   [org.clojure/data.generators      _]
                   [org.clojure/tools.cli            _]
                   [org.craigandera/causatum         _]
                   [org.apache.commons/commons-math3 _]]
  :resource-paths ["resources"]
  :main           simtest.cli)
