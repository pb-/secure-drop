(ns securedrop
  (:require [clojure.java.io :refer [input-stream output-stream copy make-parents]]
            [clojure.java.shell :as shell]
            [clojure.string :refer [trim]]
            [clj-http.client :as http])
  (:import [java.util Base64]
           [java.security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec]
           [javax.crypto Cipher]
           [javax.crypto.spec GCMParameterSpec]
           [javax.crypto.spec SecretKeySpec]
           [java.time LocalDateTime ZoneId Instant]
           [java.io ByteArrayOutputStream File IOException File]))

(def ^:dynamic *endpoint* "")
(def ^:dynamic *download-token* "")
(def ^:dynamic *secret-key-file* "")

(def rsa-key-size-bits 4096)
(def rsa-key-size-bytes (bit-shift-right rsa-key-size-bits 3))
(def block-size (bit-shift-left 1 20))
(def iv-size 12)
(def tag-size 16)
(def tag-size-bits (* 8 tag-size))


(defn load-secret-key [filename]
  (let [key-bytes (with-open [out (ByteArrayOutputStream.)]
                    (copy (input-stream filename) out)
                    (.toByteArray out))]
    (let [decoded (PKCS8EncodedKeySpec. key-bytes)]
      (.generatePrivate (KeyFactory/getInstance "RSA") decoded))))

(defn read-bytes [in n]
  (let [buffer (byte-array n)]
    (.readNBytes in buffer 0 n)
    buffer))

(defn decrypt-key [secret-key ciphertext]
  (let [cipher (Cipher/getInstance "RSA/ECB/OAEPWithSHA1AndMGF1Padding")]
    (.init cipher Cipher/DECRYPT_MODE secret-key)
    (.doFinal cipher ciphertext)))

(defn decode-unsigned-byte [b]
  (if (neg? b) (+ 256 b) b))

(defn decode-48-bit-int [s]
  (reduce
    (fn [n b]
      (bit-or (bit-shift-left n 8)
              (decode-unsigned-byte b))) 0 s))

(defn decode-iv [iv-bytes]
  (map decode-48-bit-int (partition (/ iv-size 2) iv-bytes)))

(defn decrypt-block [secret-key iv buffer size]
  (let [cipher (Cipher/getInstance "AES_256/GCM/NoPadding")
        param-spec (GCMParameterSpec. tag-size-bits iv)]
    (.init cipher Cipher/DECRYPT_MODE secret-key param-spec)
    (.doFinal cipher buffer iv-size size buffer iv-size)))

(defn decrypt-stream [secret-key in out]
  (let [encrypted-key (read-bytes in rsa-key-size-bytes)
        decrypted-key (decrypt-key secret-key encrypted-key)
        decoded-key (SecretKeySpec. decrypted-key "AES")
        buffer (byte-array block-size)]
    (loop [expected-total-chunks nil
           expected-chunk 0]
      (let [bytes-read (.readNBytes in buffer 0 block-size)
            ciphertext-size (- bytes-read iv-size)
            payload-size (- ciphertext-size tag-size)
            iv (byte-array (take iv-size buffer))
            [total-chunks current-chunk] (decode-iv iv)]
        (if (and (or (nil? expected-total-chunks)
                     (= total-chunks expected-total-chunks))
                 (= current-chunk expected-chunk))
          (do
            (decrypt-block decoded-key iv buffer ciphertext-size)
            (.write out buffer iv-size payload-size))
          (throw (ex-info "bad chunk" {})))
        (when (< (inc current-chunk) total-chunks)
          (recur total-chunks (inc current-chunk)))))))

(defn parse-long [s default]
  (if s
    (try
      (Long/parseLong s)
      (catch NumberFormatException e
        default))
    default))

(defn format-size [size]
  (if (< size 1024)
    [(str size) "B"]
    (loop [value size
           unit "KMGTPE"]
      (if (< value (* 1024 1024))
        [(format "%.1f" (/ value 1024.)) (str (first unit) "iB")]
        (recur (bit-shift-right value 10) (next unit))))))

