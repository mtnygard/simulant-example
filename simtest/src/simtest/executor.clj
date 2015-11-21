(ns simtest.executor
  (:require [datomic.api :as d]
            [simtest.main :as m]
            [simulant.sim :as sim]
            [simulant.util :as u]))

(def action-namespaces '[simtest.actions.abandon-checkout
                         simtest.actions.abandon-session
                         simtest.actions.page-request
                         simtest.actions.process-enter
                         simtest.actions.purchase-confirm
                         simtest.actions.start-session])

(defn locate-activity-stream
  [{:keys [conn activity-id] :as ctx}]
  (assoc ctx :test (d/entity (d/db conn) activity-id)))

(defn- init-sim
  [{:keys [conn test process-count clock-multiplier] :as ctx}]
  (let [cbase      (u/gen-codebase)
        sim-def    {:db/id (d/tempid :sim)
                    :source/codebase (:db/id cbase)
                    :sim/processCount process-count}
        sim        (sim/create-sim conn test sim-def)
        action-log (sim/create-action-log conn sim)
        clock      (sim/create-fixed-clock conn sim {:clock/multiplier clock-multiplier})]
    (assoc ctx :sim sim)))

(defn- initialize-actions
  [{:keys [action-namespaces] :as ctx}]
  (doseq [n action-namespaces]
    (require n))
  ctx)

(defn- start-processes
  [{:keys [uri sim] :as ctx}]
  (assoc ctx
    :processes (->> #(sim/run-sim-process uri (:db/id sim))
                    (repeatedly (:sim/processCount sim))
                    (into []))))

(defn- wait-for-completion
  [{:keys [processes] :as ctx}]
  (mapv (fn [p] @(:runner p)) processes)
  ctx)

(defn- continue?
  [ctx]
  (= :continue (:status ctx)))

(defn- report
  [ctx]
  (if (continue? ctx)
    :ok
    (:errors ctx)))

(defmacro pipeline
  [ctx & forms]
  (list* `m/condp-> ctx
         (interleave (repeat `continue?)
                     forms)))

(defn execute-sim
  [datomic-uri activity-id process-count clock-multiplier]
  (pipeline {:uri              datomic-uri
             :conn             (d/connect datomic-uri)
             :activity-id      activity-id
             :process-count    process-count
             :clock-multiplier clock-multiplier}
            locate-activity-stream
            init-sim
            initialize-actions
            start-processes
            wait-for-completion
            report))

(defmethod m/run-command :run-test
  [_
   {:keys [datomic-uri activity-id process-count clock-multiplier] :or
    {process-count 50 clock-multiplier 1.0}
    :as opts} arguments]
  (let [conn (d/connect datomic-uri)]
    (doseq [n action-namespaces]
      (require n))
    (execute-sim conn activity-id process-count clock-multiplier)))
