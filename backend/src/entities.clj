(ns entities
  (:require [clojure.java.jdbc :as jdbc]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [compojure.route :refer [not-found]]))

(defn ^:private load-entity [db id]
  (->> (jdbc/query db [(str "SELECT attribute, value "
                            "FROM datoms WHERE entity = ? "
                            "ORDER BY time, rowid") id])
       (map (fn [av] [(:attribute av) (:value av)]))
       (into {})))

(defn ^:private load-entities [db conditions]
  (let [[where-sql where-data] conditions]
    (map
      :entity
      (jdbc/query db (concat
                       [(str "SELECT entity FROM datoms "
                             "WHERE true" where-sql " "
                             "GROUP BY entity "
                             "ORDER BY time DESC, rowid")]
                       where-data)))))

(defn ^:private params->conditions [params]
  (let [parts [(when-let [attribute (:a params)]
                 [" AND attribute = ?", [attribute]])
               (when-let [value (:v params)]
                 [" AND value = ?", [value]])]
        merge-fn (fn [merged part]
                   (if part
                     (let [[sql data] merged
                           [s d] part]
                       [(str sql s) (concat data d)])
                     merged))]
    (reduce merge-fn ["", []] parts)))

(defn ^:private retreive' [request]
  (jdbc/with-db-transaction [t (:db request)]
    {:status 200
     :body {:entities
            (for [entity (load-entities
                           t (params->conditions (:params request)))]
              [entity (load-entity t entity)])}}))

(defn ^:private retrieve-one' [request]
  (let [entity (load-entity (:db request) (:id (:params request)))]
    (if (seq entity)
      {:status 200
       :body entity}
      (not-found "no such entity"))))

(def retrieve (-> retreive'
                  wrap-json-response
                  wrap-keyword-params
                  wrap-params))

(def retrieve-one (-> retrieve-one'
                      wrap-json-response))
