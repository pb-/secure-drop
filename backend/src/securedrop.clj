(ns securedrop
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes POST GET]]
            [compojure.route :refer [not-found]]
            [clojure.java.io :refer [output-stream input-stream]]
            [clojure.java.jdbc :as jdbc]
            [datoms]
            [entities]
            [blob])
  (:import [java.io File]))

(def data-directory (System/getenv "DATA_PATH"))
(def upload-token (System/getenv "UPLOAD_TOKEN"))
(def download-token (System/getenv "DOWNLOAD_TOKEN"))
(def database-spec
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname (str data-directory File/separatorChar "datoms.db")})

(defn wrap-require-token [handler token]
  (fn [request]
    (if (= ((:headers request) "token") token)
      (handler request)
      {:status 403
       :headers {"Content-Type" "text/plain"}
       :body "nope"})))

(defn wrap-db-connection [handler db-spec]
  (fn [request]
    (jdbc/with-db-connection [db db-spec]
      (handler (assoc request :db db)))))

(defn wrap-data [handler data-dir]
  (fn [request]
    (handler (assoc request :data-directory data-dir))))

(defroutes handler
  (POST "/api/blob" []
        (wrap-data
          (wrap-require-token blob/create upload-token) data-directory))
  (GET ["/api/blob/:id", :id #"[0-9a-f]{40}"] [id]
       (wrap-data
         (wrap-require-token blob/retrieve download-token) data-directory))
  (POST "/api/datoms" []
        (wrap-db-connection
          (wrap-require-token datoms/create upload-token) database-spec))
  (GET "/api/entities" []
       (wrap-db-connection
         (wrap-require-token entities/retrieve download-token) database-spec))
  (not-found "not here"))

(defn -main []
  ;; TODO check that data dir is not nil, make subdir, etc.
  )

(comment
  ; For development use with nrepl
  (do
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (run-jetty (wrap-reload #'handler) {:port 4711 :join? false})))
