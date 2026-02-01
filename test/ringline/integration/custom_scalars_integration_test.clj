(ns ringline.integration.custom-scalars-integration-test
  "End-to-end integration tests for custom scalar types.

  Tests complete CRUD workflows for:
  - Date fields (Event entity)
  - DateTime fields (Task entity)
  - Enum fields (Task entity - status, priority)
  - Decimal fields (Product entity - price, weight)"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ringline.core :as ringline]
            [ringline.schema.datomic :as ringline-datomic]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as lacinia-schema]
            [datomic.api :as d]))

;; ============================================================================
;; Test Schemas
;; ============================================================================

;; Event schema with Date fields (User Story 1)
(def event-schema
  [:map
   {:ringline/schema-name :event
    :ringline/datomic-ns "event"
    :ringline/query-root true
    :ringline/searchable [:name]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:event_date :time/local-date]
   [:registration_deadline {:optional true} :time/local-date]])

;; Task schema with DateTime and Enum fields (User Stories 2 & 3)
(def task-schema
  [:map
   {:ringline/schema-name :task
    :ringline/datomic-ns "task"
    :ringline/query-root true
    :ringline/searchable [:title]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:title :string]
   [:status [:enum :draft :in_progress :completed :cancelled]]
   [:priority [:enum :low :medium :high :urgent]]
   [:created_at :time/offset-date-time]
   [:updated_at :time/offset-date-time]
   [:scheduled_for {:optional true} :time/offset-date-time]])

;; Product schema with Decimal fields (User Story 4)
;; NOTE: Using snake_case for field names to match GraphQL/Lacinia conventions
(def product-schema
  [:map
   {:ringline/schema-name :product
    :ringline/datomic-ns "product"
    :ringline/query-root true
    :ringline/searchable [:name]
    :ringline/mutations #{:create :update :delete}}
   [:id :uuid]
   [:name :string]
   [:price [:decimal {:precision 38 :scale 10}]]
   [:weight_kg {:optional true} [:decimal {:precision 38 :scale 10}]]])

;; All schemas now included - all scalars are fully implemented
(def schemas [event-schema
              task-schema
              product-schema])

(def db-uri "datomic:mem://custom-scalars-test")

;; ============================================================================
;; Test Fixtures
;; ============================================================================

(defn create-test-database!
  "Create and initialize Datomic database with schema"
  []
  ;; Create database
  (d/create-database db-uri)
  (let [conn (d/connect db-uri)]
    ;; Generate and install Datomic schema
    (let [framework (ringline/init-framework schemas {})
          datomic-schemas (:datomic framework)
          tx-data (mapcat ringline-datomic/schema->transaction datomic-schemas)]
      @(d/transact conn tx-data))
    conn))

(defn create-test-schema
  "Create the complete Lacinia schema with resolvers attached and compiled"
  [conn]
  (let [{:keys [namespace-lookup] :as framework} (ringline/init-framework schemas {})
        lacinia-schema (:lacinia framework)

        ;; Attach mutation resolvers
        schema-with-mutations (ringline/attach-mutation-resolvers
                               lacinia-schema
                               schemas
                               conn
                               namespace-lookup)

        ;; Attach automatic query resolvers for all entities
        event-resolver (ringline/create-resolver :event conn {})
        task-resolver (ringline/create-resolver :task conn {})
        product-resolver (ringline/create-resolver :product conn {})
        schema-with-all-resolvers (-> schema-with-mutations
                                      (assoc-in [:queries :event :resolve] event-resolver)
                                      (assoc-in [:queries :task :resolve] task-resolver)
                                      (assoc-in [:queries :product :resolve] product-resolver))]
    ;; Compile the schema
    (lacinia-schema/compile schema-with-all-resolvers)))

(defn cleanup-database!
  "Clean up test database"
  [conn]
  (when conn
    (d/release conn)
    (d/delete-database db-uri)))

(defn execute-query
  "Execute a GraphQL query against the schema"
  [schema query-str conn]
  (lacinia/execute schema query-str nil {:db-conn conn}))

;; ============================================================================
;; User Story 1: Date Field Support Integration Tests (T035-T038)
;; ============================================================================

;; T035: Test Event entity with Date fields - CREATE mutation
(deftest create-event-with-date-test
  (testing "Create Event with Date fields using ISO8601 format"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "mutation {
                                      createEvent(input: {
                                        name: \"Annual Conference 2024\"
                                        event_date: \"2024-06-15\"
                                        registration_deadline: \"2024-05-31\"
                                      }) {
                                        id
                                        name
                                        event_date
                                        registration_deadline
                                      }
                                    }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (some? (get-in result [:data :createEvent :id]))
              "Returns generated UUID")
          (is (= "Annual Conference 2024"
                 (get-in result [:data :createEvent :name]))
              "Returns correct name")
          (is (= "2024-06-15"
                 (get-in result [:data :createEvent :event_date]))
              "Returns event date in ISO8601 format (10 chars)")
          (is (= "2024-05-31"
                 (get-in result [:data :createEvent :registration_deadline]))
              "Returns registration deadline in ISO8601 format"))
        (finally
          (cleanup-database! conn))))))

