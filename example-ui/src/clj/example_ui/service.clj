(ns example-ui.service
  (:require [clojure.java.io :as io]
            [cognitect.transit :as t]
            [example-ui.dev :refer [is-dev? inject-devmode-html start-figwheel]]
            [io.pedestal.http :as bootstrap]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.ring-middlewares :as ring-middlewares]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.util.response :as ring-resp]))

(deftemplate main-page-view (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defn index-page
  [request]
  (ring-resp/response (apply str (main-page-view))))

(defn init
  [request]
  (ring-resp/response {:models [{:model/name "high traffic"    :model/id 1}
                                {:model/name "high conversion" :model/id 2}
                                {:model/name "CCVS down"       :model/id 3}]}))

(defroutes routes
  ;; Defines "/" and "/about" routes with their associated :get handlers.
  ;; The interceptors defined after the verb map (e.g., {:get home-page}
  ;; apply to / and its children (/about).
  [[["/" {:get index-page} ^:interceptors [bootstrap/html-body]]
    ["/init"  {:get init} ^:interceptors [bootstrap/transit-body]]]])

;; Consumed by bar.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ::bootstrap/routes routes

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::bootstrap/type :jetty

              ;;::bootstrap/host "localhost"
              ::bootstrap/port 8080})
