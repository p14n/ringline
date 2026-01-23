(ns user
  "REPL utilities for development"
  (:require [clojure.tools.namespace.repl :as repl]))

(defn refresh
  "Reload modified namespaces"
  []
  (repl/refresh))

(defn refresh-all
  "Reload all namespaces"
  []
  (repl/refresh-all))

(comment
  ;; REPL workflow examples
  
  ;; Reload changed namespaces
  (refresh)
  
  ;; Reload all namespaces
  (refresh-all)
  
  ;; Set refresh directories (if needed)
  (repl/set-refresh-dirs "src" "dev")
  
  )

