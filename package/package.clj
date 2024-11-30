(ns package
  (:require [clojure.tools.build.api :as b]))

(def artifact-name "file-job-server")
(def version "0.1.0-SNAPSHOT")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"
                            :aliases [:file-server]}))
(defn get-uber-file-path [timestamp-str]
  (format "target/%s-%s.%s.jar"
          artifact-name
          version
          timestamp-str))

(defn uber [{:keys [timestamp-str] :as _args}]
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis     basis
                  :src-dirs  ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file (get-uber-file-path timestamp-str)
           :basis     basis
           :main      'file-server.app-server}))
