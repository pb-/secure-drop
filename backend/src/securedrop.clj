(ns securedrop
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes POST]]
            [compojure.route :refer [not-found]]
            [clojure.java.io :refer [output-stream input-stream]]
            [clojure.java.jdbc :as jdbc])
  (:import [java.io File]))

(defn add-blob [request]
  (let [file (File/createTempFile "securedrop-" nil)]
    (with-open [out (output-stream file)]
      (.transferTo (:body request) out))
    (println "stored" (.getName file))
    {:status 201
     :body (.getName file)}))

(defroutes handler
  (POST "/api/blob" [] add-blob)
  (not-found "not here"))

(comment
  ; For development use with nrepl
  (do
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (run-jetty (wrap-reload #'handler) {:port 4711 :join? false})))
