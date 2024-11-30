(ns file-server.http
  (:require [ring.util.response :as resp]))

(defn ok
  ([body]
   (-> (resp/response body)
       (resp/status 200)))
  ([]
   (ok nil)))

(defn accepted
  ([content-location body]
   (-> (resp/response body)
       (resp/status 202)
       (resp/header "Content-Location" content-location)))
  ([content-location]
   (accepted content-location nil)))

(defn not-found [uri-or-page-name]
  (-> (resp/not-found (str "Not found " uri-or-page-name))
      (resp/content-type "text")))

(defn server-busy []
  (resp/status 503))

(defn bad-request
  ([]
   (resp/bad-request {}))
  ([body]
   (resp/bad-request body)))