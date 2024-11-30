(ns file-server.app-server
  (:require [babashka.cli :refer [format-opts]]
            [file-server.cli :as cli]
            [file-server.logging :as logging]
            [file-server.server :as server]
            [taoensso.timbre :as timbre :refer [debug fatal]])
  (:gen-class))

(defn run
  ([default-overrides args]
   (let [cli-errors-ref (atom [])
         cli-error-fn (cli/create-cli-error-fn cli-errors-ref)
         application-settings (cli/gather-application-settings default-overrides args cli-error-fn)]
     (logging/configure-logging application-settings)
     (debug application-settings)
     (cond
       (:help application-settings)
       (println (format-opts {:spec cli/cli-spec}))

       (seq @cli-errors-ref)
       (do
         (doseq [e @cli-errors-ref]
           (fatal e))
         (System/exit 1))

       :else
       (try
         (server/create-server application-settings)
         (catch Exception e
           (fatal e)
           (System/exit 2))))))
  ([args]
   (run {} args)))

(def app-ref (atom nil))

(defn- stop-app []
  (when-let [stop-fn (:stop-fn @app-ref)]
    (stop-fn))
  (shutdown-agents))

(defn -main [& args]
  (when-let [{:keys [start-fn] :as app} (run args)]
    (reset! app-ref app)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #'stop-app))
    (start-fn)))
