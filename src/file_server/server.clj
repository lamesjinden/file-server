(ns file-server.server
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as s]
            [file-server.http :as http]
            [hiccup2.core :as html]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as timbre :refer [debug warn error]]))

(defn router [request]
  (cond

    (s/includes? (:uri request) "index")
    (http/ok)

    ;; todo - upload
    ;; todo - file

    :else
    (http/not-found (:uri request))))

(defn create-request-pipeline []
  router)

(defn- server-settings->display-strs [server-settings]
  (cond-> []
    (:ip server-settings)
    (conj (str "IP:\t" (:ip server-settings)))

    (:queue-size server-settings)
    (conj (str "Queue Size:\t" (:queue-size server-settings)))))

(defn- gather-server-settings [application-settings]
  (let [server-settings (select-keys application-settings [:ip :port])]
    (doseq [s (server-settings->display-strs server-settings)]
      (debug s))
    server-settings))

(defn create-server* [application-settings request-pipeline]
  (let [server-settings (merge (gather-server-settings application-settings) {:join? false})
        disposable-ref (atom nil)]
    {:start-fn (fn []
                 (debug "Starting server")
                 (let [disposable (jetty/run-jetty request-pipeline server-settings)]
                   (reset! disposable-ref disposable)
                   (debug "Server started")))
     :stop-fn (fn []
                (debug "Stopping server")
                (.stop @disposable-ref)
                (debug "Server stopped"))}))

(defn create-server [application-settings]
  (let [request-pipeline (create-request-pipeline)
        server (create-server* application-settings request-pipeline)]
    server))
