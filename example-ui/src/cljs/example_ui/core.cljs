(ns example-ui.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [figwheel.client :as figwheel :include-macros true]
            [cljs.core.async :as async :refer [put! chan <!]]
            [weasel.repl :as weasel]
            [example-ui.xhr :as xhr])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce app-state (atom {:heading          "Simulant-example Dashboard"
                          :current-activity :models
                          :navigation       [["Models"        :models]
                                             ["Simulations"   :sims]
                                             ["Results"       :results]
                                             ["Investigation" :investigate]]
                          :models           [{:model/name "high traffic"    :model/id 1}
                                             {:model/name "high conversion" :model/id 2}
                                             {:model/name "CCVS down"       :model/id 3}]}))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn handle-change [e data edit-key owner]
  (om/transact! data edit-key (fn [_] (.. e -target -value))))

(defn end-edit [data edit-key text owner cb]
  (om/set-state! owner :editing false)
  (om/transact! data edit-key (fn [_] text) :update)
  (when cb
    (cb text)))

(defn editable [data owner {:keys [edit-key on-edit] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false})
    om/IRenderState
    (render-state [_ {:keys [editing]}]
      (let [text (get data edit-key)]
        (dom/li nil
                (dom/span #js {:style (display (not editing))} text)
                (dom/input
                 #js {:style     (display editing)
                      :value     text
                      :onChange  #(handle-change % data edit-key owner)
                      :onKeyDown #(when (= (.-key %) "Enter")
                                    (end-edit data edit-key text owner on-edit))
                      :onBlur    #(when (om/get-state owner :editing)
                                    (end-edit data edit-key text owner on-edit))})
                (dom/button
                 #js {:style (display (not editing))
                      :onClick #(om/set-state! owner :editing true)}
                 "Edit"))))))

(defn on-edit [id title]
  (.log js/console "Pretend we're sending a request to the server"))

(defn create-model [models owner]
  (let [model-id-el   (om/get-node owner "model-id")
        model-id      (.-value model-id-el)
        model-name-el (om/get-node owner "model-name")
        model-name    (.-value model-name-el)
        new-model     {:model/id model-id :model/name model-name}]
    (om/transact! models [] #(conj % new-model) [:create new-model])
    (set! (.-value model-id-el) "")
    (set! (.-value model-name-el) "")))

(defn models-view [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:id "models"}
               (dom/h2 nil "Models")
               (apply dom/ul nil
                      (map
                       #(om/build editable % {:opts {:edit-key :model/name}})
                       (:models app)))
               (dom/div nil
                        (dom/label nil "ID:")
                        (dom/input #js {:ref "model-id"})
                        (dom/label nil "Name:")
                        (dom/input #js {:ref "model-name"})
                        (dom/button #js {:onClick (fn [e] (create-model (:models app) owner))}
                                    "Add"))))))

(defn main
  []
  (let [tx-chan (chan)
        tx-pub-chan (async/pub tx-chan (fn [_] :txs))]
    (om/root models-view app-state
             {:target    (.-body js/document)
              :shared    {:tx-chan tx-pub-chan}
              :tx-listen (fn [tx-data root-cursor]
                           (put! tx-chan [tx-data root-cursor]))})
    (xhr/transit {:method      :get
                  :url         "/init"
                  :on-complete #(om/transact! app-state (fn [_] %))})))
