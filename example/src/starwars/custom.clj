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
  (:require
   [com.walmartlabs.lacinia.schema :as lacinia-schema]
   [com.walmartlabs.lacinia.util :as util]
   [datomic.api :as d]
   [ringline.core :as ringline]
   [ringline.mutation.transaction :as tx]
   [ringline.schema.datomic :as ringline-datomic]
   [clojure.pprint :as pprint])
  (:import
   [java.util UUID]))


(declare Party)

(def PII
  [:map
   {:ringline/schema-name :pii
    :ringline/datomic-ns "pii"
    :ringline/query-root true
    :ringline/mutations #{:create :update :delete}
    :ringline/searchable [:id :email]
    :ringline/datomic {:db/noHistory true}}
   [:id :uuid]
   [:title {:optional true} :string]
   [:first_name {:optional true} :string]
   [:middle_name {:optional true} :string]
   [:last_name {:optional true} :string]
   [:email :string]
   [:phone {:optional true} :string]
   [:country_of_residence_code {:optional true} :string]
   [:date_of_birth {:optional true} :time/local-date]
   [:party {:optional true
            :ringline/reverse-lookup :party/_personal_info} [:ref #'Party]]])

(def Address
  [:map
   {:ringline/schema-name :address
    :ringline/datomic-ns "address"
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:street :string]
   [:city :string]
   [:state {:optional true} :string]
   [:postal_code :string]
   [:country_code :string]])

(def PartyAddress
  [:map
   {:ringline/schema-name :party_address
    :ringline/datomic-ns "party_address"
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:address #'Address]
   [:party #'Party]
   [:primary {:optional true} :boolean]])

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
   [:addresses {:ringline/reverse-lookup :party_address/_party} [:vector [:ref #'PartyAddress]]]
   [:personal_info #'PII]
   [:organization #'Organization]])

(def db-uri "datomic:mem://starwars-custom")

(def custom-operations {:queries {:allParties {:args [:map
                                                      [:organizations [:vector :uuid]]]
                                               :return-type [:vector :Party]}}
                        :mutations {:inviteParty {:args [:map [:email :string]
                                                         [:organization :uuid]]
                                                  :return-type :Party}}})
(def invite-party-mutation

  (ringline/transact-and-pull

   (fn [_ args _]
     (println "Inviting party" args)
     (let [piiid (tx/generate-tempid)]
       [{:db/id piiid :pii/email (:email args)}
        {:db/id (tx/generate-tempid)
         :party/id (d/squuid)
         :party/personal_info piiid
         :party/organization [:org/id (UUID/fromString (:organization args))]}]))

   :party :party/id))

(def all-parties-query
  (ringline/create-resolver
   :party
   (fn [_ {:keys [organizations]} query]
     (let [uuids (->> organizations (map UUID/fromString) (set))]
       (merge query
              {:where ['[?e :party/organization ?org]
                       '[?org :org/id ?v]
                       [(list 'contains? uuids '?v)]]})))))

(defn create-custom-graphql-system!
  "Create and initialize Datomic in-memory database with schema"
  [db-uri]
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    (println "Database created successfully")
    (let [opts {:custom-operations custom-operations
                :resolvers {:allParties all-parties-query
                            :inviteParty invite-party-mutation}}
          {:keys [datomic lacinia namespace-lookup schemas-map reverse-lookups input-converters] :as framework} (ringline/init-framework
                                                                                                                 [Party Organization PII PartyAddress Address] opts)
          tx-data (mapcat ringline-datomic/schema->transaction datomic)
          schema (-> lacinia
                     ;; Attach resolvers
                     (util/inject-resolvers {:queries/pii (ringline/create-resolver :pii)
                                             :queries/party (ringline/create-resolver :party)
                                             :queries/organization (ringline/create-resolver :organization)})
                     (ringline/attach-mutation-resolvers schemas-map conn namespace-lookup reverse-lookups input-converters)
                     (lacinia-schema/compile))]
      @(d/transact conn tx-data)

      {:framework framework
       :lacinia schema
       :conn conn})))
