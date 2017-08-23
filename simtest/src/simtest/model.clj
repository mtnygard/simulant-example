(ns simtest.model
  "Activity stream model"
  (:require [causatum.event-streams :as es]
            [clojure.data.generators :as gen]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [datomic.api :as d]
            [simtest.main :as m])
  (:import [org.apache.commons.math3.distribution ParetoDistribution]))

(defn shopper-transitions
  "This function returns a table of non-normalized Markov transition
  probabilities. That just means that it shows the likelihood of
  moving from one state to the next. The extra column for max-delay is
  used within Causatum to randomize the timing of events.

  The map argument to this function contains model parameters that can
  come from the command line or from a model in the database."
  [{ext-search-rate     :search-engine-arrival-rate
    site-search-rate    :internal-search-rate
    search-success-rate :search-success-rate
    start-checkout-rate :start-checkout-rate
    abandon-rate        :abandon-rate
    :or
    {ext-search-rate     75
     site-search-rate    60
     search-success-rate 50
     start-checkout-rate  4
     abandon-rate        10}}]
  (let [base-rate (- 100 abandon-rate)]
    ;;start-state      end-state                                                weight  max-delay
    [[:start           :home                                   (- 100 ext-search-rate) 10]
     [:start           :product-page                                   ext-search-rate 10]

     [:home            :search                                        site-search-rate 8000]
     [:home            :abandon                                           abandon-rate 8000]
     [:home            :category                        (- base-rate site-search-rate) 8000]

     [:search          :product-page                               search-success-rate 8000]
     [:search          :search                       (- base-rate search-success-rate) 8000]
     [:search          :abandon                                           abandon-rate 8000]

     [:product-page    :purchase-start                             start-checkout-rate 8000]
     [:product-page    :abandon                                                     25 8000]
     [:product-page    :search                                                      10 8000]
     [:product-page    :category                     (- 100 25 10 start-checkout-rate) 8000]

     [:purchase-start  :product-page                                                10 8000]
     [:purchase-start  :abandon-checkout                                  abandon-rate 8000]
     [:purchase-start  :purchase-confirm                              (- base-rate 10) 8000]

     [:category        :product-page                               search-success-rate 16000]
     [:category        :abandon                                           abandon-rate  8000]
     [:category        :search                                        site-search-rate 16000]
     [:category        :category                                                    15 16000]
     [:category        :home     (- base-rate search-success-rate site-search-rate 15) 16000]]))

(defn edge-graph->causatum-tree
  "Translate from the tabular format I like to the tree format that
  Causatum uses."
  [m [from tos]]
  (assoc m from
         [(reduce (fn [v [f t w d]] (merge v {t {:weight w :delay [:random d]}})) {} tos)]))

(defn state-transition-model
  "Build the entire model as Causatum will use it. This combines the
  transition probabilities with some sampling functions to generate
  times."
  [model-parameters]
  {:graph (reduce edge-graph->causatum-tree {} (group-by first (shopper-transitions model-parameters)))
   :delay-ops {:constant (fn [rtime n] n)
               :random   (fn [rtime n] (inc (rand n)))}})

(defn arrival-time
  "Return the time T (in milliseconds from test start) at which
  an agent will arrive.

  This uniform distribution across the test time is an
  oversimplification. It would be good to model arrival rates with a
  more realistic distribution."
  [test-duration]
  (int
   (rand
    (- test-duration 15000))))

(defn initial-state [test-duration]
  {:state :start
   :rtime (arrival-time test-duration)})

(defn state-sequence
  "Return a single sequence of model states, paired with time (in
  milliseconds from test start). The sequence ends when it reaches a
  terminal state. That is a state with no probability of transition to
  any other state."
  [model-parameters test-duration]
  (es/event-stream
   (state-transition-model model-parameters)
   [(initial-state test-duration)]))

;; ----------------------------------------
;; Generators
;; ----------------------------------------
(def ^:private tlds ["com" "mil" "gov" "net" "org" "info" "de" "co.uk" "co.au" "ca" "fi"])

