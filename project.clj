(defproject com.datomic/simulant-example "0.1.0-SNAPSHOT"
  :description      "Example application and simulation"
  :plugins          [[lein-modules "0.3.11"]]
  :modules          {:dirs       ["simtest"]
                     :inherited  {:repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"}}
                                  :dependencies [[org.clojure/clojure _]]

                                  :license      {:name         "Eclipse Public License - v 1.0"
                                                 :url          "http://www.eclipse.org/legal/epl-v10.html"
                                                 :distribution :repo
                                                 :comments     "same as Clojure"}

                                  :jvm-opts     ["-Xmx2g" "-Xms2g" "-server" "-Ddatomic.objectCacheMax=128m"
                                                 "-Ddatomic.memoryIndexMax=256m" "-Ddatomic.memoryIndexThreshold=32m"]

                                  :aliases      {"all" ^:displace ["do" "clean," "test," "install"]}}
                     :subprocess nil
                     :versions   {clojure          "1.7.0-rc1"
                                  data.generators  "0.1.2"
                                  tools.cli        "0.3.1"
                                  datomic-free     "0.9.5173"
                                  simulant         "0.1.6"
                                  causatum         "0.3.0"
                                  commons-math3    "3.3"

                                  ;; Interprocess communication
                                  transit-cljs     "0.8.220"
                                  transit-clj      "0.8.275"

                                  ;; For the UI
                                  io.pedestal      "0.4.0"
                                  clojurescript    "0.0-3308"
                                  kioo             "0.4.0"
                                  org.omcljs/om    "0.8.8"
                                  environ          "1.0.0"
                                  figwheel         "0.3.3"
                                  figwheel-sidecar "0.3.3"
                                  weasel           "0.6.0"}})
