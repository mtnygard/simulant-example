(ns example-ui.xhr
  (:require [cognitect.transit :as t]
            [goog.events :as events])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType]))


(def ^:private xhr-methods
  {:get    "GET"
   :put    "PUT"
   :post   "POST"
   :delete "DELETE"})

(defn transit
  "Make an XHR call using Transit's JSON encoding"
  [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)
        reader (t/reader :json)
        writer (t/writer :json)]
    (events/listen xhr goog.net.EventType.SUCCESS
                   (fn [e]
                     (if-let [payload (try (t/read reader (.getResponseText xhr))
                                           (catch :default e nil))]
                       (on-complete
                        (t/read reader (.getResponseText xhr))))))
    (. xhr
       (send url (xhr-methods method)
             (when data (t/write writer data))
             #js {"Content-Type" "application/transit+json"}))))
