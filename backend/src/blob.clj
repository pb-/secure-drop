(ns blob
  (:require [compojure.route :refer [not-found]]
            [clojure.java.io :refer [input-stream output-stream make-parents]])
  (:import [java.io File]
           [java.nio.file Paths Files CopyOption FileAlreadyExistsException]
           [java.security MessageDigest]))

(defn ^:private blob-path [data-directory blob-id]
  (str data-directory File/separatorChar "blobs" File/separatorChar blob-id))

(defn ^:private bytea->hex [bytea]
  (apply str (map (partial format "%02x") bytea)))

(defn ^:private move-or-remove [source destination]
  (try
    (Files/move source destination (into-array CopyOption []))
    (catch FileAlreadyExistsException e
      (.delete (.toFile source)))))

(defn create [request]
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
          path (File. (blob-path (:data-directory request) blob-id))]
      (make-parents path)
      (move-or-remove (.toPath file) (.toPath path))
      {:status 201
       :headers {"Content-type" "text/plain"}
       :body blob-id})))

(defn retrieve [request]
  (let [blob-id (:id (:params request))
        file (blob-path (:data-directory request) blob-id)]
    (if (.exists (File. file))
      {:status 200
       :headers {"Content-Type" "application/octet-stream"}
       :body (input-stream file)}
      (not-found "no such blob"))))
