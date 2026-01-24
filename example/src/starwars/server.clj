(ns starwars.server
  "HTTP GraphQL server using Ring, Reitit, and Jetty.

   This example demonstrates:
   - Integrating Lacinia GraphQL with Ring HTTP handlers
   - Using Reitit for HTTP routing
   - Running a Jetty server
   - Handling GraphQL queries and mutations over HTTP
   - Proper JSON request/response handling"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as response]
            [reitit.ring :as reitit-ring]
            [com.walmartlabs.lacinia :as lacinia]
            [starwars.core :as core])
  (:gen-class))

;; Global state for database connection and schema
(defonce server-state (atom {:conn nil
                             :schema nil
                             :server nil}))

(defn init-database!
  "Initialize Datomic database and GraphQL schema"
  []
  (let [conn (core/create-database!)
        schema (core/create-schema conn)]
    (swap! server-state assoc :conn conn :schema schema)
    (println "Database and schema initialized")))

(defn shutdown-database!
  "Shutdown database connection"
  []
  (when-let [conn (:conn @server-state)]
    (core/cleanup-database! conn)
    (swap! server-state assoc :conn nil :schema nil)
    (println "Database connection closed")))

;; GraphQL HTTP Handler

(defn graphql-handler
  "Handle GraphQL queries and mutations over HTTP.

   Expects POST requests with JSON body containing:
   - query: GraphQL query string (required)
   - variables: GraphQL variables map (optional)
   - operationName: Operation name for multi-operation documents (optional)"
  [request]
  (let [schema (:schema @server-state)
        conn (:conn @server-state)
        body (:body request)
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
        (response/response result)))))

;; GraphiQL Handler (simple HTML interface for testing)

(def graphiql-html
  "GraphiQL 5 interface using modern ESM CDN approach for testing GraphQL queries in the browser"
  "<!doctype html>
<html lang=\"en\">
  <head>
    <meta charset=\"UTF-8\" />
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
    <title>GraphiQL - Ringline User CRUD Example</title>
    <style>
      body {
        margin: 0;
      }

      #graphiql {
        height: 100dvh;
      }

      .loading {
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 4rem;
      }
    </style>
    <link rel=\"stylesheet\" href=\"https://esm.sh/graphiql/dist/style.css\" />
    <script type=\"importmap\">
      {
        \"imports\": {
          \"react\": \"https://esm.sh/react@19.1.0\",
          \"react/\": \"https://esm.sh/react@19.1.0/\",

          \"react-dom\": \"https://esm.sh/react-dom@19.1.0\",
          \"react-dom/\": \"https://esm.sh/react-dom@19.1.0/\",

          \"graphiql\": \"https://esm.sh/graphiql?standalone&external=react,react-dom,@graphiql/react,graphql\",
          \"graphiql/\": \"https://esm.sh/graphiql/\",
          \"@graphiql/react\": \"https://esm.sh/@graphiql/react?standalone&external=react,react-dom,graphql,@graphiql/toolkit,@emotion/is-prop-valid\",

          \"@graphiql/toolkit\": \"https://esm.sh/@graphiql/toolkit?standalone&external=graphql\",
          \"graphql\": \"https://esm.sh/graphql@16.11.0\",
          \"@emotion/is-prop-valid\": \"data:text/javascript,\"
        }
      }
    </script>
    <script type=\"module\">
      import React from 'react';
      import ReactDOM from 'react-dom/client';
      import { GraphiQL, HISTORY_PLUGIN } from 'graphiql';
      import { createGraphiQLFetcher } from '@graphiql/toolkit';
      import 'graphiql/setup-workers/esm.sh';

      const fetcher = createGraphiQLFetcher({
        url: '/graphql',
      });
      const plugins = [HISTORY_PLUGIN];

      function App() {
        return React.createElement(GraphiQL, {
          fetcher,
          plugins,
          defaultEditorToolsVisibility: true,
        });
      }

      const container = document.getElementById('graphiql');
      const root = ReactDOM.createRoot(container);
      root.render(React.createElement(App));
    </script>
  </head>
  <body>
    <div id=\"graphiql\">
      <div class=\"loading\">Loading…</div>
    </div>
  </body>
</html>")

(defn graphiql-handler
  "Serve GraphiQL interface for interactive GraphQL exploration"
  [_request]
  (-> (response/response graphiql-html)
      (response/content-type "text/html")))

;; Health check handler

(defn health-handler
  "Health check endpoint"
  [_request]
  (response/response {:status "ok"
                      :database (if (:conn @server-state) "connected" "disconnected")
                      :schema (if (:schema @server-state) "loaded" "not-loaded")}))

;; Routes

(def routes
  "HTTP routes using Reitit"
  [["/graphql" {:post graphql-handler}]
   ["/graphiql" {:get graphiql-handler}]
   ["/health" {:get health-handler}]])

(def app
  "Ring handler with middleware"
  (-> (reitit-ring/ring-handler
       (reitit-ring/router routes)
       (reitit-ring/create-default-handler
        {:not-found (constantly (response/response {:error "Not found"}))}))
      (wrap-json-body {:keywords? true})
      wrap-json-response
      wrap-params))

;; Server lifecycle

(defn start-server!
  "Start the Jetty HTTP server"
  [& {:keys [port] :or {port 3000}}]
  (init-database!)
  (let [server (jetty/run-jetty #'app {:port port :join? false})]
    (swap! server-state assoc :server server)
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