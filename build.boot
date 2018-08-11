(set-env!
  :resource-paths #{"src" "html"}
  :dependencies '[
                  [adzerk/boot-cljs "LATEST" :scope "test"]
                  [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                  [aleph "0.4.6"]
                  [cheshire "5.8.0"]
                  [cljs-ajax "0.7.4"]
                  [com.cemerick/piggieback "0.2.1"  :scope "test"]
                  [org.clojure/clojurescript "1.10.339"]
                  [re-frame "0.10.5"]
                  [ring/ring-core "1.7.0-RC1"]
                  [weasel "0.7.0"  :scope "test"]
                  [day8.re-frame/http-fx "0.1.6"]
                  [reagent  "0.7.0"]
                  [re-frame "0.10.5"]
                  ])


(require '[adzerk.boot-cljs          :refer [cljs]]
         '[adzerk.boot-cljs-repl     :refer [cljs-repl]])


(task-options!
 {:cljs {:optimizations :none ;:whitespace
         :output-dir "target/"
         }})
