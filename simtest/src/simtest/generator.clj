(ns simtest.generator
  (:require [clojure.data.generators :as gen]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [datomic.api :as d]
            [simtest.main :as m]
            [simtest.model :as model]
            [simulant.sim :as sim]
            [simulant.util :as u])
  (:import [org.apache.commons.math3.distribution ParetoDistribution]))

; ----------------------------------------
; Actions for a single event stream
; ----------------------------------------
(defn- action
  [agent state type & {:as extra}]
  (merge
   {:db/id          (d/tempid :test)
    :agent/_actions (u/e agent)
    :action/atTime  (long (:rtime state))
    :action/type    type}
   extra))

(defmulti actions-for-state (fn [_ _ state] (:state state)))

(defmethod actions-for-state :start
  [model agent state]
  [(action agent state :action.type/start-session)])

(defmethod actions-for-state :home
  [model agent state]
  [(action agent state :action.type/page-request :page/type :home)])

(defmethod actions-for-state :product-page
  [model agent state]
  [(action agent state :action.type/page-request :page/type :product)])

(defmethod actions-for-state :search
  [model agent state]
  [(action agent state :action.type/page-request :page/type :search)])

(defmethod actions-for-state :abandon
  [model agent state]
  [(action agent state :action.type/abandon-session)])

(defmethod actions-for-state :category
  [model agent state]
  [(action agent state :action.type/page-request :page/type :category :category ((:category-sampler model)))])

(defmethod actions-for-state :product-page
  [model agent state]
  [(action agent state :action.type/page-request :page/type :product-page :sku ((:product-sampler model)))])

(defmethod actions-for-state :purchase-start
  [model agent state]
  [(action agent state :action.type/process-enter :process/type :checkout)])

(defmethod actions-for-state :abandon-checkout
  [model agent state]
  [(action agent state :action.type/abandon-checkout)])

(defmethod actions-for-state :purchase-confirm
  [model agent state]
  [(action agent state :action.type/purchase-confirm)])

(defn agent-actions
  [model test agent]
  (mapcat (partial actions-for-state model agent)
          (model/state-sequence model (:test/duration test))))

; ----------------------------------------
; Make a population of agents
; ----------------------------------------
(defn- attach-samplers
  [model]
  (assoc model
    :product-sampler (product-sampler model)
    :category-sampler (category-sampler model)))

(defn- all-agents
  [model test]
  (for [db-id (repeatedly (:test/visitor-count test) #(d/tempid :test))]
    (let [a {:db/id               db-id
             :agent/type          :agent.type/shopper
             :agent/email-address (email-address)
             :test/_agents        (u/e test)}]
      (concat [a] (agent-actions model test a)))))

(defn- test-instance
  [model test]
  (assoc test
    :test/type      :test.type/shopping
    :model/_tests   (u/e model)))

(defn- model-parameters
  "Do any conversion needed from the database format
   of the model to the in-memory representation.

   In this case, convert the model entity into a
   regular map so we can assoc and dissoc to it later."
  [model]
  (into {} (d/touch model)))

(defmethod sim/create-test :model.type/shopping
  [conn model test-def]
  (let [test       (test-instance model test-def)
        test-txr   (d/transact conn [test])
        model      (-> model model-parameters attach-samplers)]
    (doseq [a (all-agents model test)]
      (m/dot a)
      @(d/transact-async conn a))
    (u/tx-ent @test-txr (u/e test))))

(defn model-for-name
  "Return the model entity that corresponds to model-name."
  [db model-name]
  (d/entity db [:model/name model-name]))

(defmethod m/run-command :make-activity
  [_
   {datomic-uri        :datomic-uri
    number-of-visitors :number-of-visitors
    duration           :test-duration
    model-name         :model-name :as opts}
   arguments]
  (if-not number-of-visitors
    (m/required-argument :number-of-visitors)
    (if-not duration
      (m/required-argument :duration)
      (let [conn            (d/connect datomic-uri)
            test-definition {:db/id (d/tempid :test)
                             :test/visitor-count number-of-visitors
                             :test/duration      (* 1000 60 duration)}
            model           (model-for-name (d/db conn) model-name)]
        (if model
          (do
            (sim/create-test conn model test-definition)
            :ok)
          (m/missing-model (str "Model " model-name " must exist to create a test.")))))))
