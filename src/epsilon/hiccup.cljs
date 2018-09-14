(ns epsilon.hiccup
  (:require [clojure.string :as str]))

(defn el-attributes [el]
  (let [names (.getAttributeNames el)]
    (reduce (fn [a n] (assoc a (keyword n) (.getAttribute el n))) {} names)))

(def safe-tags
  #{
    :body
    :a :address :b :big :blockquote :br :center :cite :code :dd :div :dl :dt :em
    :font
    :h1 :h2 :h3 :h4 :h5 :h6
    :hr :i :img :lh :li :ol :p :pre :section :small :span :strong :sub :sup
    :table :tbody :td :th :tr
    :u :ul
    :wbr
    })


(defn check-url-scheme [url schemes]
  url)

(defn extract-styles [el]
  (let [props (.-style el)
        style-names (array-seq props)]
    (reduce (fn [m name] (assoc m (keyword name) (aget props name))) {} style-names)))

(defn allow-styles [h]
  ;; FIXME need to check if the value is an external url
  (or h {}))

(defmulti safe-attributes (fn [tag attrs] tag))

(defmethod safe-attributes :a [_ el {:keys [href name style]}]
  {:name name
   :style (allow-styles (extract-styles el))
   :href (check-url-scheme href #{:http :https :ftp})})

(def core-attributes
  [:accesskey :class :contenteditable :contextmenu :dir :draggable
   :hidden :id :lang :spellcheck :style :tabindex :title])
(def xml-attributes
  [:xml:lang])

(defn rewrite-keys [substitutions attrs]
  (reduce-kv (fn [m k v]
               (assoc m (get substitutions k k) v))
             {}
             attrs))


(def common-attributes (concat core-attributes xml-attributes))

(defmethod safe-attributes :font [_ el attrs] {})

(defmethod safe-attributes :table [_ el attrs]
  (let [a (select-keys attrs common-attributes)]
    (assoc a :style (allow-styles (extract-styles el)))))
(defmethod safe-attributes :tbody [_ el attrs]
  (let [a (select-keys attrs common-attributes)]
    (assoc a :style (allow-styles (extract-styles el)))))
(defmethod safe-attributes :tr [_ el attrs]
  (let [a (select-keys attrs common-attributes)]
    (assoc a :style (allow-styles (extract-styles el)))))
(defmethod safe-attributes :td [_ el attrs]
  (let [a (select-keys attrs (concat common-attributes [:colspan :rowspan :headers]))]
    (assoc (rewrite-keys {:colspan :colSpan, :rowspan :rowSpan} a)
           :style (allow-styles (extract-styles el)))))


(defmethod safe-attributes :img [_ el {:keys [src style]}]
  (let [style (if style (extract-styles el) {})]
    {:style (allow-styles style)
     :src (check-url-scheme src #{:http :https :ftp :data})}))

(defmethod safe-attributes :default [_ el {:keys [style]}]
  {
   :style (allow-styles (extract-styles el))
})

(defn node-type [el]
  (let [nt (.-nodeType el)]
    (cond
      (or (= nt (.-ELEMENT_NODE js/Node))
          (= nt (.-DOCUMENT_NODE js/Node)))
      :descend
      (= nt (.-TEXT_NODE js/Node))
      :text-content
      :else
      nil)))


(defn virtualize-dom-element [el]
  (case (node-type el)
    :descend
    (let [tag (keyword (str/lower-case (.-tagName el)))]
      (cond (safe-tags tag)
            (into [tag
                   (safe-attributes tag el (el-attributes el))]
                  (map virtualize-dom-element (array-seq (.-childNodes el))))
            (= (type el) js/HTMLUnknownElement)
            (into [:span.unknown {}]
                  (map virtualize-dom-element (array-seq (.-childNodes el))))
            true
            (do (.log js/console el)
                [:span {} "["  (.-tagName el) " redacted]"])))
    :text-content
    (.-textContent el)
    ""))

(defn parse-html [string]
  (let [dom (. (js/DOMParser.) parseFromString string "text/html")
        body (.-body dom)]
    (virtualize-dom-element body)))
