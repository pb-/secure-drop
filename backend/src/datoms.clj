(ns datoms
  (:require [clojure.spec.alpha :as s]
            [clojure.java.jdbc :as jdbc]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]))

(def ^:private create-datoms-spec
  (let [temp-id (s/and int? neg?)]
    (s/+ (s/tuple temp-id
                  string?
                  (s/or :value string?
                        :ref temp-id)))))

(defn ^:private rand-str
  ([] (rand-str 11))
  ([len]
   (->> #(rand-nth "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz")
        (repeatedly len)
        (apply str))))

(defn ^:private bad-request [message]
  {:status 400
   :headers {"Content-Type" "text/plain"}
   :body message})

(defn ^:private generate-ids [ds]
  (reduce
    (fn [result d]
      (let [id (first d)]
        (if-not (result id)
          (assoc result id (rand-str))
          result))) {} ds))

(defn ^:private resolve-ids [ds ids]
  (if-let [bad-id (first (for [[_ _ v] ds :when (and (int? v) (nil? (ids v)))] v))]
    [(format "unknown temp-id reference %d" bad-id) nil]
    [nil
     (mapv
       (fn [[entity attribute value]]
         [(ids entity) attribute (if (int? value) (ids value) value)]) ds)]))

(defn ^:private insert! [db ds]
  (let [now (/ (System/currentTimeMillis) 1000.)]
    (jdbc/with-db-transaction [t db]
      (jdbc/insert-multi!
        t
        :datoms
        [:entity :attribute :value :time]
        (for [[e a v] ds]
          [e a v now])))))

(defn ^:private create' [request]
  (let [ds (:body request)]
    (if-not (s/valid? create-datoms-spec ds)
      (bad-request (s/explain-str create-datoms-spec ds))
      (let [ids (generate-ids ds)
            [err resolved-ds] (resolve-ids ds ids)]
        (if-not err
          (do (insert! (:db request) resolved-ds)
              {:status 201
               :body ids})
          (bad-request err))))))

(def create (wrap-json-response (wrap-json-body create')))