(defn parse-batch [[id attrs]]
  {:id id
   :size (Integer/parseInt (attrs "batch/size"))
   :uploaded-at (LocalDateTime/ofInstant
                  (Instant/ofEpochSecond (parse-long
                                           (attrs "batch/uploaded-at") 0))
                  (ZoneId/systemDefault))})

(defn parse-file [[id attrs]]
  {:id id
   :blob-id (attrs "file/blob-id")
   :encrypted? (Boolean/parseBoolean (attrs "file/encrypted?"))
   :file-name (attrs "file/name")
   :size (Long/parseLong (attrs "file/size"))})

(defn format-batch [{:keys [id size uploaded-at]}]
  (format
    " %s  %-19s  %d file%s" id uploaded-at size (if (> size 1) "s" "")))

(defn format-file [{:keys [size file-name]}]
  (let [[value unit] (format-size size)]
    (format " %6s %-3s  %s" value unit file-name)))

(defn retrieve-entities [params]
  ((:body
     (http/get (str *endpoint* "/entities")
               {:accept :json
                :as :json-string-keys
                :headers {"Token" *download-token*}
                :query-params params})) "entities"))

(defn printerrln [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn retrieve-file [{:keys [blob-id file-name]}]
  (let [response (http/get (str *endpoint* "/blob/" blob-id)
                           {:headers {"Token" *download-token*}
                            :as :stream})]
    (if (= (:status response) 200)
      (with-open [out (output-stream file-name)]
        (println "downloading " file-name)
        (decrypt-stream
          (load-secret-key *secret-key-file*)
          (:body response) out))
      (printerrln "HTTP" (:status response) "for" blob-id))))

(defn list-batches []
  (retrieve-entities {:a "batch/size"}))

(defn list-files [batch-id]
  (retrieve-entities {:a "file/batch-id" :v batch-id}))

(defn cli-list-batches []
  (doseq [batch (map (comp format-batch parse-batch) (list-batches))]
    (println batch)))

(defn cli-show-batch [id]
  (doseq [file (map (comp format-file parse-file) (list-files id))]
    (println file)))

(defn cli-download-batch [id]
  (dorun (map (comp retrieve-file parse-file) (list-files id))))

(defn join-path [& parts]
  (apply str (interpose File/separatorChar parts)))

(defn load-config-value [path prompt]
  (try
    (trim (slurp path))
    (catch IOException _
      (print (str prompt ": "))
      (flush)
      (spit path (read-line))
      (load-config-value path prompt))))

(defn get-secret-key-file [path]
  (if-not (.exists (File. path))
    (do
      (println "No secret key found, generating a new one")
      (let [result (shell/sh "openssl" "genrsa" "4096" :out-enc :bytes)
            result (shell/sh
                     "openssl" "pkcs8" "-topk8"
                     "-outform" "DER" "-nocrypt" "-out" path
                     :in (:out result))]
        path))
    path))

(defn load-config []
  (let [config-path (join-path (System/getenv "HOME") ".config" "secure-drop")]
    (make-parents (join-path config-path "dummy"))
    {:secret-key-file (get-secret-key-file
                        (join-path config-path "private-key"))
     :endpoint (load-config-value
                 (join-path config-path "endpoint")
                 "API endpoint (usually ends in /api)")
     :download-token (load-config-value
                       (join-path config-path "download-token")
                       "Your download token")}))

(defn usage []
  (printerrln "Usage: ... batch list")
  (printerrln "       ... batch show BATCH-ID")
  (printerrln "       ... batch download BATCH-ID"))

(defn -main [& args]
  (let [config (load-config)]
    (binding [*endpoint* (:endpoint config)
              *secret-key-file* (:secret-key-file config)
              *download-token* (:download-token config)]
      (if args
        (let [[command & args] args]
          (case command
            "batch" (let [[subcommand & args] args]
                      (case subcommand
                        "list" (cli-list-batches)
                        "show" (cli-show-batch (first args))
                        "download" (cli-download-batch (first args))
                        (usage)))
            (usage)))
        (usage)))))
