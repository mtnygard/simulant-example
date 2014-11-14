(ns simtest.database
  (:require [clojure.java.io :as io]
            [datomic.api :as d]
            [simtest.main :as m]))

(def ^:private migration-tracking-attributes
  [{:db/id          #db/id[:db.part/db]
    :db/ident       :schema/namespace
    :db/valueType   :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity
    :db/index       true
    :db.install/_attribute :db.part/db}
   {:db/id          #db/id[:db.part/db]
    :db/ident       :schema/version
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}])

(defn enable-migration-tracking!
  [conn]
  (or (-> conn d/db (d/entid :schema/namespace))
      @(d/transact conn migration-tracking-attributes)))

(defn schema-versions
  [schema]
  (->> schema
       first
       (group-by #(or (:schema/version %) 0))))

(defn- versions-in-db
  [db nm]
  (set
   (map first (d/q '[:find ?ver :in $ ?ns
                     :where [?e :schema/namespace ?ns]
                            [?e :schema/version ?ver]]
                   db nm))))

(defn new-schema-txs
  "Returns a seq of the transaction maps with versions
   that don't already exist in the database plus
   a transaction map representing the new version itself."
  [db nm resource-schema]
  (let [existing-versions (versions-in-db db nm)]
    (keep (fn [[version schema]]
            (when (nil? (existing-versions version))
              (conj schema
                    {:db/id (d/tempid :db.part/user)
                     :schema/namespace nm
                     :schema/version version})))
          (schema-versions resource-schema))))

(defn load-schemata!
  [conn resource]
  (if-let [schema-def (some-> resource io/resource slurp read-string)]
    (for [[namespace schema] schema-def
          tx                 (new-schema-txs (d/db conn) namespace schema)]
      (if (and namespace schema)
        (try
          @(d/transact conn tx)
          {:result :ok :transaction tx}
          (catch Exception e
            (m/transaction-error e)))))
    (m/missing-resource resource)))

(defn bootstrap!
  "Bootstraps schema the first time, and updates it with new entities
  all subsequent times.

  WARNING: This does not account for updates to the simulant schema,
  only to your schema."
  [uri]
  (let [created? (d/create-database uri)
        conn (d/connect uri)]
    (enable-migration-tracking! conn)
    (for [schema-definition ["simulant/schema.edn" "simtest.edn"]]
      (do (println "Loading " schema-definition)
          (load-schemata! conn schema-definition)))))

(defmethod m/run-command :install-schema
  [_ {datomic-uri :datomic-uri} arguments]
  (let [outcome (bootstrap! datomic-uri)]
    (if (every? #(= :ok %) (map :result (flatten outcome)))
      :ok
      (do
        (m/print-errors outcome)
        :failed))))
