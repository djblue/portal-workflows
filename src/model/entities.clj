(ns model.entities
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [model.entities.helpers :as h])
  (:import (java.io PushbackReader)))

(defn slurp-edn [resource-name]
  (-> resource-name io/resource io/reader PushbackReader. edn/read))

(def snapshot (slurp-edn "system-snapshot.edn"))

(defn get-entities
  ([entity kvs]
   (get-entities entity kvs nil))
  ([entity kvs _opts]
   (let [{:keys [col]} (h/parse-ent-key entity)]
     (filterv
      #(= kvs (select-keys % (keys kvs)))
      (get snapshot col)))))

(defn get-entity
  ([entity id-or-kvs]
   (get-entity entity id-or-kvs nil))
  ([entity id-or-kvs opts]
   (let [kvs (if (string? id-or-kvs) {:id id-or-kvs} id-or-kvs)]
     (first (get-entities entity kvs opts)))))

(defn dump-db
  ([] snapshot)
  ([version] (slurp-edn (str "system-snapshot-" version ".edn"))))