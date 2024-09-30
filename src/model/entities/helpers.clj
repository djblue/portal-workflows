(ns model.entities.helpers
  (:require [clojure.string :as str]
            [utilis.core :as util]))

(defn parse-ent-key
  [ent-key]
  (util/try-or-nil
   (let [;; eg. :snap-patient & snap-patient/id are the same collection
         col-part (or (util/try-or-nil (namespace ent-key))
                      (name ent-key))
         col (keyword (last (re-matches #"(snap-)?(.*)" col-part)))
         k (if (and (keyword? ent-key) (namespace ent-key))
             (name ent-key)
             :id)]
     {:col col
      :prop (keyword k)})))

(defn snap-ent-type
  ":snap-patient -> :snap-patient
   :snap-patient/id -> :snap-patient
   :patient -> :snap-patient"
  [k]
  (let [{:keys [col]} (parse-ent-key k)]
    (keyword (str "snap-" (name col)))))

(defn snap-entity-id-key? [k]
  (and
   (keyword? k)
   (namespace k)
   (str/starts-with? (namespace k) "snap-")
   (= "id" (name k))))

(defn snap-entity-key [k]
  (and (keyword? k)
       (not (namespace k))
       (str/starts-with? (name k) "snap-")))

(defn only-ent-and-ent-ids [m]
  (into {} (filter (comp (some-fn snap-entity-key snap-entity-id-key?) key)) m))