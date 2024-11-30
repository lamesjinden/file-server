(ns file-server.logging
  (:require [taoensso.timbre :as timbre]))

(def default-log-level :error)

(defn configure-logging [application-settings]
  (let [verbose (:verbose application-settings)
        min-level (cond
                    (>= (count verbose) 3) :trace
                    (= (count verbose) 2) :debug
                    (= (count verbose) 1) :info
                    :else default-log-level)]
    (timbre/set-min-level! min-level)))
