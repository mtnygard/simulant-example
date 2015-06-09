(ns example-ui.service
  (:require [clojure.java.io :as io]
            [datomic.api :as d]
            [example-ui.db :as db]
            [example-ui.dev :refer [is-dev? inject-devmode-html]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.util.response :as ring-resp]
            [simtest.generator :as generator]
            [simtest.model :as model]))

(deftemplate main-page-view (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn index-page
  [request]
  (ring-resp/response (apply str (main-page-view))))

(defn model-entities
  [db]
  (map first (model/models db)))

(defn script-entities
  [db]
  (map first (generator/activities db)))

(defn init
  [request]
  (let [db (:db request)]
    (ring-resp/response {:models (model-entities db)
                         :scripts (script-entities db)})))

(defroutes routes
  [[["/" ^:interceptors [bootstrap/html-body] {:get index-page}
     ^:interceptors [bootstrap/transit-body db/insert-datomic]
     ["/init"  {:get init}]]]])

(def service {:env :prod
              ::bootstrap/routes routes

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::bootstrap/type :jetty

              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080})
