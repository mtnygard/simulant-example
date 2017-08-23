(ns create-data
  "This namespace creates some sample data files that are used later when creating activities.
   You only need to load this namespace one time to create the files,
  but you do need to load this before trying to generate activity.

   It just lets me avoid committing long meaningless text files to the repository."
  (:require [clojure.data.generators :as gen]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]))

(def sku-length         9)
(def category-length    11)
(defn digit []          (char (gen/uniform 48 57)))
(defn sku []            (gen/string digit sku-length))
(defn sku-list [n]      (take n (repeatedly sku)))
(defn category []       (str "cat" (gen/string digit (- category-length 3))))
(defn category-list [n] (take n (repeatedly category)))
(defn pprint-all [c]    (with-out-str (binding [*print-length* nil] (pprint c))))

(defn write-list! [f c]
  (with-open [w (io/writer f)]
    (.write w "(")
    (doseq [elt c]
      (.write w (pr-str elt))
      (.write w "\n"))
    (.write w ")")))

;; Create SKU file
(time (write-list! "resources/shop/sku.edn" (sku-list 250000)))

;; Create categories file
(time (write-list! "resources/shop/category.edn" (category-list 6000)))

:ok
