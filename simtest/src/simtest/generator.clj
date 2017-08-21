(ns simtest.generator
  (:require [clojure.pprint :as pprint]
            [datomic.api :as d]
            [simtest.main :as m]
            [simtest.model :as model]
            [simulant.sim :as sim]
            [simulant.util :as u]))

(defn- refresh
  [db ent]
  (d/entity db (:db/id ent)))

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
  (into [] (mapcat (partial actions-for-state model agent)
                   (model/state-sequence model (:test/duration test)))))

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

(defn- create-agent
  "Create a Datomic entity map for a single agent, along with its
   activity stream.

   For this example, all agents are the same type: a shopper.
   This function attaches the activity stream for the agent, but
   take note that the data structure here is just a list of maps.
   The activity streams are related by the :db/id included in each
   map."
  [model test db-id]
  (let [a {:db/id               db-id
           :agent/type          :agent.type/shopper
           :agent/email-address (model/email-address)
           :test/_agents        (u/e test)}]
    (conj (agent-actions model test a) a)))

(defn- test-instance
  "Build a test instance from a model and a test definition.
   This should return a datomic entity map."
  [model test codebase]
  (assoc test
    :test/type       :test.type/shopping
    :model/_tests    (u/e model)
    :source/codebase (u/e codebase)))

(defn- model-parameters
  "Do any conversion needed from the database format
   of the model to the in-memory representation.

   In this case, convert the model entity into a
   regular map so we can assoc and dissoc to it later."
  [model]
  (into {} (d/touch model)))


;; This is the callback from Simulant to create a test. A
;; test defines the agents and their activity streams
(defmethod sim/create-test :model.type/shopping
  [conn model test-def]
  (let [codebase   (u/gen-codebase)
        test       (test-instance model test-def codebase)
        test-txr   (d/transact conn [test codebase])
        test-real  (u/tx-ent @test-txr (u/e test))
        model      (-> model model-parameters attach-samplers)]
    (doseq [agent-id (repeatedly (:test/visitor-count test) #(d/tempid :test))]
      @(d/transact conn (m/dot (create-agent model test-real agent-id))))
    (refresh (d/db conn) test-real)))

(defn empty-test
  [{:keys [number-of-visitors test-duration] :or
    {number-of-visitors 10000 test-duration 10}}]
  {:db/id              (d/tempid :test)
   :test/visitor-count number-of-visitors
   :test/duration      (* 1000 60 test-duration)})

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
  (d/q '[:find ?ttn ?tt ?idn ?id  ?mnn ?mn ?durn ?dur ?agtn (count ?agt)
         :in $ ?ttn     ?idn      ?mnn     ?durn      ?agtn
         :where
         [?id  :test/type ?x]
         [?x   :db/ident ?tt]
         [?id  :test/agents ?agt]
         [?id  :test/duration ?dur]
         [?mid :model/tests ?id]
         [?mid :model/name  ?mn]]
       db "Type" "DB ID" "Model Name" "Duration" "# Agents"))

(defmethod m/run-command :make-activity
  [_ {:keys [datomic-uri model-name] :as opts} arguments]
  (let [conn  (d/connect datomic-uri)]
    (if-let [model (model-for-name (d/db conn) model-name)]
      (do
        (sim/create-test conn model (empty-test opts))
        :ok)
      (m/missing-model (str "Model " model-name " must exist to create a test.")))))

(defmethod m/run-command :list-activities
  [_ {:keys [datomic-uri] :as opts} arguments]
  (let [conn (d/connect datomic-uri)
        acts (query-activities (d/db conn))]
    (if-not (empty? acts)
      (pprint/print-table (map (partial apply hash-map) acts))
      (println "No activities"))
    :ok))
