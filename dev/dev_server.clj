(ns dev-server
  (:require
   [clojure.pprint]
   [file-server.server :as server]
   [file-server.cli :as cli]))

(set! *warn-on-reflection* true)

(defonce server (atom nil))

(defn start-server []
  (when-let [start-fn (:start-fn @server)]
    (println "starting server")
    (println)

    (start-fn)))

(defn stop-server []
  (when-let [stop-fn  (:stop-fn @server)]
    (println "stopping server")
    (println)

    (stop-fn)
    (reset! server nil)))

(defn create-server [& args]
  (when @server
    (throw (ex-info "Cannot create server when @server is not nil" {})))

  (println)
  (println "== creating dev server ==")
  (let [cli-error-fn (cli/create-cli-error-fn (atom []))
        application-settings (cli/gather-application-settings args cli-error-fn)]
    (println)
    (println "application-settings:")
    (clojure.pprint/pprint application-settings)
    (println)
    (println "initialize dev server app:")
    (println)

    (let [request-pipeline (server/create-request-pipeline)]
      (reset! server (server/create-server* application-settings request-pipeline)))))

(defn -main [& args]
  (apply create-server args))

(comment

  (create-server "-v" "--port=8000")
  (start-server)
  (stop-server)

  ;
  )