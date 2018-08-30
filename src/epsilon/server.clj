(ns epsilon.server
  (:require
   [aleph.http :as aleph]
   [aleph.netty :as netty]
   [cheshire.core :as json]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   ring.util.codec
   ring.util.response
   )
  (:gen-class))

(defn jr [v]
  {:status 200
   :headers {"content-type" "text/json"}
   :body v})

(defn fail [v]
  {:status 500
   :headers {"content-type" "text/json"}
   :body (json/generate-string v)})

(defn query-params [req]
  (let [m (ring.util.codec/form-decode (get req :query-string))]
    (if (map? m) m {})))

(defmulti notmuch-args (fn [command & args] command))

(defmethod notmuch-args :search [_ {:keys [offset output limit order-by]} & terms]
  (filter (complement empty?)
          (into
           ["search" "--format=json" (str "--output=" (or output "summary"))
            (and offset (str "--offset=" offset))
            (and limit (str "--limit=" limit))
            (and order-by ({:newest " --sort=newest-first"
                            :oldest " --sort=oldest-first"}
                           order-by))]
           terms)))


(defmethod notmuch-args :show [_ term {:keys [thread body include-html]}]
  ["show" "--format=json"
   (if body "--body=true" "--body=false")
   (if include-html "--include-html=true" "--include-html=false" )
   (if thread "--entire-thread=true" "--entire-thread=false")
   term])

(defmethod notmuch-args :raw [_ {:keys [message part]}]
  ["show" "--format=raw" (str "--part=" part) (str "id:" message)
   :out-enc :bytes])

(defn notmuch [& args]
  (let [args (apply notmuch-args  args)]
    (apply shell/sh
           (str (System/getenv "HOME") "/.nix-profile/bin/notmuch")
           args)))

(defn strip-prefix [prefix term]
  (if (.startsWith term (str prefix ":"))
    (.substring term (inc (.length prefix)))
    term))

(defn query-tags [term]
  (let [term (strip-prefix "tag" term)
        ret (notmuch :search {:limit 10 :offset 0 :output "tags"} "*")]
    (if (zero? (:exit ret))
      (let [tags (json/parse-string (:out ret))]
        (map #(str "tag:" %)
             (filter #(.startsWith % term) tags)))
      (assoc ret :error "notmuch returned non-zero"))))

(defn find-header [name tree]
  (if (or (seq? tree) (vector? tree))
    (map (partial find-header name) tree)
    (if-let [h (get tree "headers")]
      (get h name))))


(defn query-authors [term]
  (if (>  (.length term) 2)
    (let [ret (notmuch :show (str "from:" (strip-prefix "from" term))
                       {:limit 100 :thread false :body false})]
      (if (zero? (:exit ret))
        (let [s (json/parse-string (:out ret))]
          (map #(str "from:" %) (distinct (flatten (find-header "From" s)))))
        (assoc ret :error "notmuch returned non-zero")))
    []))


(defn completions-handler [req]
  (let [p (query-params req)
        term (get p "q")]
    (cond (.startsWith term "tag:")
          (let [r (query-tags term)]
            (if (:error r) (fail r) (jr (json/generate-string r))))
          (.startsWith term "from:")
          (let [r (query-authors term)]
            (if (:error r) (fail r) (jr (json/generate-string r))))
          true
          (let [tags (query-tags term)
                authors (query-authors term)]
            (cond (:error tags) (fail tags)
                  (:error authors) (fail authors)
                  true (jr (json/generate-string (concat tags authors))))))))

(defn search-handler [req]
  (let [p (query-params req)
        terms (get p "q")
        limit (get p "limit" 10)
        offset (get p "offset" 0)
        ret (notmuch :search {:limit limit :offset offset} terms)]
    (if (zero? (:exit ret))
      (jr (:out ret))
      (fail (assoc ret :error "notmuch returned non-zero")))))

(defn show-handler [req]
  (let [id (get (query-params req) "id")
        ret (notmuch :show (str "thread:" id) {:body true :include-html true :thread true})]
    (if (zero? (:exit ret))
      (jr (:out ret))
      (fail (assoc ret :error "notmuch returned non-zero")))))

(defn raw-handler [req]
  (let [id (get (query-params req) "id")
        part (get (query-params req) "part")
        content-type (get (query-params req) "content-type")
        ret (notmuch :raw {:message id :part part})]
    (if (zero? (:exit ret))
      {:headers {:content-type content-type
                 } :status 200 :body (:out ret)}
      (fail (assoc ret :error "notmuch returned non-zero")))))


(defn tag-handler [req] (jr {:tags []}))

(defn static-files-handler [req]
  (let [path (str/replace (:uri req) #"\A/target" "")]
    (ring.util.response/file-response path {:root "target"})))

(defn handler-by-uri [uri]
  (let [handlers
        [["/completions" completions-handler]
         ["/raw" raw-handler]
         ["/search" search-handler]
         ["/show" show-handler]
         ["/target" static-files-handler]
         ["/tag" tag-handler]]]
    (first (filter #(.startsWith uri (first %)) handlers))))

(defn handler [req]
  (println req)
  (let [[_ h] (handler-by-uri (:uri req))]
    (h req)))

(defn run [config]
  (let [config (merge {:port 8080} config)]
    (println "running with config " config)
    (aleph/start-server #'handler config)))

(defn -main [& args]
  (let [config (reduce (fn [m [k v]] (assoc m k v)) {} (partition 2 (map read-string args)))
        s (run config)]
    (netty/wait-for-close s)))
