(ns starwars.server
  "HTTP GraphQL server using Ring, Reitit, and Jetty.

   This example demonstrates:
   - Integrating Lacinia GraphQL with Ring HTTP handlers
   - Using Reitit for HTTP routing
   - Running a Jetty server
   - Handling GraphQL queries and mutations over HTTP
   - Proper JSON request/response handling"
  (:require
   [datomic.api :as d]
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]
   [ringline.ring :as ringline-ring]
   [starwars.auto :as core]
   [starwars.graphiql :as graphiql])
  (:gen-class))

(defn cleanup-database!
  "Clean up the database connection"
  [conn db-uri]
  (d/release conn)
  (d/delete-database db-uri))


(defn app
  "Ring handler with middleware"
  [schema conn]
  (-> (reitit-ring/ring-handler
       (reitit-ring/router [["/graphql" {:post (ringline-ring/create-graphql-handler schema conn)}]
                            ["/graphiql" {:get graphiql/graphiql-handler}]])
       (reitit-ring/create-default-handler
        {:not-found (constantly (response/response {:error "Not found"}))}))
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-params))

;; Server lifecycle

(defn start-server!
  "Start the Jetty HTTP server"
  [start-graphql! & {:keys [port db-uri] :or {port 3000}}]
  (let [{:keys [schema conn]} (start-graphql! db-uri)
        server (jetty/run-jetty (app schema conn) {:port port :join? false})]
    (println (str "\n=== Ringline GraphQL Server Started ==="))
    (println (str "GraphQL endpoint: http://localhost:" port "/graphql"))
    (println (str "GraphiQL UI:      http://localhost:" port "/graphiql"))
    (println "\nPress Ctrl+C to stop the server\n")
    {:server server
     :conn conn
     :db-uri db-uri}))

(defn stop-server!
  "Stop the Jetty HTTP server"
  [{:keys [server conn db-uri]}]
  (.stop server)
  (cleanup-database! conn db-uri))

(defn -main
  "Main entry point - starts the HTTP server"
  [& args]
  (let [port (if (first args) (Integer/parseInt (first args)) 3000)]
    (start-server! core/create-graphql-system! :port port)
    ;; Keep the main thread alive
    @(promise)))