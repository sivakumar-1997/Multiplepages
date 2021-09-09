(ns app.system.service.immutant-web
  (:require [immutant.web :as web]
            [integrant.core :as ig]
            [lib.clojure-tools-logging.logger :as logger]
            [lib.clojure.core :as e]
            [lib.integrant.system :as system]))

(set! *warn-on-reflection* true)

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defn- start-webapp
  [server, {webapp-name :name :as webapp}, server-options]
  (let [options (merge server-options (webapp :options))]
    (logger/debug (logger/get-logger *ns*) (e/spr "Start webapp" webapp-name options))
    (-> (web/run (webapp :handler) (merge server options))
        (with-meta (update (meta server) :running-webapps
                           conj [webapp-name options])))))

(defn- skip-webapp
  [server, webapp]
  (logger/debug (logger/get-logger *ns*) (e/spr "Skip webapp" webapp))
  server)

(defn- start-server
  [{:keys [options
           webapps
           dev/prepare-webapp
           await-before-start]}]

  (system/await-before-start await-before-start)

  (let [prepare-webapp (or prepare-webapp identity)]
    (reduce (fn [server, {:keys [webapp-is-enabled] :or {webapp-is-enabled true} :as webapp}]
              (if webapp-is-enabled
                (start-webapp server (prepare-webapp webapp) options)
                (skip-webapp server webapp)))
            (-> (or options {})
                (with-meta {:running-webapps []}))
            webapps)))

(defn- stop-server
  [server]
  (web/stop server))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defmethod ig/init-key :app.system.service/immutant-web
  [_ options]
  (e/future (start-server options)))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••

(defmethod ig/halt-key! :app.system.service/immutant-web
  [_ server]
  ;; Stop service synchronously to continue shutdown of other systems when server is fully stopped.
  (stop-server (e/unwrap-future server)))

;;••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••••
