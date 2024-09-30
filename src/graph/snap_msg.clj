(ns graph.snap-msg
  (:require [model.entities.helpers :as e]
            [utilis.core :as u]))

(defn- ->node [id attrs] {:name id :attributes attrs})

(defn- ->edge [a b attrs] {:tail a :head b :attributes attrs})

(defn- success? [log]
  (case (:command log)
    "send-email" (get-in log [:result :data :success?])
    nil))

(defn ->entity-edges
  ([entity]
   (->entity-edges entity nil))
  ([{:keys [id] :as entity} {:keys [links]}]
   (let [edges (e/only-ent-and-ent-ids entity)]
     (for [[k eids?] edges
           eid (u/->vec eids?)
           :when (and eid (or (nil? links) (links k)))]
       (->edge id eid {:color :green})))))

(defn ->entity-nodes
  ([entity]
   (->entity-nodes entity nil))
  ([{:keys [id] :as entity} {:keys [links]}]
   (let [nodes (e/only-ent-and-ent-ids entity)]
     (for [[k eids?] nodes
           eid (u/->vec eids?)
           :when (and eid (or (nil? links) (links k)))]
       (->node eid {:label (str (name (e/snap-ent-type k)) "-" (subs id 0 5))})))))

(defn log->viz [log]
  {:nodes (into
           [(->node (:id log)
                    {:label (str (:command log) "-" (subs (:id log) 0 5))
                     :shape :box
                     :color (cond
                              (false? (success? log)) :red
                              #_#_(true? (success? log)) :green
                              :else :black)})]
           (->entity-nodes log {:links #{:snap-form/id :snap-patient/id}}))
   :edges (->entity-edges log {:links #{:snap-form/id :snap-patient/id}})})

(defn logs->viz [logs]
  (reduce
   (fn [out log]
     (merge-with
      into
      out
      (log->viz log)
      ;; (when-let [oid (:origination-id log)]
      ;;   {:edges [(->edge oid (:id log) {:color :blue})]})
      (when-let [cid (:correlation-id log)]
        {:edges [(->edge cid (:id log) {:color :red})]})))
   {:nodes #{} :edges #{}}
   logs))

(defn ->digraph [x] x)