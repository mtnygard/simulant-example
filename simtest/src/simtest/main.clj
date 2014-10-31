(ns simtest.main)

(defmulti run-command (fn [command & _] command))

(def output (agent 0))

(defn prflush [s v] (do (print v) (flush) v))

(defn dot [a]
  (send-off output prflush
            (cond
             (< 15 (count a))    \#
             (< 10 (count a) 16) \+
             (< 5 (count a) 11)  \-
             :else               \.)))

(defn error [reason detail] {:result :error :reason reason :detail detail})

(defmacro error-generators
  [& reasons]
  (assert (every? keyword? reasons))
  (list* `do
         (for [r reasons]
           `(def ~(symbol (name r)) (partial error ~r)))))

(error-generators :argument-error :missing-model :missing-resource :transaction-exception :required-argument)

(defn print-errors
  "Replace this with a hook into your preferred logging framework."
  [errors]
  (binding [*out* *err*]
    (doseq [e (filter #(not= :ok (:result %)) (flatten errors))]
      (println e))))
