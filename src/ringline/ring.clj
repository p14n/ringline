(ns ringline.ring
  "Ring handlers for GraphQL API"
  (:require [com.walmartlabs.lacinia :as lacinia]
            [ring.util.response :as response]))

;; GraphQL HTTP Handler

(defn create-graphql-handler [schema conn]
  "Handle GraphQL queries and mutations over HTTP.

   Expects POST requests with JSON body containing:
   - query: GraphQL query string (required)
   - variables: GraphQL variables map (optional)
   - operationName: Operation name for multi-operation documents (optional)"
  (fn graphql-handler [request]
    (let [body (:body request)
          query (:query body)
          variables (:variables body)
          operation-name (:operationName body)]

      (if-not query
        (response/response {:errors [{:message "No query provided"}]})

        (let [result (lacinia/execute schema
                                      query
                                      variables
                                      {:conn conn
                                       :operation-name operation-name})]
          (response/response result))))))
