(def from-edn
  (let [d (read-string (slurp "deps.edn"))
        merged (merge (:deps d) (-> d :aliases :dev :extra-deps))]
    (reduce-kv (fn [a k v] (conj a [k (:mvn/version v)])) [] merged)))

(set-env!
  :resource-paths #{"src" "build" "html" "generated"}
  :dependencies from-edn)

(require '[boot.tasks.cljs :refer [cljs]])

(deftask use-target
  "Profile setup for running repl"
  []
  (set-env! :resource-paths #(conj % "target"))
  identity)

(task-options!
 repl {:init-ns 'epsilon.server}
 cljs {:optimizations :whitespace
       :output-to "target/main.js"
      })
