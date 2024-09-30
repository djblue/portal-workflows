(ns utilis.core)

(defn- run-try-body [or-v body-fn]
  (try
    (body-fn)
    (catch Throwable _e
      or-v)))

(defmacro try-or [or-v & body]
  `(#'run-try-body ~or-v #(do ~@body)))

(defmacro try-or-nil [& body]
  `(try-or nil ~@body))

(defn ->vec [maybe-v]
  (cond (vector? maybe-v) maybe-v
        (sequential? maybe-v) (vec maybe-v)
        :else [maybe-v]))