;; T036: Test Event entity with Date fields - QUERY
(deftest query-event-with-date-test
  (testing "Query Event returns Date fields in ISO8601 format"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; First create an event
        (execute-query schema
                       "mutation {
                         createEvent(input: {
                           name: \"Summer Workshop\"
                           event_date: \"2024-07-20\"
                         }) {
                           id
                         }
                       }"
                       conn)
        ;; Then query it back
        (let [result (execute-query schema
                                    "{ event(name: \"Summer Workshop\") { id name event_date } }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "Summer Workshop"
                 (get-in result [:data :event :name]))
              "Returns correct name")
          (is (= "2024-07-20"
                 (get-in result [:data :event :event_date]))
              "Returns date in ISO8601 format (YYYY-MM-DD, 10 chars)")
          (is (= 10 (count (get-in result [:data :event :event_date])))
              "Date string is exactly 10 characters"))
        (finally
          (cleanup-database! conn))))))

;; T037: Test Event entity with Date fields - UPDATE mutation
(deftest update-event-date-test
  (testing "Update Event Date field"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Create event
        (let [create-result (execute-query schema
                                           "mutation {
                                             createEvent(input: {
                                               name: \"Product Launch\"
                                               event_date: \"2024-08-10\"
                                             }) {
                                               id
                                             }
                                           }"
                                           conn)
              event-id (get-in create-result [:data :createEvent :id])]
          ;; Update the event date
          (let [update-result (execute-query schema
                                             (str "mutation {
                                                    updateEvent(input: {
                                                      id: \"" event-id "\"
                                                      event_date: \"2024-09-15\"
                                                    }) {
                                                      id
                                                      name
                                                      event_date
                                                    }
                                                  }")
                                             conn)]
            (is (nil? (:errors update-result)) "No errors in update")
            (is (= "2024-09-15"
                   (get-in update-result [:data :updateEvent :event_date]))
                "Date is updated correctly")))
        (finally
          (cleanup-database! conn))))))

;; T038: Test Date validation - invalid formats
(deftest date-validation-test
  (testing "Reject invalid date formats"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Test invalid format (slash-separated)
        (let [result (execute-query schema
                                    "mutation {
                                      createEvent(input: {
                                        name: \"Invalid Event\"
                                        event_date: \"2024/06/15\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for invalid format"))

        ;; Test invalid date value (Feb 30)
        (let [result (execute-query schema
                                    "mutation {
                                      createEvent(input: {
                                        name: \"Invalid Event\"
                                        event_date: \"2024-02-30\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for invalid date value"))
        (finally
          (cleanup-database! conn))))))

;; ============================================================================
;; User Story 2: DateTime Field Support Integration Tests (T056-T060)
;; ============================================================================

;; T056, T057: Test Task entity with DateTime fields - CREATE mutation
(deftest create-task-with-datetime-test
  (testing "Create Task with DateTime fields using ISO8601 format with timezone"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Complete project documentation\"
                                        status: \"in-progress\"
                                        priority: \"high\"
                                        created_at: \"2024-01-24T14:30:00+05:00\"
                                        updated_at: \"2024-01-24T14:30:00+05:00\"
                                        scheduled_for: \"2024-01-31T09:00:00-08:00\"
                                      }) {
                                        id
                                        title
                                        created_at
                                        updated_at
                                        scheduled_for
                                      }
                                    }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (some? (get-in result [:data :createTask :id]))
              "Returns generated UUID")
          (is (= "Complete project documentation"
                 (get-in result [:data :createTask :title]))
              "Returns correct title")
          ;; Note: DateTime values are stored as UTC Instant and serialized back as UTC
          ;; The heuristic in transformer converts non-midnight Instant to DateTime string
          (is (string? (get-in result [:data :createTask :created_at]))
              "Returns created_at as string")
          (is (string? (get-in result [:data :createTask :updated_at]))
              "Returns updated_at as string")
          (is (string? (get-in result [:data :createTask :scheduled_for]))
              "Returns scheduled_for as string"))
        (finally
          (cleanup-database! conn))))))


