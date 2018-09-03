(ns hiccupize-icons
  (:require [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn xml->hiccup [el]
  (if (string? el)
    el
    (into [(keyword (name (:tag el))) (:attrs el)]
          (map xml->hiccup (:content el)))))

(defn basename [f]
  (second (re-find #"(.*)\.svg\Z" (.getName f))))

(defn convert [dir]
  (let [dir (io/file dir)
        fs (file-seq dir)]
    (run! (fn [file]
            (when  (.isFile file)
              (let [xml (xml/parse-str (slurp file))
                    n (basename file)
                    underscore-n (str/replace n "-" "_")
                    ns (symbol (str "epsilon.icons." n))
                    hiccup (xml->hiccup xml)]
                (with-open [out (io/writer (io/file "generated" "epsilon" "icons"
                                                    (str underscore-n ".cljs")))]
                  (binding [*out* out]
                    (prn `(~(symbol "ns") ~ns))
                    (prn `(def ~(symbol "svg") ~hiccup)))))))
          fs)))

(defn -main
  ([source-dir & args]
   (reduce (fn [m f] (let [g (io/file m f)] (.mkdir g) g))
           "."  ["generated" "epsilon" "icons"])
   (convert source-dir))
  ([] 
   (-main "node_modules/feather-icons/dist/icons/")))
