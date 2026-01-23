(ns test-parser
  "Quick REPL test for parser functionality"
  (:require [ringline.schema.parser :as parser]
            [ringline.fixtures :as fixtures]))

(comment
  ;; Test parse-schema with user schema
  (def user-result (parser/parse-schema :user fixtures/user-schema))
  
  (println "User schema parsed:")
  (clojure.pprint/pprint user-result)
  
  ;; Test parse-schemas with multiple entities
  (def multi-result (parser/parse-schemas fixtures/test-schemas))
  
  (println "\nMultiple schemas parsed:")
  (clojure.pprint/pprint multi-result)
  
  ;; Check relationships
  (println "\nUser relationships:")
  (clojure.pprint/pprint (:relationships (first (filter #(= :user (:schema-name %)) multi-result))))
  
  (println "\nPost relationships:")
  (clojure.pprint/pprint (:relationships (first (filter #(= :post (:schema-name %)) multi-result))))
  
  )

