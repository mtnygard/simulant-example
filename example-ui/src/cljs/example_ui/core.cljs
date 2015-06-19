(ns example-ui.core
  (:require [kioo.om :as k :include-macros true]
            [kioo.core :as kcore]
            [om.core :as om :include-macros true]
            [om.next :as omn :include-macros true]
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
                                             ["Investigation" :investigate]]}))

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

(defn sim-item-multiselect [contents owner {:keys [title create-title label-key] :as opts}]
  (reify
    om/IRender
    (render [_]
      (dom/ul #js {:className "multiselect"}
              (dom/li #js {:className "title"} title)
              (dom/li #js {:className "options"}
                      (apply dom/ul nil
                             (dom/li #js {:className "actionNew"} create-title)
                             (map #(dom/li nil (get % label-key)) contents)))))))

(k/defsnippet filter-element "design.html" [:#filterElement]
  [{:keys [elements title create-title]}]
  {[:li#all]         (kcore/content title)
   [:li.actionNew]   (kcore/content create-title)
   [:li.options :ul] (kcore/content (map (fn [o] (dom/li nil o)) elements))}
  )

(k/defsnippet filter-bar "design.html" [:#filterBar]
  [{:keys [models scripts captures]}]
  {[:.row] (kcore/substitute
            (map filter-element
                 [{:elements (map :model/name models)
                   :title "All models"
                   :create-title "New model"}
                  {:elements (map :db/id scripts)
                   :title "All scripts"
                   :create-title "Generate script"}
                  {:elements (map :db/id captures)
                   :title "All captures"
                   :create-title "Run simulation"}]))})

(defn kioo-snippets
  [app owner]
  (om/component filter-bar))


(omn/defui
  static IQuery
  (query [_])
  static IQueryParams
  (params [_])

  )

(defn main
  []
  (om/root kioo-snippets app-state {:target (. js/document getElementById "filterBar")})
  (xhr/transit {:method      :get
                :url         "/init"
                :on-complete #(swap! app-state (fn [data] (merge % data)))}))
