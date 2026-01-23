(ns ringline.integration.schema-parsing-test
  "Integration tests for multi-entity schema parsing with relationships"
  (:require [clojure.test :refer [deftest is testing]]
            [ringline.schema.parser :as parser]
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

