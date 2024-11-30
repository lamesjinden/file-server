(ns build
  (:require [babashka.fs :as fs]
            [babashka.tasks :refer [clojure]]
            [clojure.string :as s]))

(def bb-dir (-> (fs/parent *file*)
                (fs/parent)
                (str)))

(def target-dir (fs/path bb-dir "target"))

(def timestamp-str (-> (java.text.SimpleDateFormat. "yyyy-MM-dd-HH-mm-ss")
                       (.format (java.util.Date.))))

(defn clean []
  (fs/delete-tree target-dir))

(defn build-file-server []
  (clojure {:dir bb-dir} "-T:file-uberjar uber" (format "{:timestamp-str \"%s\"}" timestamp-str)))

(defn run-dev-file-server [command-line-args]
  (let [args (s/join " " command-line-args)
        command (format "-M:dev-server:dev-file-server:run-dev-file-server %s" args)]
    (clojure {:dir bb-dir} command)))

(defn run-dev-file-server-repl []
  (clojure {:dir bb-dir} "-M:dev-server:file-server:dev-file-server:nrepl-main"))

(defn run-file-server [command-line-args]
  (let [args (s/join " " command-line-args)
        command (format "-M:run-file-server %s" args)]
    (clojure {:dir bb-dir} command)))


