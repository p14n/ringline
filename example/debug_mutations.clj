(require '[starwars.schema :as schema]
         '[ringline.mutation.parser :as parser]
         '[ringline.mutation.lacinia :as lacinia]
         '[clojure.pprint :as pprint])

(println "\n=== Debugging Mutation Schemas ===\n")

(let [parsed (parser/parse-mutations :human schema/human-schema)
      result (lacinia/generate-mutation-schemas parsed)]
  (println "Parsed mutations:")
  (pprint/pprint parsed)
  (println "\nGenerated Lacinia schemas:")
  (pprint/pprint result))

