(ns starwars.custom
  "Star Wars example using Ringline framework.

   This example demonstrates:
   - Malli schemas as single source of truth
   - Automatic Datomic schema generation
   - Automatic GraphQL schema generation
   - Automatic query resolvers via :ringline/query-root
   - Enum support (Episode)
   - Multiple entity types (Human, Droid)
   - Datomic in-memory database"
  (:require [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [com.walmartlabs.lacinia.util :as util]
            [datomic.api :as d]
            [clojure.pprint :as pprint]))


(declare Party)

(def PII
  [:map
   {:ringline/schema-name :pii
    :ringline/datomic-ns "pii"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id :email]
    :ringline/datomic {:datomic/no-history true}}
   [:id :uuid]
   [:title {:optional true} :string]
   [:first_name {:optional true} :string]
   [:middle_name {:optional true} :string]
   [:last_name {:optional true} :string]
   [:email :string]
   [:phone {:optional true} :string]
   [:country_of_residence_code {:optional true} :string]
   [:date_of_birth {:optional true} :time/local-date]])

(def Organization
  [:map
   {:ringline/schema-name :organization
    :ringline/datomic-ns "org"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id :name]}
   [:id :uuid]
   [:name :string]
   [:country_of_incorporation_code :string]])

(def Party
  [:map
   {:ringline/schema-name :party
    :ringline/datomic-ns "party"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id]}
   [:id :uuid]
   [:personal_info #'PII]
   [:organization #'Organization]])

(def db-uri "datomic:mem://starwars-custom")

(defn create-custom-graphql-system!
  "Create and initialize Datomic in-memory database with schema"
  [db-uri]
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [{:keys [datomic lacinia namespace-lookup schemas-map]} (ringline/init-framework [Party Organization PII] {})
          tx-data (mapcat ringline-datomic/schema->transaction datomic)
          schema (-> lacinia
                     ;; Attach resolvers
                     (util/inject-resolvers {:queries/pii (ringline/create-resolver :pii conn namespace-lookup)
                                             :queries/party (ringline/create-resolver :party conn namespace-lookup)
                                             :queries/organization (ringline/create-resolver :organization conn namespace-lookup)})
                     (ringline/attach-mutation-resolvers schemas-map conn namespace-lookup)
                     (lacinia-schema/compile))]
      @(d/transact conn tx-data)
      ;@(d/transact conn (concat initial-planets initial-humans initial-droids))

      {:schema schema
       :conn conn})))
