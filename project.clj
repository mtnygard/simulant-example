(defproject com.datomic/simulant-example "0.1.0-SNAPSHOT"
  :description      "Example application and simulation"
  :plugins          [[lein-modules "0.3.11"]]
  :modules          {:dirs       ["simtest", "example-ui"]
                     :inherited  {:repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"}}
                                  :dependencies [[org.clojure/clojure "1.8.0"]]

                                  :license      {:name         "Eclipse Public License - v 1.0"
                                                 :url          "http://www.eclipse.org/legal/epl-v10.html"
                                                 :distribution :repo
                                                 :comments     "same as Clojure"}

                                  :jvm-opts     ["-Xmx2g" "-Xms2g" "-server" "-Ddatomic.objectCacheMax=128m"
                                                 "-Ddatomic.memoryIndexMax=256m" "-Ddatomic.memoryIndexThreshold=32m"]

                                  :aliases      {"all" ^:displace ["do" "clean," "test," "install"]}}
                     :subprocess nil
                     :versions   {clojure          "1.8.0"
                                  data.generators  "0.1.2"
                                  tools.cli        "0.3.1"
                                  datomic-free     "0.9.5327"
                                  simulant         "0.1.8"
                                  causatum         "0.3.0"
                                  commons-math3    "3.3"

                                  ;; Interprocess communication
                                  transit-cljs     "0.8.220"
                                  transit-clj      "0.8.275"

                                  ;; For the UI
                                  io.pedestal      "0.4.0"
                                  ring             "1.3.2"
                                  clojurescript    "0.0-3308"
                                  org.omcljs/om    "0.9.0-SNAPSHOT"
                                  environ          "1.0.0"
                                  figwheel         "0.3.3"
                                  figwheel-sidecar "0.3.3"
                                  weasel           "0.6.0"
                                  ch.qos.logback   "1.1.2"
                                  org.slf4j        "1.7.7"
                                  enlive           "1.1.5"}})