;; T058: Test querying tasks with DateTime fields
(deftest query-task-with-datetime-test
  (testing "Query Task returns DateTime fields in ISO8601 format with timezone"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; First create a task
        (execute-query schema
                       "mutation {
                         createTask(input: {
                           title: \"Review pull request\"
                           status: \"draft\"
                           priority: \"medium\"
                           created_at: \"2024-01-24T10:00:00Z\"
                           updated_at: \"2024-01-24T10:00:00Z\"
                         }) {
                           id
                         }
                       }"
                       conn)
        ;; Then query it back
        (let [result (execute-query schema
                                    "{ task(title: \"Review pull request\") { id title created_at updated_at } }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "Review pull request"
                 (get-in result [:data :task :title]))
              "Returns correct title")
          (is (string? (get-in result [:data :task :created_at]))
              "Returns created_at as ISO8601 string")
          (is (string? (get-in result [:data :task :updated_at]))
              "Returns updated_at as ISO8601 string")
          ;; Verify format includes timezone (Z or ±HH:MM)
          (let [created-at (get-in result [:data :task :created_at])]
            (is (or (re-find #"Z$" created-at)
                    (re-find #"[+-]\d{2}:\d{2}$" created-at))
                "DateTime string includes timezone")))
        (finally
          (cleanup-database! conn))))))

;; T059: Test timezone preservation (note: current implementation converts to UTC)
(deftest datetime-timezone-handling-test
  (testing "DateTime values are handled correctly with timezone information"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Create task with specific timezone
        (let [create-result (execute-query schema
                                           "mutation {
                                             createTask(input: {
                                               title: \"Timezone test\"
                                               status: \"draft\"
                                               priority: \"low\"
                                               created_at: \"2024-01-24T14:30:00+05:00\"
                                               updated_at: \"2024-01-24T14:30:00+05:00\"
                                             }) {
                                               id
                                               created_at
                                             }
                                           }"
                                           conn)]
          (is (nil? (:errors create-result)) "No errors in create")
          ;; Note: Current implementation stores as UTC Instant
          ;; The returned value will be in UTC timezone
          (is (string? (get-in create-result [:data :createTask :created_at]))
              "Returns datetime as string"))
        (finally
          (cleanup-database! conn))))))

;; T060: Test DateTime validation - missing timezone and invalid values
(deftest datetime-validation-test
  (testing "Reject datetime without timezone"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Test missing timezone
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Invalid Task\"
                                        status: \"draft\"
                                        priority: \"low\"
                                        created_at: \"2024-01-24T14:30:00\"
                                        updated_at: \"2024-01-24T14:30:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for missing timezone"))

        ;; Test invalid time values
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Invalid Task\"
                                        status: \"draft\"
                                        priority: \"low\"
                                        created_at: \"2024-01-24T25:00:00+00:00\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for invalid hour"))

        ;; Test invalid timezone offset
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Invalid Task\"
                                        created_at: \"2024-01-24T14:30:00+99:99\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for invalid timezone offset"))
        (finally
          (cleanup-database! conn))))))

;; ============================================================================
;; User Story 3: Enum Field Support Integration Tests (T075-T079)
;; ============================================================================

;; T075, T076: Test Task entity with Enum fields - CREATE mutation
(deftest create-task-with-enum-test
  (testing "Create Task with Enum fields (status, priority)"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Implement enum support\"
                                        status: \"in_progress\"
                                        priority: \"high\"
                                        created_at: \"2024-01-24T14:30:00+00:00\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                        title
                                        status
                                        priority
                                      }
                                    }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (some? (get-in result [:data :createTask :id]))
              "Returns generated UUID")
          (is (= "Implement enum support"
                 (get-in result [:data :createTask :title]))
              "Returns correct title")
          (is (= "in_progress"
                 (get-in result [:data :createTask :status]))
              "Returns status as string")
          (is (= "high"
                 (get-in result [:data :createTask :priority]))
              "Returns priority as string"))
        (finally
          (cleanup-database! conn))))))

