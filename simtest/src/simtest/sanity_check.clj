(ns simtest.sanity-check
  (:require [simtest.model :as m]))

(def count-vals (map (fn [[k v]] {k (count v)})))

(defn generate-trials
  [trials model-parameters test-duration]
  (repeatedly trials
              #(m/state-sequence model-parameters test-duration)))

(defn histogram-by
  [slicer dim coll]
  (->> coll
       (group-by (comp dim slicer))
       (into {} count-vals)))

(defn start-state-distribution
  [trials model-parameters test-duration]
  (histogram-by second :state (generate-trials trials model-parameters test-duration)))

(defn terminal-state-distribution
  [trials model-parameters test-duration]
  (histogram-by last :state (generate-trials trials model-parameters test-duration)))

(defn conversion-rate
  [trials model-parameters test-duration]
  (let [hist (terminal-state-distribution trials model-parameters test-duration)]
    (double (/ (:purchase-confirm hist) (reduce + (vals hist))))))
