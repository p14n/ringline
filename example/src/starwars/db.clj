(ns starwars.db
  "In-memory database for User CRUD example.

   This simulates a Datomic database using atoms.
   In a real application, you would use actual Datomic."
  (:require [clojure.pprint :as pprint]))

;; In-memory storage using atoms
(defonce users (atom {}))

;; Initial data
(def initial-users
  {#uuid "00000000-0000-0000-0000-000000000001"
   {:id #uuid "00000000-0000-0000-0000-000000000001"
    :name "Alice Johnson"
    :email "alice@example.com"
    :age 30}

   #uuid "00000000-0000-0000-0000-000000000002"
   {:id #uuid "00000000-0000-0000-0000-000000000002"
    :name "Bob Smith"
    :email "bob@example.com"
    :age 25}

   #uuid "00000000-0000-0000-0000-000000000003"
   {:id #uuid "00000000-0000-0000-0000-000000000003"
    :name "Charlie Brown"
    :email "charlie@example.com"
    :age 35}})

;; Initialize database
(defn init-db!
  "Initialize the database with sample data"
  []
  (reset! users initial-users))

;; User CRUD operations
(defn get-all-users [] (vals @users))
(defn get-user [id] (get @users id))

(defn create-user! [data]
  (let [id (:id data)]
    (swap! users assoc id data)
    data))

(defn update-user! [id updates]
  (when (get @users id)
    (swap! users update id merge updates)
    (get @users id)))

(defn delete-user! [id]
  (when (get @users id)
    (swap! users dissoc id)
    true))

;; Print database state
(defn print-db-state
  "Print the current state of the database"
  []
  (println "\n=== Database State ===")
  (println "\nUsers:")
  (pprint/pprint @users))

