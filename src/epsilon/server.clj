(ns epsilon.server
  (:require
   [aleph.http :as aleph]
   [cheshire.core :as json]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   ring.util.codec
   ring.util.response
   ))

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

(defmethod notmuch-args :search [_ {:keys [offset limit order-by]} & terms]
  (filter (complement empty?)
          (into
           ["search" "--format=json" "--output=summary"
            (and offset (str "--offset=" offset))
            (and limit (str "--limit=" limit))
            (and order-by ({:newest " --sort=newest-first"
                            :oldest " --sort=oldest-first"}
                           order-by))]
           terms)))

(defmethod notmuch-args :show [_ id]
  ["show" "--format=json" "--body=true" (str "thread:" id)])

(defn notmuch [& args]
  (println args)
  (apply shell/sh
         (str (System/getenv "HOME") "/.nix-profile/bin/notmuch")
         (apply notmuch-args  args)))

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
        ret (notmuch :show id)]
    (if (zero? (:exit ret))
      (jr (:out ret))
      (fail (assoc ret :error "notmuch returned non-zero")))))


(defn tag-handler [req] (jr {:tags []}))

(defn static-files-handler [req]
  (let [path (str/replace (:uri req) #"\A/target" "")]
    (ring.util.response/file-response path {:root "target"})))

(defn handler-by-uri [uri]
  (let [handlers
        [["/search" search-handler]
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
    (aleph/start-server #'handler config)))
