(ns ringline.integration.schema-parsing-test
  "Integration tests for multi-entity schema parsing with relationships"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
            [ringline.schema.datomic :as datomic]
            [ringline.schema.lacinia :as lacinia]
            [ringline.fixtures :as fixtures]))

(deftest multi-entity-parsing-integration-test
  (testing "Complete workflow: parse multiple related entities"
    (let [schemas {:user fixtures/user-schema
                   :post fixtures/post-schema
                   :comment fixtures/comment-schema}
          result (parser/parse-schemas schemas)]

      (testing "All entities are parsed"
        (is (= 3 (count result)) "Parses all three entities")
        (is (every? :schema-name result) "All have schema names")
        (is (every? :fields result) "All have fields")
        (is (every? :properties result) "All have properties"))

      (testing "Relationships are correctly identified"
        (let [user (first (filter #(= :user (:schema-name %)) result))
              post (first (filter #(= :post (:schema-name %)) result))
              comment (first (filter #(= :comment (:schema-name %)) result))]

          ;; User -> Posts (one-to-many)
          (is (some #(= :posts (:field %)) (:relationships user))
              "User has posts relationship")
          (let [posts-rel (first (filter #(= :posts (:field %)) (:relationships user)))]
            (is (= :post (:target posts-rel)) "Posts targets Post entity")
            (is (= :many (:cardinality posts-rel)) "Posts is one-to-many"))

          ;; Post -> Author (many-to-one)
          (is (some #(= :author (:field %)) (:relationships post))
              "Post has author relationship")
          (let [author-rel (first (filter #(= :author (:field %)) (:relationships post)))]
            (is (= :user (:target author-rel)) "Author targets User entity")
            (is (= :one (:cardinality author-rel)) "Author is many-to-one"))

          ;; Comment -> Post (many-to-one)
          (is (some #(= :post (:field %)) (:relationships comment))
              "Comment has post relationship")

          ;; Comment -> Author (many-to-one)
          (is (some #(= :author (:field %)) (:relationships comment))
              "Comment has author relationship")))

      (testing "Custom properties are preserved"
        (let [user (first (filter #(= :user (:schema-name %)) result))
              post (first (filter #(= :post (:schema-name %)) result))]
          (is (= :user (get-in user [:properties :ringline/datomic-ns]))
              "User has correct datomic namespace")
          (is (= :post (get-in post [:properties :ringline/datomic-ns]))
              "Post has correct datomic namespace")
          (is (true? (get-in user [:properties :ringline/query-root]))
              "User is marked as query root")
          (is (true? (get-in post [:properties :ringline/query-root]))
              "Post is marked as query root")))

      (testing "Field metadata is complete"
        (let [user (first (filter #(= :user (:schema-name %)) result))
              user-fields (:fields user)
              id-field (first (filter #(= :id (:name %)) user-fields))
              posts-field (first (filter #(= :posts (:name %)) user-fields))]
          (is (= :uuid (:type id-field)) "ID field has UUID type")
          (is (= :ref (:type posts-field)) "Posts field has ref type")
          (is (= :many (:cardinality posts-field)) "Posts field has many cardinality"))))))

(deftest nested-relationship-resolution-test
  (testing "Three-level relationship chain: User -> Post -> Comment"
    (let [result (parser/parse-schemas fixtures/test-schemas)
          user (first (filter #(= :user (:schema-name %)) result))
          post (first (filter #(= :post (:schema-name %)) result))
          comment (first (filter #(= :comment (:schema-name %)) result))]

      ;; Verify the chain exists
      (is (some #(= :posts (:field %)) (:relationships user))
          "User links to Post")
      (is (some #(and (= :post (:field %))
                      (= :post (:target %)))
                (:relationships comment))
          "Comment links to Post")
      (is (some #(and (= :author (:field %))
                      (= :user (:target %)))
                (:relationships comment))
          "Comment links to User (author)"))))

(deftest schema-without-relationships-test
  (testing "Simple schema without any relationships"
    (let [schemas {:simple fixtures/simple-schema}
          result (parser/parse-schemas schemas)
          simple (first result)]
      (is (= :simple (:schema-name simple)))
      (is (empty? (:relationships simple)) "No relationships")
      (is (seq (:fields simple)) "Has fields")
      (is (every? #(not= :ref (:type %)) (:fields simple))
          "No ref type fields"))))

(deftest end-to-end-datomic-schema-generation-test
  (testing "Complete workflow: Malli -> ParsedSchema -> DatomicSchema -> Transaction"
    (let [schemas {:user fixtures/user-schema
                   :post fixtures/post-schema}
          parsed (parser/parse-schemas schemas)
          datomic-schemas (datomic/generate-schemas parsed)]

      (testing "Datomic schemas generated for all entities"
        (is (= 2 (count datomic-schemas)))
        (is (every? :source-entity datomic-schemas))
        (is (every? :attributes datomic-schemas)))

      (testing "User entity Datomic schema is correct"
        (let [user-schema (first (filter #(= :user (:source-entity %)) datomic-schemas))
              attrs (:attributes user-schema)]
          (is (some #(= :user/id (:db/ident %)) attrs) "Has id attribute")
          (is (some #(= :user/username (:db/ident %)) attrs) "Has username attribute")
          (is (some #(= :user/email (:db/ident %)) attrs) "Has email attribute")
          (is (some #(= :user/posts (:db/ident %)) attrs) "Has posts relationship")))

      (testing "Post entity Datomic schema is correct"
        (let [post-schema (first (filter #(= :post (:source-entity %)) datomic-schemas))
              attrs (:attributes post-schema)]
          (is (some #(= :post/id (:db/ident %)) attrs) "Has id attribute")
          (is (some #(= :post/title (:db/ident %)) attrs) "Has title attribute")
          (is (some #(= :post/author (:db/ident %)) attrs) "Has author relationship")))

      (testing "Transaction data is valid"
        (let [user-schema (first (filter #(= :user (:source-entity %)) datomic-schemas))
              tx-data (datomic/schema->transaction user-schema)]
          (is (vector? tx-data))
          (is (every? map? tx-data))
          (is (every? :db/ident tx-data))
          (is (every? :db/valueType tx-data))
          (is (every? :db/cardinality tx-data))))))

  (testing "Relationship attributes have correct ref types"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          datomic-schemas (datomic/generate-schemas parsed)
          user-schema (first (filter #(= :user (:source-entity %)) datomic-schemas))
          post-schema (first (filter #(= :post (:source-entity %)) datomic-schemas))
          user-posts (first (filter #(= :user/posts (:db/ident %)) (:attributes user-schema)))
          post-author (first (filter #(= :post/author (:db/ident %)) (:attributes post-schema)))]
      (is (= :db.type/ref (:db/valueType user-posts)) "User posts is ref type")
      (is (= :db.cardinality/many (:db/cardinality user-posts)) "User posts is many")
      (is (= :db.type/ref (:db/valueType post-author)) "Post author is ref type")
      (is (= :db.cardinality/one (:db/cardinality post-author)) "Post author is one"))))

(deftest end-to-end-lacinia-schema-generation-test
  (testing "Complete workflow: Malli -> ParsedSchema -> LaciniaSchema"
    (let [schemas {:user fixtures/user-schema
                   :post fixtures/post-schema}
          parsed (parser/parse-schemas schemas)
          lacinia-schema (lacinia/generate-schemas parsed)]

      (testing "Lacinia schema has all required sections"
        (is (map? lacinia-schema))
        (is (contains? lacinia-schema :objects))
        (is (contains? lacinia-schema :queries)))

      (testing "All entities have GraphQL object types"
        (let [objects (:objects lacinia-schema)]
          (is (contains? objects :User) "User object exists")
          (is (contains? objects :Post) "Post object exists")))

      (testing "User object has correct fields"
        (let [user-fields (get-in lacinia-schema [:objects :User :fields])]
          (is (contains? user-fields :id) "Has id field")
          (is (contains? user-fields :username) "Has username field")
          (is (contains? user-fields :email) "Has email field")
          (is (contains? user-fields :posts) "Has posts relationship field")))

      (testing "Post object has correct fields"
        (let [post-fields (get-in lacinia-schema [:objects :Post :fields])]
          (is (contains? post-fields :id) "Has id field")
          (is (contains? post-fields :title) "Has title field")
          (is (contains? post-fields :author) "Has author relationship field")))

      (testing "Query roots are created for entities marked as query-root"
        (let [queries (:queries lacinia-schema)]
          (is (some? (get queries :user)) "Has user query")
          (is (some? (get queries :post)) "Has post query")))

      (testing "Searchable fields become query arguments"
        (let [user-query (get-in lacinia-schema [:queries :user])]
          (is (contains? user-query :args) "User query has args")))))

  (testing "Complete workflow with all three schemas"
    (let [parsed (parser/parse-schemas fixtures/test-schemas)
          lacinia-schema (lacinia/generate-schemas parsed)
          objects (:objects lacinia-schema)]
      (is (= 3 (count objects)) "Has all three object types")
      (is (contains? objects :User))
      (is (contains? objects :Post))
      (is (contains? objects :Comment)))))