;; T077: Test querying tasks with Enum fields
(deftest query-task-with-enum-test
  (testing "Query Task returns Enum fields as strings"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; First create a task
        (execute-query schema
                       "mutation {
                         createTask(input: {
                           title: \"Review code\"
                           status: \"draft\"
                           priority: \"medium\"
                           created_at: \"2024-01-24T10:00:00Z\"
                           updated_at: \"2024-01-24T10:00:00Z\"
                         }) {
                           id
                         }
                       }"
                       conn)
        ;; Then query it back
        (let [result (execute-query schema
                                    "{ task(title: \"Review code\") { id title status priority } }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "Review code"
                 (get-in result [:data :task :title]))
              "Returns correct title")
          (is (= "draft"
                 (get-in result [:data :task :status]))
              "Returns status as string")
          (is (= "medium"
                 (get-in result [:data :task :priority]))
              "Returns priority as string"))
        (finally
          (cleanup-database! conn))))))

;; T078: Test Enum validation - invalid values and case mismatches
(deftest enum-validation-test
  (testing "Reject invalid enum values with helpful error messages"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Test completely invalid value
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Invalid Task\"
                                        status: \"unknown\"
                                        priority: \"low\"
                                        created_at: \"2024-01-24T14:30:00+00:00\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for invalid enum value")
          ;; Note: Error message validation would require checking the actual error message
          ;; which depends on how Lacinia/Malli validation errors are formatted
          )

        ;; Test case mismatch (should suggest correct case)
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Case Mismatch Task\"
                                        status: \"Draft\"
                                        priority: \"low\"
                                        created_at: \"2024-01-24T14:30:00+00:00\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for case mismatch")
          ;; The error message should suggest "draft" instead of "Draft"
          )

        ;; Test uppercase case mismatch
        (let [result (execute-query schema
                                    "mutation {
                                      createTask(input: {
                                        title: \"Uppercase Task\"
                                        status: \"draft\"
                                        priority: \"HIGH\"
                                        created_at: \"2024-01-24T14:30:00+00:00\"
                                        updated_at: \"2024-01-24T14:30:00+00:00\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for uppercase priority")
          ;; The error message should suggest "high" instead of "HIGH"
          )
        (finally
          (cleanup-database! conn))))))

;; T079: Run all enum integration tests
(deftest all-enum-features-test
  (testing "Complete enum workflow - create, query, validate"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Create task with all enum values
        (let [create-result (execute-query schema
                                           "mutation {
                                             createTask(input: {
                                               title: \"Complete enum test\"
                                               status: \"completed\"
                                               priority: \"urgent\"
                                               created_at: \"2024-01-24T14:30:00+00:00\"
                                               updated_at: \"2024-01-24T14:30:00+00:00\"
                                             }) {
                                               id
                                               title
                                               status
                                               priority
                                             }
                                           }"
                                           conn)]
          (is (nil? (:errors create-result)) "No errors in create")
          (is (= "completed" (get-in create-result [:data :createTask :status]))
              "Status is serialized as string")
          (is (= "urgent" (get-in create-result [:data :createTask :priority]))
              "Priority is serialized as string"))

        ;; Query back and verify
        (let [query-result (execute-query schema
                                          "{ task(title: \"Complete enum test\") { status priority } }"
                                          conn)]
          (is (nil? (:errors query-result)) "No errors in query")
          (is (= "completed" (get-in query-result [:data :task :status]))
              "Status persists correctly")
          (is (= "urgent" (get-in query-result [:data :task :priority]))
              "Priority persists correctly"))
        (finally
          (cleanup-database! conn))))))

;; ============================================================================
;; User Story 4: Decimal Number Support Integration Tests (T096-T101)
;; ============================================================================

;; T096: Product entity schema with decimal fields (defined at top of file)

;; T097: Test createProduct mutation with decimal values
(deftest create-product-with-decimal-test
  (testing "Create Product with Decimal fields (price, weight)"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        (let [result (execute-query schema
                                    "mutation {
                                      createProduct(input: {
                                        name: \"Premium Widget\"
                                        price: \"19.99\"
                                        weight_kg: \"2.5\"
                                      }) {
                                        id
                                        name
                                        price
                                        weight_kg
                                      }
                                    }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (some? (get-in result [:data :createProduct :id]))
              "Returns generated UUID")
          (is (= "Premium Widget"
                 (get-in result [:data :createProduct :name]))
              "Returns correct name")
          (is (= "19.99"
                 (get-in result [:data :createProduct :price]))
              "Returns price as string with full precision")
          (is (= "2.5"
                 (get-in result [:data :createProduct :weight_kg]))
              "Returns weight as string with full precision"))
        (finally
          (cleanup-database! conn))))))

