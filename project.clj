(defproject com.datomic/simulant-example "0.1.0-SNAPSHOT"
  :description      "Example application and simulation"

  :plugins          [[lein-modules "0.3.9"]]

  :modules          {:inherited
                     {:repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"}}
                      :dependencies [[org.clojure/clojure            _]
                                     [com.datomic/datomic-free       _
                                      :exclusions [org.clojure/clojure]]]

                      :license      {:name "Eclipse Public License - v 1.0"
                                     :url "http://www.eclipse.org/legal/epl-v10.html"
                                     :distribution :repo
                                     :comments "same as Clojure"}

                      :jvm-opts     ["-Xmx2g" "-Xms2g" "-server" "-Ddatomic.objectCacheMax=128m"
                                     "-Ddatomic.memoryIndexMax=256m" "-Ddatomic.memoryIndexThreshold=32m"]

                      :aliases      {"all" ^:displace ["do" "clean," "test," "install"]}}

                     :versions     {clojure         "1.7.0-alpha1"
                                    data.generators "0.1.2"
                                    tools.cli       "0.3.1"
                                    datomic-free    "0.9.4899"
                                    simulant        "0.1.6"
                                    io.pedestal     "0.3.1"
                                    causatum        "0.3.0"
                                    commons-math3   "3.3"}})