(def email-address-parts #(gen/uniform 2 5))
(def email-part-sizer    #(gen/uniform 3 30))

(defn email-address
  "Create a random email address. This does _not_ explore all possible
  RFC 822 formats, only a simplified subset of interesting cases."
  []
  (let [parts (reverse
               (cons (gen/rand-nth tlds)
                     (take (email-address-parts)
                           (repeatedly #(gen/symbol email-part-sizer)))))
        separators (cons \@ (repeat 5 \.))]
    (str/join (butlast (interleave parts separators)))))

;; ----------------------------------------
;; Create Pareto-distributed products & cats
;; ----------------------------------------
(defn pareto
  "Create a distribution that we can use to simulate heavy traffic on
  a 'hot' item or category. This is the classic 'long tail'
  distribution for product purchases."
  [scale shape]
  (let [d (ParetoDistribution. scale shape)]
    (fn [] (.sample d))))

(defn sampler
  "Return a function that samples from the collection. Each call to
  the arity-0 function will return a new sample, so it's clearly not a
  pure function."
  [dist coll]
  (let [size (dec (count coll))]
    (fn []
      (nth coll (long (min (dist) size))))))

(defn ingest
  "Return the data structure from an EDN file."
  [f]
  (if-let [result (some-> f
                          io/resource
                          io/reader
                          slurp
                          edn/read-string)]
    result
    (throw (ex-info "Missing resource"
                    (simtest.main/missing-resource "You probably need to load the namespace 'create-data' to build the test data files.")))))

(defn category-sampler
  "Return a function that samples from categories, using a
  distribution that simulates traffic concentration on a few 'hot'
  categories, but still has some activity spread across a wide variety
  of other categories."
  [{:keys [category-pareto-index]}]
  (sampler (pareto 1 category-pareto-index)
           (ingest "shop/category.edn")))

(defn product-sampler
  "Return a function that samples from categories, using a
  distribution that simulates traffic concentration on a few 'hot'
  categories, but still has some activity spread across a wide variety
  of other categories."
  [{:keys [product-pareto-index]}]
  (sampler (pareto 1 product-pareto-index)
           (ingest "shop/sku.edn")))

;; ----------------------------------------------------
;; Model creation
;; ----------------------------------------------------
(def default-model-parameters
  {:search-engine-arrival-rate 75
   :internal-search-rate       60
   :search-success-rate        50
   :start-checkout-rate        4
   :abandon-rate               10
   :product-pareto-index       2
   :category-pareto-index      1})

(defn model-entity
  "Return a Datomic entity map for a model"
  [name model-parameters]
  (merge model-parameters
         {:db/id      (d/tempid :model)
          :model/name name
          :model/type :model.type/shopping}))

(defn create-model!
  "Create a model in Datomic."
  ([conn name]
     (create-model! conn name default-model-parameters))
  ([conn name model-parameters]
     @(d/transact conn [(model-entity name model-parameters)])))

;; ----------------------------------------
;; CLI and helpers
;; ----------------------------------------

(defn model-for-name
  "Return the model entity that corresponds to model-name."
  [db model-name]
  (d/entity db [:model/name model-name]))

(defn query-models
  [db]
  (d/q '[:find ?kn ?n ?kt ?t ?kid ?id
         :in $ ?kn ?kt ?kid
         :where
         [?id :model/name ?n]
         [?id :model/type ?tid]
         [?tid :db/ident ?t]]
       db "Name" "Type" "DB ID"))

(defn models
  [db]
  (d/q '[:find (pull ?id [*])
         :where
         [?id :model/type]]
       db))

(defn pivot-map
  [m]
  (map (fn [[k v]] {:key k :value v}) m))

(defn keywordize
  [s]
  (if (= \: (first s))
    (keyword (subs s 1))
    (keyword s)))

(defmethod m/run-command :make-model
  [_ {:keys [datomic-uri model-name] :as opts} arguments]
  (if (empty? model-name)
    (m/argument-error (str "make-model requires a model name parameter"))
    (let [conn (d/connect datomic-uri)]
      (create-model! conn model-name)
      :ok)))

(defmethod m/run-command :list-models
  [_ {:keys [datomic-uri] :as opts} arguments]
  (let [conn (d/connect datomic-uri)]
    (pprint/print-table (map (partial apply hash-map) (query-models (d/db conn))))
    :ok))

(defmethod m/run-command :list-model-parameters
  [_ {:keys [datomic-uri model-name] :as opts} arguments]
  (let [conn      (d/connect datomic-uri)
        model     (model-for-name (d/db conn) model-name)]
    (if model
      (-> {}
          (into (d/touch model))
          (dissoc :db/id :model/name :model/type)
          pivot-map
          pprint/print-table)
      (m/argument-error (str "Model named '" model-name "' not found")))))

(defmethod m/run-command :set-model-parameter
  [_ {:keys [datomic-uri model-name] :as opts} [_ parameter-name parameter-value]]
  (let [conn       (d/connect datomic-uri)
        model      (model-for-name (d/db conn) model-name)
        parameter  (keywordize parameter-name)
        value      (Long/parseLong parameter-value)]
    @(d/transact conn [[:db/add (:db/id model) parameter value]])
    :ok))
