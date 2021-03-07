(ns entities
  (:require [clojure.java.jdbc :as jdbc]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn ^:private load-entity [db id]
  (->> (jdbc/query db [(str "SELECT attribute, value "
                            "FROM datoms WHERE entity = ? "
                            "ORDER BY time, rowid") id])
       (map (fn [av] [(:attribute av) (:value av)]))
       (into {})))

(defn ^:private load-entities [db]
  (map
    :entity
    (jdbc/query db [(str "SELECT entity FROM datoms GROUP BY entity "
                         "ORDER BY time DESC, rowid LIMIT 10")])))

(defn ^:private retreive' [request]
  (jdbc/with-db-transaction [t (:db request)]
    {:status 200
     :body {:entities
            (for [entity (load-entities t)]
              [entity (load-entity t entity)])}}))

(def retrieve (wrap-json-response retreive'))
