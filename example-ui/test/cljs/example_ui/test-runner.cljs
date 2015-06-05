(ns example-ui.test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [example-ui.core-test]))

(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
       (run-tests
        'example-ui.core-test))
    0
    1))
