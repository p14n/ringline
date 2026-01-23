(ns ringline.core
  "Main namespace for the ringline application."
  (:require [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [malli.core :as m]
            [datomic.api :as d]))

(defn -main
  "Application entry point."
  [& args]
  (println "Ringline application started!")
  (println "Dependencies loaded:")
  (println "  - Lacinia (GraphQL)")
  (println "  - Malli (Data validation)")
  (println "  - Datomic (Database)"))

