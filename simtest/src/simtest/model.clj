(ns simtest.model
  "Activity stream model"
  (:require [causatum.event-streams :as es]
            [clojure.pprint :refer [print-table]]
            [datomic.api :as d]
            [simtest.main :as m]))

(defn shopper-transitions
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
  [m [from tos]]
  (assoc m from
         [(reduce (fn [v [f t w d]] (merge v {t {:weight w :delay [:random d]}})) {} tos)]))

(defn state-transition-model
  [model-parameters]
  {:graph (reduce edge-graph->causatum-tree {} (group-by first (shopper-transitions model-parameters)))
   :delay-ops {:constant (fn [rtime n] n)
               :random   (fn [rtime n] (inc (rand n)))}})

(defn arrival-time [test-duration]
  (int
   (rand
    (- test-duration 15000))))

(defn initial-state [test-duration]
  {:state :start
   :rtime (arrival-time test-duration)})

(defn state-sequence [model-parameters test-duration]
  (es/event-stream
   (state-transition-model model-parameters)
   [(initial-state test-duration)]))

; ----------------------------------------------------
; Placeholder until model editing via CLI is supported
; ----------------------------------------------------
(def default-model-parameters
  {:search-engine-arrival-rate 75
   :internal-search-rate       60
   :search-success-rate        50
   :start-checkout-rate        4
   :abandon-rate               10
   :product-pareto-index       2
   :category-pareto-index      1})

(defn model-entity
  [name model-parameters]
  (merge model-parameters
         {:db/id      (d/tempid :model)
          :model/name name
          :model/type :model.type/shopping}))

(defn create-model!
  ([conn name]
     (create-model! conn name default-model-parameters))
  ([conn name model-parameters]
     @(d/transact conn [(model-entity name model-parameters)])))

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
    (print-table (map (partial apply hash-map) (query-models (d/db conn))))
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
          print-table)
      (m/argument-error (str "Model named '" model-name "' not found")))))

(defmethod m/run-command :set-model-parameter
  [_ {:keys [datomic-uri model-name] :as opts} arguments]
  (let [conn       (d/connect datomic-uri)
        model      (model-for-name (d/db conn) model-name)
        parameter  (keywordize (first arguments))
        value      (Long/parseLong (second arguments))]
    @(d/transact conn [[:db/add (:db/id model) parameter value]])))
