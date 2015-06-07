(ns example-ui.db
  "Datomic bootstrap and Datomic + Pedestal interceptor"
  (:require [environ.core :refer [env]]
            [datomic.api :as d]
            [io.pedestal.interceptor :refer [interceptor]]
            [environ.core :refer [env]]))

(defonce uri (env :datomic-uri (str "datomic:mem://" (d/squuid))))

(def insert-datomic
  "Provide a Datomic conn and db in all incoming requests"
  (interceptor
   {:name ::insert-datomic
    :enter (fn [context]
             (let [conn (d/connect uri)]
               (-> context
                   (assoc-in [:request :conn] conn)
                   (assoc-in [:request :db] (d/db conn)))))}))


(defn e->m [e] (select-keys e (keys e)))
