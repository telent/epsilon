(set-env!
  :resource-paths #{"src" "build" "html" "generated"}
  :dependencies '[
                  [aleph "0.4.6"]
                  [cheshire "5.8.0"]
                  [cljs-ajax "0.7.4"]
                  [com.cemerick/piggieback "0.2.1"  :scope "test"]
                  [org.clojure/clojurescript "1.10.339"]
                  [org.clojure/data.xml "0.0.8"]
                  [re-frame "0.10.5"]
                  [ring/ring-core "1.7.0-RC1"]
                  [weasel "0.7.0"  :scope "test"]
                  [day8.re-frame/http-fx "0.1.6"]
                  [reagent  "0.7.0"]
                  [re-frame "0.10.5"]
                  ])


(require '[boot.tasks.cljs          :refer [cljs]])

(deftask use-target
  "Profile setup for running repl"
  []
  (set-env! :resource-paths #(conj % "target"))
  identity)

(task-options!
 {:cljs {:optimizations :whitespace
         :output-to "target/main.js"
         }})
