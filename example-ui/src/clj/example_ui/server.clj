(ns example-ui.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET defroutes]]
            [compojure.handler :refer [api]]
            [compojure.route :refer [resources]]
            [environ.core :refer [env]]
            [example-ui.dev :refer [is-dev? inject-devmode-html start-figwheel]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :as reload]))

(deftemplate page (io/resource "index.html") [] [:body] (if is-dev? inject-devmode-html identity))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/*" req (page)))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (api #'routes))
    (api routes)))

(defn run [& [port]]
  (defonce ^:private server
    (do
      (when is-dev? (start-figwheel))
      (let [port (Integer. (or port (env :port) 10555))]
        (print "Starting web server on port" port ".\n")
        (run-jetty http-handler {:port port :join? false}))))
  server)

(defn -main [& [port]]
  (run port))
