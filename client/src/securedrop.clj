(ns securedrop
  (:require [clojure.java.io :refer [input-stream output-stream]])
  (:import [java.util Base64]
           [java.security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec]
           [javax.crypto Cipher]
           [javax.crypto.spec GCMParameterSpec]
           [javax.crypto.spec SecretKeySpec]))

(def rsa-key-size-bits 4096)
(def rsa-key-size-bytes (bit-shift-right rsa-key-size-bits 3))
(def block-size (bit-shift-left 1 20))
(def iv-size 12)
(def tag-size 16)
(def tag-size-bits (* 8 tag-size))

(def test-secret-key "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")

(defn base64->byte-array [b64]
  (.decode (Base64/getDecoder) b64))

(defn decode-secret-key [base64]
  (let [decoded (PKCS8EncodedKeySpec. (base64->byte-array base64))]
    (.generatePrivate (KeyFactory/getInstance "RSA") decoded)))

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

(defn -main []
  (with-open [in (input-stream "/tmp/securedrop-4205285819672884081.tmp")
              out (output-stream "/tmp/a.jpg")]
    (decrypt-stream (decode-secret-key test-secret-key) in out)))
