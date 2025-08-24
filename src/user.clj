(ns user
  (:require [portal.api :as p]))

(p/start {:port 4444})

(comment
  (require '[portal-slides.core :as slides])
  (slides/open {:file "slides.md" :reset-tap-env true})

  comment)