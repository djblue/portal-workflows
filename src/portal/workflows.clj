(ns portal.workflows
  (:refer-clojure :exclude [read-string])
  (:require [clojure.java.io :as io]
            [clojure.repl :as repl]
            [clojure.string :as str]
            [clojure.test :as test]
            [emmy.portal :as ep]
            [portal.api :as p]
            [portal.runtime :as rt]
            [portal.runtime.browser :as browser]
            [portal.runtime.fs :as fs]
            [portal.viewer :as v])
  (:import (clojure.lang LineNumberingPushbackReader)
           (java.io StringReader)
           (java.util UUID)
           (java.util Date)))

(def file "slides.md")

(defonce state (atom {:portals []
                      :current-slide 0
                      :slides (fs/slurp (io/resource file))}))

(defmethod browser/-open ::iframe [{:keys [portal server options]}]
  (when-let [state (-> options ::session :options :value)]
    (swap! state update :portals conj
           {:portal  portal
            :server  (select-keys server [:host :port])
            :options (dissoc options :value ::session)})))

(defn- close
  {:command true}
  ([]
   (when-let [state (-> rt/*session* :options :value)]
     (swap! state update :portals empty)))
  ([portal]
   (when-let [state (-> rt/*session* :options :value)]
     (swap! state update :portals
            (fn [portals]
              (filterv
               #(not= (some-> portal :session-id UUID/fromString)
                      (:session-id (:portal %)))
               portals))))))

(defonce nrepl (atom false))

(comment
  (tap> state)
  (reset! state
          {:portals []
           :current-slide 0
           :slides (fs/slurp (io/resource file))})
  (swap! state assoc :slides (fs/slurp (io/resource file)))
  comment)

(defn- read-string [s]
  (-> (StringReader. s)
      (LineNumberingPushbackReader.)
      (read)))

(defn eval-str
  "Eval string as clojure code"
  {:command true
   :shortcuts [["c" "p" "p"]]}
  ([code]
   (eval-str code {}))
  ([code opts]
   (let [open          p/open
         current-slide (some-> rt/*session* :options :value deref :current-slide)
         report        (atom [])
         stdio         (atom [])
         slide-ns      (symbol (str "slide-" (inc current-slide)))
         start         (System/nanoTime)]
     (with-redefs [p/open (fn open*
                            ([] (open* nil))
                            ([options]
                             (let [session rt/*session*]
                               (binding [rt/*session* nil]
                                 (with-redefs [p/open open]
                                   (open (merge
                                          (select-keys (:options session) [:theme])
                                          options
                                          {:launcher ::iframe
                                           :editor :vs-code
                                           ::session session})))))))]
       (-> (try
             (binding [*ns* (create-ns slide-ns)
                       *file* file
                       test/report (fn [value] (swap! report conj value))
                       *out* (PrintWriter-on #(swap! stdio conj {:tag :out :val %}) nil)
                       *err* (PrintWriter-on #(swap! stdio conj {:tag :err :val %}) nil)]
               (refer-clojure)
               {:level  :info
                :result (eval
                         (read-string
                          (str "(do " (str/join (take (:line opts 0) (repeat "\n"))) code "\n)")))})
             (catch Exception ex
               {:level  :error
                :result (Throwable->map ex)}))
           (merge {:code code
                   :ms (quot (- (System/nanoTime) start) 1000000)
                   :time (Date.)
                   :runtime :clj
                   :ns slide-ns
                   :file file
                   :line (:line opts 1)
                   :column (:column opts 1)})
           (cond->
            (seq @report)
             (assoc :report @report)
             (seq @stdio)
             (assoc :stdio @stdio))
           (with-meta
             {:portal.nrepl/eval true
              :portal.viewer/for
              {:code :portal.viewer/code
               :time :portal.viewer/relative-time
               :ms   :portal.viewer/duration-ms}
              :portal.viewer/code {:language :clojure}
              :eval-fn #'eval-str})
           (cond-> @nrepl (doto tap>))
           :result)))))

(defn slide-count [{:keys [slides]}]
  (count (str/split slides #"---\n")))

(defn prev-slide
  {:shortcuts [["["]]}
  ([]
   (some-> rt/*session* :options :value prev-slide))
  ([presentation]
   (swap! @#'portal.runtime/tap-list empty)
   (reset! nrepl false)
   (swap! @#'clojure.core/tapset empty)
   (swap! presentation
          (fn [{:keys [current-slide] :as presentation}]
            (cond-> presentation
              (> current-slide 0)
              (update :current-slide dec)
              :always (assoc :portals []))))))

(defn next-slide
  {:shortcuts [["]"]]}
  ([]
   (some-> rt/*session* :options :value next-slide))
  ([presentation]
   (swap! @#'portal.runtime/tap-list empty)
   (reset! nrepl false)
   (swap! @#'clojure.core/tapset empty)
   (swap! presentation
          (fn [{:keys [current-slide] :as presentation}]
            (cond-> presentation
              (< (inc current-slide) (slide-count presentation))
              (update :current-slide inc)
              :always (assoc :portals []))))))

(defn open [presentation]
  (let [instance (atom nil)]
    (reset!
     instance
     (p/inspect
      presentation
      {#_#_:mode :dev
       #_#_:mode :boot
       #_#_:launcher :vs-code
       :editor :vs-code
       :window-title "Portal Workflows"
       :main `-main}))))

(defn -main []
  (p/register! #'close)
  (p/register! #'eval-str)
  (p/register! #'portal.api/docs)
  (p/register! #'prev-slide)
  (p/register! #'next-slide)
  (rt/register! #'tap> {:command true})
  (open state))

(defn- var->symbol [v]
  (let [m (meta v)]
    (symbol (str (:ns m)) (str (:name m)))))

(defn source
  "Resolve source for var or symbol via `clojure.repl/source`"
  [x]
  (v/code
   (cond
     (var? x) (repl/source-fn (var->symbol x))
     :else    (repl/source-fn x))))

(p/register! #'source)

(comment
  (ep/prepare!)

  (p/open {:mode :dev :launcher :vs-code})
  (-main)

  comment)