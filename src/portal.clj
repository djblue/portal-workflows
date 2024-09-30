(ns portal
  (:require [clojure.datafy :as d]
            [clojure.instant :as i]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [graph.snap-msg :as g]
            [model.entities :as e]
            [model.entities.core :as-alias ec]
            [model.entities.helpers :as h]
            [portal.api :as p]
            [portal.viewer :as v]
            [taoensso.timbre :as log]
            [utilis.core :refer [->vec]]))


(defmacro with-src-aliases [src & body] `(do ~@body))

(defmacro with-snap-db-override [db & body] `(do ~@body))

(defn log->portal [{:keys [level ?err msg_ timestamp_ ?ns-str ?file context ?line]}]
  (merge
   (when ?err
     {:error (d/datafy ?err)})
   (when-let [ts (force timestamp_)]
     {:time (i/read-instant-date ts)})
   {:level   level
    :ns      (symbol (or ?ns-str ?file "?"))
    :line    (or ?line 1)
    :column  1
    :result  (force msg_)
    :runtime :clj}
   context))

(defonce logs (atom '()))

(defn append-log
  "Accumulate a rolling log of 100 entries."
  [log]
  (swap! logs
         (fn [logs]
           (take 100 (conj logs log)))))

(defn setup []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. #(try (p/close) (catch Exception _))))
  (reset! logs '())
  (log/merge-config!
   {:appenders
    {:memory {:enabled? true :fn (comp append-log log->portal)}}}))

(defn on-load []
  (try
    (p/eval-str "(require 'graph.viz)")
    (catch Exception e
      (tap> e))))

(defn open [opts]
  (p/open
   (merge {:port 23456
           :editor :vs-code
           :on-load #'on-load
           :launcher :auto}
          opts)))

(defn open-logs
  ([]
   (open-logs nil))
  ([opts]
   (open (assoc opts
                :window-title "logs"
                :value logs))))

(declare nav)

(def date-times
  #{:_modified
    :_created
    :created
    :time
    :end
    :start
    :collection
    :transaction
    :visit-date-time
    :start-date
    :end-date
    :date-time
    :effective-date})

(def tables
  #{:problems
    :medications
    :insurances
    :immunizations
    :vital-signs
    :allergies
    :family-history
    :observations})

(def default-viewers
  (merge
   (zipmap date-times (repeat ::v/date-time))
   (zipmap tables (repeat ::v/table))))

(defn patch-meta-1 [m value]
  (if-not (map? value)
    value
    (let [viewers (select-keys default-viewers (keys value))]
      (vary-meta
       value
       merge
       {'clojure.core.protocols/nav #'nav}
       (when (seq viewers) {::v/for (merge viewers (::v/for (meta value)))})
       m))))

(defn patch-meta
  ([value]
   (patch-meta value nil))
  ([value m]
   (walk/postwalk (partial patch-meta-1 m) value)))

(defn message-log->graph [message-log]
  (v/default (g/->digraph (g/logs->viz message-log)) :graph.viz/dot))

(p/register! #'message-log->graph)

(defn nav [coll k v]
  (try
    (let [ent-type (some-> k namespace h/snap-ent-type)
          m  (select-keys (meta coll) [:model.entities.core/snap-db :model.entities.core/conn])
          {:model.entities.core/keys [snap-db conn]} (meta coll)
          aliases (when conn {:primary (get {:dev :develop :prod :prod-ro} conn)})]
      (patch-meta
       (with-src-aliases aliases
         (with-snap-db-override
           (or snap-db :snap)
           (cond
             (and ent-type (= "id" (name k)))
             (e/get-entities (h/snap-ent-type (namespace k)) {:id (first (->vec v))})

             (= :id k)
             (let [type (-> coll meta :entity-type)]
               (e/get-entities :coref
                               (cond-> {:canonical-id v}
                                 type
                                 (assoc :type (str/replace (name type) #"snap-" "")))))

             (= :system-key k)
             (e/get-entities :coref
                             (cond-> {:id-data.system-key v}
                               (:type coll)
                               (assoc :type (:type coll))))

             (= :alias k)
             (e/get-entities :coref
                             (cond-> {:id v}
                               (:type coll)
                               (assoc :type (:type coll))))

             (and (= :canonical-id k) (:type coll))
             (e/get-entity (:type coll) {:id v})

             (= :parent k)
             (e/get-entity :org {:id v})

             (= :redox/log-id k)
             (assoc
              (e/get-entity :message-log {:command "received-message" :data.meta.logs.id v})
              :snap-entities (patch-meta (e/get-entities :coref {:redox/log-id v}) m))

             (= :correlation-id k)
             (e/get-entity :message-log {:id v})

             (= :visit-number k)
             (e/get-entities :coref {:id-data.system-key v :type :visit})

             (= :origination-id k)
             (let [message-log (e/get-entities :message-log {:origination-id v})]
               (vary-meta message-log assoc :graph
                          (message-log->graph message-log)))

             :else v)))
       m))
    (catch Exception e (v/ex (Throwable->map e)))))

(defn submit [value]
  (let [value (patch-meta value)]
    (when (:portal.nrepl/eval (meta value))
      (when-let [stdio (:stdio value)]
        (p/submit stdio))
      (doseq [assert (:report value)
              :when (#{:fail :error} (:type assert))]
        (p/submit assert)))
    (cond
      (instance? Throwable value)
      (p/submit (Throwable->map value))

      (:portal.nrepl/eval (meta value))
      (do (append-log value)
          (tap> (:result value)))

      (:utilis.log.preload/timbre (meta value))
      (append-log value)

      :else (p/submit value))))

;; (defmethod server/route [:post "/submit"] [request]
;;   (submit (#'server/body request))
;;   {:status  204
;;    :headers {"Access-Control-Allow-Origin" "*"}})

(defn open-taps
  ([]
   (open-taps nil))
  ([opts]
   (add-tap #'submit)
   (open (assoc opts :window-title "taps"))))

(p/register! #'tap>)

(comment
  (setup)
  (open-logs)
  (open-taps))

