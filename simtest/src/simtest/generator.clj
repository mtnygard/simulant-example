(ns simtest.generator
  (:require [clojure.pprint :as pprint]
            [datomic.api :as d]
            [simtest.main :as m]
            [simtest.model :as model]
            [simulant.sim :as sim]
            [simulant.util :as u]))

; ----------------------------------------
; Actions for a single event stream
; ----------------------------------------
(defn- action
  "Create a single, atomic action to record in the activity stream.
   This returns a Datomic entity map."
  [agent state type & {:as extra}]
  (merge
   {:db/id          (d/tempid :test)
    :agent/_actions (u/e agent)
    :action/atTime  (long (:rtime state))
    :action/type    type}
   extra))

(defmulti actions-for-state
  "Return a vector of actions that the runner will execute on
   entry to a particular state. The return value must be a
   collection of Datomic entity maps. The `action` function
   creates these nicely.

   The state used for dispatch here should match one of the
   states in simtest.model/shopper-transitions."
  (fn [_ _ state] (:state state)))

(defmethod actions-for-state :default [])

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
  "Create a sequence of actions for one single agent. This
   uses the model to generate a sequence of states, which
   we turn into actions using the actions-for-state multimethod."
  [model test agent]
  (mapcat (partial actions-for-state model agent)
          (model/state-sequence model (:test/duration test))))

; ----------------------------------------
; Make a population of agents
; ----------------------------------------
(defn- attach-samplers
  "Inject the generators for product and category data. This
   augments the static model data with functions that we can
   use down inside the actions to parameterize an action with
   random inputs."
  [model]
  (assoc model
    :product-sampler (model/product-sampler model)
    :category-sampler (model/category-sampler model)))

(defn- all-agents
  "Create a stream of datomic entities for the agents.
   For this example, all agents are the same type: a shopper.
   This function attaches the activity streams for the agents, but
   take note that the data structure here is just a list of maps.
   The activity streams are related by the :db/id included in each
   map."
  [model test]
  (for [db-id (repeatedly (:test/visitor-count test) #(d/tempid :test))]
    (let [a {:db/id               db-id
             :agent/type          :agent.type/shopper
             :agent/email-address (model/email-address)
             :test/_agents        (u/e test)}]
      (concat [a] (agent-actions model test a)))))

(defn- test-instance
  "Build a test instance from a model and a test definition.
   This should return a datomic entity map."
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
  "This is the callback from Simulant to create a test. A
   test defines the agents and their activity streams."
  [conn model test-def]
  (let [test       (test-instance model test-def)
        test-txr   (d/transact conn [test])
        model      (-> model model-parameters attach-samplers)]
    (doseq [a (all-agents model test)]
      (m/dot a)
      @(d/transact-async conn a))
    (u/tx-ent @test-txr (u/e test))))

;; ----------------------------------------
;; CLI
;; ----------------------------------------

(defn model-for-name
  "Return the model entity that corresponds to model-name."
  [db model-name]
  (d/entity db [:model/name model-name]))

(defn query-activities
  "Return a collection of maps for activity streams in the
  database. The maps have string keys so they are easy to print. Don't
  use this for executions."
  [db]
  (d/q '[:find ?ttn ?tt ?mnn ?mn ?idn ?tid ?agtn (count ?agts)
         :in $ ?ttn     ?mnn     ?idn      ?agtn
         :where
         [?id :test/type ?tid]
         [?tid :db/ident ?tt]
         [?id :model/name ?mn]
         [?id :test/agents ?agts]]
       db "Type" "Model Name" "DB ID" "# Agents"))

(defmethod m/run-command :make-activity
  "As invoked from the command line, make a new activity stream."
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

(defmethod m/run-command :list-activities
  "As invoked from the command line, list activity streams in the
  database."
  [_ {:keys [datomic-uri] :as opts} arguments]
  (let [conn (d/connect datomic-uri)
        acts (query-activities (d/db conn))]
    (if-not (empty? acts)
      (pprint/print-table (map (partial apply hash-map) acts))
      (println "No activities"))
    :ok))
