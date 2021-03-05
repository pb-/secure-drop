(ns securedrop
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [defroutes POST]]
            [compojure.route :refer [not-found]]
            [clojure.java.io :refer [output-stream input-stream]]
            [clojure.java.jdbc :as jdbc])
  (:import [java.io File]
           [java.nio.file Paths Files CopyOption FileAlreadyExistsException]
           [java.security MessageDigest]))

(def data-directory (System/getenv "DATA_PATH"))

(defn blob-path [blob-id]
  (Paths/get data-directory (into-array String ["blobs" blob-id])))

(defn bytea->hex [bytea]
  (apply str (map (partial format "%02x") bytea)))

(defn move-or-remove [source destination]
  (try
    (Files/move source destination (into-array CopyOption []))
    (catch FileAlreadyExistsException e
      (.delete (.toFile source)))))

(defn create-blob [request]
  (let [file (File/createTempFile "securedrop-" nil)
        buffer (byte-array (bit-shift-left 1 20))
        body (:body request)
        sha-1 (MessageDigest/getInstance "SHA-1")]
    (with-open [out (output-stream file)]
      (loop []
        (let [bytes-read (.readNBytes body buffer 0 (count buffer))]
          (when (pos? bytes-read)
            (.update sha-1 buffer 0 bytes-read)
            (.write out buffer 0 bytes-read)
            (recur)))))
    (let [sha-1-sum (.digest sha-1)
          blob-id (bytea->hex sha-1-sum)
          path (blob-path blob-id)]
      (move-or-remove (.toPath file) path)
      {:status 201
       :body blob-id})))

(defroutes handler
  (POST "/api/blob" [] create-blob)
  (not-found "not here"))

(comment
  ; For development use with nrepl
  (do
    (require '[ring.middleware.reload :refer [wrap-reload]])
    (run-jetty (wrap-reload #'handler) {:port 4711 :join? false})))
