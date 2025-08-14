(ns graph.viz
  (:require ["@viz-js/viz/dist/viz.cjs" :refer [instance]]
            ["react" :as react]
            [clojure.walk :as walk]
            [portal.colors :as c]
            [portal.ui.api :as p]
            [portal.ui.inspector :as ins]
            [portal.ui.styled :as d]
            [portal.ui.theme :as theme]))

(defn- use-viz []
  (let [[viz set-viz!] (react/useState)]
    (react/useEffect
     (fn []
       (.then (instance) set-viz!))
     #js [])
    viz))

(def rgba-re #"^rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*(\d+(?:\.\d+)?))?\)$")

(defn rgba->hex [x]
  (if-let [[_ r g b a] (re-matches rgba-re x)]
    (let [r (js/parseInt r)
          g (js/parseInt g)
          b (js/parseInt b)
          a (js/parseFloat a)]
      (str "#"
           (.toString r 16)
           (.toString g 16)
           (.toString b 16)
           (.substring (.toString (Math/round (* a 255)) 16) 0 2)))
    x))

(defn inspect-dot [s]
  (let [viz (use-viz)
        ref (react/useRef)
        theme (theme/use-theme)
        bg (ins/get-background)
        fg (ins/get-background2)
        colors {:blue (::c/boolean theme)
                "blue" (::c/boolean theme)
                :green (::c/string theme)
                "green" (::c/string theme)
                :red (::c/exception theme)
                "red" (::c/exception theme)}
        s (if (string? s)
            s
            (-> (walk/postwalk-replace colors s)
                (update :graphAttributes merge {:bgcolor bg})
                (update :nodeAttributes merge {:color (rgba->hex (::c/border theme))
                                               :fontsize (:font-size theme)
                                               :shape :box
                                               :fillcolor fg
                                               :fontcolor (::c/text theme)
                                               :style :filled})))]
    (react/useEffect
     (fn []
       (when viz
         (when-let [el (.-current ref)]
           (let [graph (clj->js s)]
             (.appendChild el (.renderSVGElement viz graph))))))
     #js [ref viz])
    [:<>
     #_(pr-str x)
     [d/div {:ref ref
             :on-click (fn [e]
                         (js/console.log (take 10 (iterate #(.-parentNode %) (.-target e)))))
             :style {:border [1 :solid (::c/border theme)]
                     :background bg}}]]))

(p/register-viewer!
 {:predicate any?
  :component inspect-dot
  :name      ::dot})