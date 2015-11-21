(ns simtest.main)

(defmulti run-command (fn [command & _] command))

(def output (agent 0))

(defn prflush [s v] (do (print v) (flush) v))

(defn dot [a]
  (send-off output prflush
            (cond
             (< 25 (count a))    \*
             (< 15 (count a))    \#
             (< 10 (count a) 16) \+
             (< 5 (count a) 11)  \-
             :else               \.))
  a)

(defn error [reason detail] {:result :error :reason reason :detail detail})

(defmacro error-generators
  [& reasons]
  (assert (every? keyword? reasons))
  (list* `do
         (for [r reasons]
           `(def ~(symbol (name r)) (partial error ~r)))))

(error-generators :argument-error :missing-model :missing-resource :transaction-error :required-argument)

(defn print-errors
  "Replace this with a hook into your preferred logging framework."
  [errors]
  (binding [*out* *err*]
    (doseq [e (filter #(not= :ok (:result %)) (flatten errors))]
      (println e))))

(defmacro condp->
   "Takes an expression and a set of predicate/form pairs. Threads expr (via ->)
   through each form for which the corresponding predicate is true of expr.
   Note that, unlike cond branching, condp-> threading does not short circuit
   after the first true test expression."
   [expr & clauses]
   (assert (even? (count clauses)))
   (let [g (gensym)
         pstep (fn [[pred step]] `(if (~pred ~g) (-> ~g ~step) ~g))]
     `(let [~g ~expr
            ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
        ~g)))
