(ns simtest.executor
  (:require [simtest.main :as m]))

(defmethod m/run-command :run-test
  [_ options]
  :error)
