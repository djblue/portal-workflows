(ns user
  (:require [portal.api :as p]
            [portal.user :as-alias u]))

(p/start {:port 4444})

(.addShutdownHook (Runtime/getRuntime) (Thread. p/close))

(comment
  (add-tap #'p/submit)

  (require '[portal-slides.core :as slides])
  (slides/open {:file "portal-workflows.md" :reset-tap-env true})
  (slides/open {:launcher :vs-code
                :file "thinking-with-portal.md"
                :theme ::u/surprising-blueberry})

  comment)