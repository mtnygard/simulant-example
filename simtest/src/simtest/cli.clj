(ns simtest.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [simtest.main :as m]))

(def cli-options
  [["-d" "--datomic-uri URI" "Datomic URI"
    :required "URI"
    :default "datomic:free://localhost:4334/simulant-example"
    :parse-fn identity]
   ["-n" "--number-of-visitors COUNT" "Number of visitors for a test"
    :default 100000
    :parse-fn #(Integer/parseInt %)]
   ["-m" "--model-name NAME" "Name of the model to use in a test"
    :default "daily-traffic"
    :parse-fn identity]
   ["-t" "--test-duration MINUTES" "How long the test should execute"
    :default 5
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn usage
  [options-summary]
  (->> ["Command line interface to the Simulant example"
        ""
        "Usage: lein run [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "install-schema       One-time database setup"
        "make-model           Create a model with default parameters"
        "set-model-parameter  Adjust a model parameter. Requires parameter name and value as args."
        "list-models          View models in the database"
        "make-activity        Create an activity stream"
        "list-activities      Show activity streams in the database"
        "run-test             Execute a simulation test"]
       (str/join \newline)))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn exit
  [status msg]
  (println)
  (println msg)
  (System/exit status))

(defmethod m/run-command :default
  [_ options arguments]
  (exit 1 (error-msg ["No such action"])))

(defn execute-and-exit
  [namespace command options arguments summary]
  (require namespace)
  (let [res (m/run-command command options arguments)]
    (case res
      :ok    (exit 0 res)
      :usage (exit 1 (usage summary))
      (exit -1 (str "Unknown return value " res)))))

(def command->namespace
  {"install-schema"        'simtest.database
   "make-model"            'simtest.model
   "set-model-parameter"   'simtest.model
   "list-model-parameters" 'simtest.model
   "list-models"           'simtest.model
   "make-activity"         'simtest.generator
   "list-activities"       'simtest.generator
   "run-test"              'simtest.executor})

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
     (:help options)         (exit 0 (usage summary))
     (< (count arguments) 1) (exit 1 (usage summary))
     errors                  (exit 1 (error-msg errors))
     :else
     (if-let [that-ns (command->namespace (first arguments))]
       (execute-and-exit that-ns (keyword (first arguments)) options arguments summary)
       (exit 1 (usage summary))))))