;; T098: Test querying products with decimal fields
(deftest query-product-with-decimal-test
  (testing "Query Product returns Decimal fields as strings"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; First create a product
        (execute-query schema
                       "mutation {
                         createProduct(input: {
                           name: \"Standard Widget\"
                           price: \"9.99\"
                           weight_kg: \"1.25\"
                         }) {
                           id
                         }
                       }"
                       conn)
        ;; Then query it back
        (let [result (execute-query schema
                                    "{ product(name: \"Standard Widget\") { id name price weight_kg } }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "Standard Widget"
                 (get-in result [:data :product :name]))
              "Returns correct name")
          (is (= "9.99"
                 (get-in result [:data :product :price]))
              "Returns price as string")
          (is (= "1.25"
                 (get-in result [:data :product :weight_kg]))
              "Returns weight as string"))
        (finally
          (cleanup-database! conn))))))

;; T099: Test decimal precision preservation
(deftest decimal-precision-preservation-test
  (testing "Maintain full precision through create and query"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Create product with high-precision decimal
        (execute-query schema
                       "mutation {
                         createProduct(input: {
                           name: \"Precision Test\"
                           price: \"1234567890.1234567890\"
                           weight_kg: \"0.0000000001\"
                         }) {
                           id
                         }
                       }"
                       conn)
        ;; Query back and verify precision
        (let [result (execute-query schema
                                    "{ product(name: \"Precision Test\") { price weight_kg } }"
                                    conn)]
          (is (nil? (:errors result)) "No errors in response")
          (is (= "1234567890.1234567890"
                 (get-in result [:data :product :price]))
              "Price precision preserved")
          (is (= "0.0000000001"
                 (get-in result [:data :product :weight_kg]))
              "Weight precision preserved"))
        (finally
          (cleanup-database! conn))))))

;; T100: Test decimal precision/scale limit violations
(deftest decimal-limit-violations-test
  (testing "Reject decimal values exceeding precision/scale limits"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Test precision limit (39 digits - should fail)
        (let [result (execute-query schema
                                    "mutation {
                                      createProduct(input: {
                                        name: \"Too Many Digits\"
                                        price: \"123456789012345678901234567890123456789\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for precision violation"))

        ;; Test scale limit (11 decimal places - should fail)
        (let [result (execute-query schema
                                    "mutation {
                                      createProduct(input: {
                                        name: \"Too Many Decimals\"
                                        price: \"1.12345678901\"
                                      }) {
                                        id
                                      }
                                    }"
                                    conn)]
          (is (some? (:errors result)) "Should have errors for scale violation"))
        (finally
          (cleanup-database! conn))))))

;; T101: Run all decimal integration tests
(deftest all-decimal-features-test
  (testing "Complete decimal workflow - create, query, precision preservation"
    (let [conn (create-test-database!)
          schema (create-test-schema conn)]
      (try
        ;; Create product with various decimal values
        (let [create-result (execute-query schema
                                           "mutation {
                                             createProduct(input: {
                                               name: \"Complete Test\"
                                               price: \"99.99\"
                                               weight_kg: \"5.5\"
                                             }) {
                                               id
                                               name
                                               price
                                               weight_kg
                                             }
                                           }"
                                           conn)]
          (is (nil? (:errors create-result)) "No errors in create")
          (is (= "99.99" (get-in create-result [:data :createProduct :price]))
              "Price is serialized as string")
          (is (= "5.5" (get-in create-result [:data :createProduct :weight_kg]))
              "Weight is serialized as string"))

        ;; Query back and verify
        (let [query-result (execute-query schema
                                          "{ product(name: \"Complete Test\") { price weight_kg } }"
                                          conn)]
          (is (nil? (:errors query-result)) "No errors in query")
          (is (= "99.99" (get-in query-result [:data :product :price]))
              "Price persists correctly")
          (is (= "5.5" (get-in query-result [:data :product :weight_kg]))
              "Weight persists correctly"))
        (finally
          (cleanup-database! conn))))))

