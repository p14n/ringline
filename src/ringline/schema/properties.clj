(ns ringline.schema.properties
  "Custom Malli properties for Ringline framework")

;; Custom property keywords for Malli schemas

(def datomic-ns
  "Property key for specifying Datomic namespace for entity attributes.
   
   Example: {:ringline/datomic-ns :user}
   
   This will generate Datomic attributes like :user/id, :user/name, etc."
  :ringline/datomic-ns)

(def query-root
  "Property key for marking an entity as a GraphQL query root.
   
   Example: {:ringline/query-root true}
   
   This will add the entity to the GraphQL query root with appropriate resolvers."
  :ringline/query-root)

(def searchable
  "Property key for marking fields as searchable in GraphQL queries.
   
   Example: {:ringline/searchable [:email :username]}
   
   This will generate GraphQL query arguments for searching by these fields."
  :ringline/searchable)

;; Helper functions for property access

(defn get-datomic-ns
  "Extract the Datomic namespace from schema properties"
  [properties]
  (get properties datomic-ns))

(defn query-root?
  "Check if schema is marked as a query root"
  [properties]
  (get properties query-root false))

(defn get-searchable-fields
  "Extract searchable fields from schema properties"
  [properties]
  (get properties searchable []))

