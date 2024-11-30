(ns file-server.cli
  (:require [babashka.cli :as cli]))

(def default-options
  {:port       8090
   :ip         "127.0.0.1"})

(def cli-spec {:help
               {:ref "<help>"
                :desc "Display the Help description."
                :alias :h}

               :verbose
               {:ref "<verbosity>"
                :desc "Logging verbosity. <verbosity> can be provided as a switch (-v or --verbose) or key=val (--verbosity=false)."
                :alias :v
                :coerce []}

               :host
               {:ref "<host>"
                :desc "IP/hosntame of the interface for the server application."
                :alias :i}

               :port
               {:ref "<port>"
                :desc "Port for the server application."
                :alias :p
                :coerce :long
                :validate {:pred #(< 0 % 0x10000)
                           :ex-msg (fn [m] (str "Not a valid port number: " (:value m)))}}})

(defn create-cli-error-fn [cli-errors-ref]
  (fn [{:keys [_spec type cause msg _option] :as data}]
    (if (= :org.babashka/cli type)
      (condp = cause
        :require
        (swap! cli-errors-ref conj (str "Missing required argument:\t" msg))

        :validate
        (swap! cli-errors-ref conj (str "Invalid argument:\t" msg))

        :restrict
        (swap! cli-errors-ref conj (str "Restricted argument:\t" msg))

        :coerce
        (swap! cli-errors-ref conj (str "Failed Coercion:\t" msg))

        :else
        (swap! cli-errors-ref conj (str "Argument error:\t" cause ";\t" msg)))
      (throw (ex-info msg data)))))

(defn args->opts [args cli-error-fn]
  (cli/parse-opts args {:spec cli-spec
                        :error-fn cli-error-fn}))

(defn gather-application-settings
  ([default-overrides cli-args cli-error-fn]
   (let [cli-settings (args->opts cli-args cli-error-fn)
         config-file-settings (or (:configuration-file cli-settings)
                                  {})
         settings (merge default-options default-overrides config-file-settings cli-settings)]
     settings))
  ([cli-args cli-error-fn]
   (gather-application-settings {} cli-args cli-error-fn)))
