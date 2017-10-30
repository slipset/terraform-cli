(ns terraform-clj.core
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.set :as set]))

(defmulti build-resource :type)

(defmethod build-resource :default [{:keys [id type] :as conf}]
  {type {id (dissoc conf :type :id)}})

(defmethod build-resource :aws-launch-configuration [{:keys [id type] :as conf}]
  {type {id (-> conf
                (dissoc :id :type :tags)
                (set/rename-keys {:ami :image-id
                                  :vpc-security-group-ids :security-groups}))}})

(defn build-resources [resources]
  {:resource (->> resources
                   (map build-resource)
                   (apply merge-with conj))})

(defn build-provider [{:keys [provider region profile]}]
  {:provider {provider {:region region
                        :profile profile} }})

(defn to-underscore [k]
  (str/replace (name k) \- \_))

(defn build [conf]
  (let [provider (build-provider conf)
        resources (build-resources (:resources conf))]
    (json/encode (merge provider resources) {:key-fn to-underscore
                                             :pretty true})))

(defn ->json-file [filename json]
  (spit filename json))
