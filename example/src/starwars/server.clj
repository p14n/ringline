(ns starwars.server
  "HTTP GraphQL server using Ring, Reitit, and Jetty.

   This example demonstrates:
   - Integrating Lacinia GraphQL with Ring HTTP handlers
   - Using Reitit for HTTP routing
   - Running a Jetty server
   - Handling GraphQL queries and mutations over HTTP
   - Proper JSON request/response handling"
  (:require
   [reitit.ring :as reitit-ring]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.util.response :as response]
   [ringline.ring :as ringline-ring]
   [starwars.core :as core]
   [starwars.graphiql :as graphiql])
  (:gen-class))

;; Global state for database connection and schema
(defonce server-state (atom {:conn nil
                             :schema nil
                             :server nil}))

(defn shutdown-database!
  "Shutdown database connection"
  []
  (when-let [conn (:conn @server-state)]
    (core/cleanup-database! conn)
    (swap! server-state assoc :conn nil :schema nil)
    (println "Database connection closed")))

;; Health check handler

(defn health-handler
  "Health check endpoint"
  [_request]
  (response/response {:status "ok"
                      :database (if (:conn @server-state) "connected" "disconnected")
                      :schema (if (:schema @server-state) "loaded" "not-loaded")}))


(defn app
  "Ring handler with middleware"
  [schema conn]
  (-> (reitit-ring/ring-handler
       (reitit-ring/router [["/graphql" {:post (ringline-ring/create-graphql-handler schema conn)}]
                            ["/graphiql" {:get graphiql/graphiql-handler}]
                            ["/health" {:get health-handler}]])
       (reitit-ring/create-default-handler
        {:not-found (constantly (response/response {:error "Not found"}))}))
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-params))

;; Server lifecycle

(defn start-server!
  "Start the Jetty HTTP server"
  [& {:keys [port] :or {port 3000}}]
  (let [{:keys [schema conn]} (core/create-graphql-system!)
        server (jetty/run-jetty (app schema conn) {:port port :join? false})]
    (swap! server-state assoc :server server)
    (swap! server-state assoc :conn conn)
    (println (str "\n=== Ringline GraphQL Server Started ==="))
    (println (str "GraphQL endpoint: http://localhost:" port "/graphql"))
    (println (str "GraphiQL UI:      http://localhost:" port "/graphiql"))
    (println (str "Health check:     http://localhost:" port "/health"))
    (println "\nPress Ctrl+C to stop the server\n")
    server))

(defn stop-server!
  "Stop the Jetty HTTP server"
  []
  (when-let [server (:server @server-state)]
    (.stop server)
    (swap! server-state assoc :server nil)
    (println "Server stopped"))
  (shutdown-database!))

(defn -main
  "Main entry point - starts the HTTP server"
  [& args]
  (let [port (if (first args) (Integer/parseInt (first args)) 3000)]
    (start-server! :port port)
    ;; Keep the main thread alive
    @(promise)))