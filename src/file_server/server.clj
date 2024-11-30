(ns file-server.server
  (:require
   [babashka.fs :as fs]
   [clojure.string :as s]
   [file-server.http :as http]
   [hiccup2.core :as html]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.multipart-params :as multipart]
   [ring.middleware.multipart-params.temp-file]
   [ring.util.mime-type :refer [ext-mime-type]]
   [taoensso.timbre :as timbre :refer [debug]])
  (:import [java.net URLDecoder URLEncoder]))

;; todo:
;;; 1. streaming static file with RANGE header support
;;;;  * investigate use of ring-range-middleware (https://clojars.org/ring-range-middleware)
;;; 2. ~~streaming upload support~~
;;;;  * ~~investigate use of ring.middleware.multipart-params~~

(defn- file-link
  "Get HTML link for a file/directory in the given dir."
  [dir f]
  (let [rel-path (fs/relativize dir f)
        ending (if (fs/directory? f) "/" "")
        names (seq rel-path)
        enc-names (map #(URLEncoder/encode (str %)) names)]
    [:a {:href (str "/" (s/join "/" enc-names) ending)}
     (str rel-path ending)]))

(defn- index [dir f]
  (let [files (map #(file-link dir %)
                   (fs/list-dir f))]
    {:body (-> [:html
                [:head
                 [:meta {:charset "UTF-8"}]
                 [:title (str "Index of `" f "`")]
                 [:style "body { font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 20px; }"
                  "form { background: #f4f4f4; padding: 20px; border-radius: 5px; }"
                  "input[type='file'] { margin-bottom: 10px; }"
                  "input[type='submit'] { background-color: #4CAF50; color: white; padding: 10px 15px; border: none; border-radius: 4px; cursor: pointer; }"]]
                [:body
                 [:div
                  [:h1 "File Upload"]
                  [:form {:action "~/" :method "post" :enctype "multipart/form-data"}
                   [:input {:type "file" :name "file" :required true}]
                   [:input {:type "submit" :value "Upload File"}]]]
                 [:div
                  [:h1 "Index of " [:code (str f)]]
                  [:ul
                   (for [child files]
                     [:li child])]]
                 [:hr]
                 [:footer {:style {"text-align" "center"}} "Served by http-server.clj"]]]
               html/html
               str)}))

(defn- body
  ([path]
   (body path {}))
  ([path headers]
   (let [base-headers {"Content-Type" (or (ext-mime-type (fs/file-name path)) "application/octet-stream")}
         response {:headers (merge base-headers headers)
                   :body (fs/file path)}]
     response)))

(defn- with-ext [path ext]
  (fs/path (fs/parent path) (str (fs/file-name path) ext)))

(defn- parse-range-header [range-header]
  (map #(when % (Long/parseLong %))
       (-> range-header
           (s/replace #"^bytes=" "")
           (s/split #"-"))))

(defn- read-bytes [f [start end]]
  (let [end (or end (dec (min (fs/size f)
                              (+ start (* 1024 1024)))))
        len (- end start)
        arr (byte-array len)]
    (with-open [r (java.io.RandomAccessFile. f "r")]
      (.seek r start)
      (.read r arr 0 len))
    arr))

(defn- byte-range
  ([path request-headers]
   (byte-range path request-headers {}))
  ([path request-headers response-headers]
   (let [f (fs/file path)
         [start _end :as requested-range] (parse-range-header (request-headers "range"))
         arr (read-bytes f requested-range)
         num-bytes-read (count arr)]
     {:status 206
      :headers (merge {"Content-Type" (ext-mime-type (fs/file-name path))
                       "Accept-Ranges" "bytes"
                       "Content-Length" num-bytes-read
                       "Content-Range" (format "bytes %d-%d/%d"
                                               start
                                               (+ start num-bytes-read)
                                               (fs/size f))}
                      response-headers)
      :body arr})))


(defn handle-get [{:keys [uri] :as request}]
  (let [dir (fs/cwd)
        f (fs/path dir (s/replace-first (URLDecoder/decode uri) #"^/" ""))
        index-file (fs/path f "index.html")]
    (cond
      (and (fs/directory? f) (fs/readable? index-file))
      (body index-file)

      (fs/directory? f)
      (index dir f)

      (and (fs/readable? f) (contains? (:headers request) "range"))
      (byte-range f (:headers request))

      (fs/readable? f)
      (body f)

      (and (nil? (fs/extension f)) (fs/readable? (with-ext f ".html")))
      (body (with-ext f ".html"))

      :else
      (http/not-found uri))))

(defn- create-unique-filename [upload-dir filename]
  (if-not (fs/exists? (fs/path upload-dir filename))
    (fs/path upload-dir filename)
    (->> (range)
         (map #(fs/path upload-dir (str filename "-" % (fs/extension filename))))
         (drop-while fs/exists?)
         (first))))

(defn handle-post [request]
  (let [referer (get-in request [:headers "referer"])
        origin (get-in request [:headers "origin"])
        relative-path (let [x (s/replace-first referer origin "")]
                        (if (s/starts-with? x "/")
                          (s/replace-first x "/" "")
                          x))
        root-dir (fs/cwd)
        upload-dir (fs/path root-dir relative-path)]

    (when-not (s/starts-with? (str (fs/canonicalize upload-dir))
                              (str (fs/canonicalize root-dir)))
      (throw (ex-info "upload-dir must be a descendant of root-dir" {:root-dir root-dir
                                                                     :upload-dir upload-dir})))

    (fs/create-dirs upload-dir)

    (doseq [[_name part] (:multipart-params request)]
      (let [filename (:filename part)
            unique-filename (create-unique-filename upload-dir (or filename "upload-file"))
            body-file (:tempfile part)]
        (fs/copy body-file unique-filename)))

    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str "File(s) uploaded successfully")}))

(defn router [{:keys [request-method] :as request}]
  (cond
    (= request-method :get)
    (handle-get request)

    (= request-method :post)
    (handle-post request)

    :else
    (http/not-found (:uri request))))

(defn create-request-pipeline []
  (-> #'router
      (multipart/wrap-multipart-params)))

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
