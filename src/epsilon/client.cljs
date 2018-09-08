(ns epsilon.client
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax :refer [GET POST]]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [clojure.string :as str]
            [epsilon.hiccup :as hiccup]
            [epsilon.icons.chevrons-left]
            [epsilon.icons.refresh-cw]
            [epsilon.icons.delete]
            [epsilon.icons.search]
            [epsilon.icons.tag]
            [epsilon.icons.user]
            [epsilon.logo]

            ))

;; some day, my son, all this will be user-selectable
(def preferred-multipart-alternative "text/html")


(defn inner-html [s]
  {:dangerouslySetInnerHTML {:__html s}})

(defn html-entity
  ([name attrs]
   [:span (merge attrs (inner-html (str name ";")))])
  ([name] (html-entity name {})))


(defn merge-attrs [[name attrs & kids] more-attrs]
  (let [attrs (merge attrs more-attrs)
        vb (if-let [overridden (get attrs :view-box)]
             (str/join " " overridden)
             (get :viewBox attrs))
        attrs (assoc attrs :viewBox vb)]
    (into [name attrs] kids)))

;; -- Domino 1 - Event Dispatch -----------------------------------------------


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db
  :initialize
  (fn [_ _]
    {:search-term ""
     :suggestions []
     }))

(defn urlable-term [term]
  (if (string? (first term))
    (str (get {"address" "from"} (first term) (first term)) ":" (second term))
    term))

(rf/reg-event-fx
 :search-requested
 (fn [{:keys [db]} [_ term offset]]
   {:db (-> db (dissoc :show-suggestions :thread-id :thread :search-result))
    :http-xhrio
    {:method          :get
     :uri             "/search"
     :params 	      {:q term :limit 25}
     :format          (ajax/url-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [:search-result]
     :on-failure      [:search-error]}
    :dispatch [:search-term-updated term]
    }))

(rf/reg-event-fx
 :search-term-updated
 (fn [{:keys [db]} [_ term]]
   {:db (-> db (assoc :search-term term))
    :http-xhrio
    {:method          :get
     :uri             "/completions"
     :params 	        {:q term :limit 10}
     :format          (ajax/url-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [:new-suggestions-received]
     :on-failure      [:new-suggestions-received-error]}}))


(rf/reg-event-db
 :show-suggestions
 (fn [db [_ val]]
   (assoc db :show-suggestions val)))

(rf/reg-event-db
 :search-result
 (fn [db [_ result]]
   (assoc db :search-result result)))

(rf/reg-event-db
 :search-error
 (fn [db [_ error]]
   (.log js/console error)
   (assoc db :search-error error)))

(rf/reg-event-db
 :new-suggestions-received
 (fn [db [_ result]]
   (assoc db :suggestions result)))

(rf/reg-event-db
 :new-suggestions-received-error
 (fn [db [_ err]]
   (println "suggestions error" err)
   db))

(rf/reg-event-fx
 :view-thread-requested
 (fn [{:keys [db]} [_ thread-id]]
   {:db (assoc db :thread-id thread-id)
    :http-xhrio {:method          :get
                 :uri             "/show"
                 :params 	  {:id thread-id}
                 :format          (ajax/url-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [:thread-retrieved]
                 :on-failure      [:thread-retrieve-failed]}}))

(rf/reg-event-fx
  :remove-tag
  (fn [{:keys [db]} [_ index tag]]
    (let [m (get-in db [:thread index])]
      (when (get-in m [:tags tag])
        {:db (update-in db [:thread index :tags] disj tag)
         :http-xhrio {:method :delete
                      :uri  (str "/messages/" (js/encodeURIComponent (:id m)) "/tags/" tag)
                      :format (ajax/url-request-format)
                      :response-format (ajax/json-response-format {:keywords? true})
                      :on-success      [:xhr-succeeded]
                      :on-failure      [:xhr-failed]}}))))

(rf/reg-event-db
 :xhr-succeeded
 (fn [db [_ result]]
   (println result)
   db))

(rf/reg-event-db
 :xhr-failed
 (fn [db [_ result]]
   (println "failed" result)
   db))

(defn unnest-thread [flattened [frst & replies]]
  (let [flattened (conj flattened frst)]
    (reduce (fn [a r] (unnest-thread a r)) flattened (first replies))))

(rf/reg-event-db
 :thread-retrieved
 (fn [db [_ result]]
   (if result
     (let [flattened (unnest-thread [] (-> result first first))]
       (assoc db :thread (mapv #(assoc % :tags (set (:tags %)))  flattened)))
     (assoc db :thread nil))))

(rf/reg-event-db
 :thread-retrieve-failed
 (fn [db [_ error]]
   (.log js/console error)
   (assoc db :error error)))


;; -- Domino 4 - Query  -------------------------------------------------------

(rf/reg-sub
  :search-term
  (fn [db _]
    (:search-term db)))

(rf/reg-sub
  :show-suggestions
  (fn [db _]
    (:show-suggestions db)))

(rf/reg-sub
  :suggestions
  (fn [db _]
    (:suggestions db)))

(rf/reg-sub
  :search-result
  (fn [db _]
    (:search-result db)))

(rf/reg-sub
  :thread
  (fn [db _]
    (:thread db)))

(rf/reg-sub
 :thread-subject
 (fn [db _]
   (:Subject (:headers (first (first (first (:thread db))))))))



;; -- Domino 5 - View Functions ----------------------------------------------

(defn search-term-input
  []
  (let [term  @(rf/subscribe [:search-term])]
    [:div.search-term-input
     [:form {:on-submit
             (fn [e]
               (rf/dispatch [:search-requested term 0])
               (.stopPropagation e)
               (.preventDefault e))}
      [:div.widget ;{:style {:position "relative"}}
       [:input {:type "text"
                :placeholder "Search messages"
                :auto-complete "off"
                :value term
                :on-change #(rf/dispatch [:search-term-updated (-> % .-target .-value)])
                }]
       [:span {:style {:position "absolute" :top "4px" :right "0.2em"}}
        (merge-attrs
         epsilon.icons.search/svg
         {:style {:color "grey"} :view-box [2 2 24 24] :height 30 :width 30})]]
      [:span {:on-click #(rf/dispatch [:search-term-updated ""])}
       (html-entity "&nbsp") (html-entity "&nbsp")
       (merge-attrs epsilon.icons.delete/svg {:view-box [0 2 30 20] :height 40 :width 34})
       (html-entity "&nbsp")
       ]]]))

(defmulti html-for-suggestion (fn [[key value]] key))
(defmethod html-for-suggestion "address" [[key value]]
  [:span
   (merge epsilon.icons.user/svg {:width 18 :height 18}) value])
(defmethod html-for-suggestion "tag" [[key value]]
  [:span
   (merge epsilon.icons.tag/svg {:width 18 :height 18}) value])

(defmethod html-for-suggestion :default [[key value]]
  value)

(defn suggestions
  []
  [:div.taglist
   [:ul
    (map (fn [suggestion]
           [:li
            {:key suggestion
             :on-click
             (fn [e]
               (rf/dispatch [:show-suggestions false])
               (rf/dispatch [:search-requested (urlable-term suggestion) 0]))
             }
            (html-for-suggestion suggestion)])
         @(rf/subscribe [:suggestions]))]])

(defn el-for-tag
  ([text] (el-for-tag text nil))
  ([text onclick] [:div.tag {:on-click onclick} [:span.angle] [:span.name [:span text]]]))

(defn search-result
  []
  (let [rs @(rf/subscribe [:search-result])]
    [:div.search-results
     (map (fn [r]
            [(if (some #{"unread"} (:tags r)) :div.result.unread :div.result)
             {:key (:thread r)
              :on-click #(rf/dispatch [:view-thread-requested (:thread r)])}
             [:div.subject
              [:span.subject (:subject r)]
              (into [:span.tags.threadview] (map el-for-tag (:tags r)))]
             [:div
              [:span.authors (:authors r)]
              [:span.when [:div (:date_relative r)]]]])
          rs)]))

(defmulti render-message-part (fn [message part] (:content-type part)))

(defmethod render-message-part "multipart/alternative" [m p]
  (let [c (:content p)
        preferred (first (filter #(= (:content-type %) preferred-multipart-alternative) c))]
    (render-message-part m preferred)))

(defmethod render-message-part "multipart/mixed" [m p]
  (let [c (:content p)]
    (into [:div] (map (partial render-message-part m) c))))

(defmethod render-message-part "text/plain" [m p]
  [:div {} [:pre (:content p)]])

;; FIXME Probably we should do HTML parsing when the message is fetched, not every time
;; we render it.

(defmethod render-message-part "text/html" [m p]
  (let [[_ attrs & content]  (hiccup/parse-html (:content p))]
    (into [:div attrs] content)))

(defn download-link [part message-id]
  [:div.attachment {:key (:id part)}
   [:a {:href (str "/raw?"
                   "id=" message-id "&"
                   "content-type=" (:content-type part) "&"
                   "part=" (:id part))
        :target "_download"} "Download " (:content-type part) " attachment " (:filename part)]])

(defmethod render-message-part "application/octet-stream" [m p]
  (download-link p (:id m)))

(defmethod render-message-part "application/pdf" [m p]
  (download-link p (:id m)))


(defmethod render-message-part "multipart/related" [m p]
  (when-let [s (:start p)]
    (println "ignoring start param " s))
  (render-message-part m (first (:content p))))

(defmethod render-message-part :default [m p]
  [:div {:key (:id p)} [:pre "mime type " (:content-type p) " not supported"]])

(def r-arrow (html-entity "&rarr"))

;; XXX this is not 100% according to rfc 282x or whatever it is now.  More like 30%
(defn parse-email-address [address]
  (if address
    (let [angles-re #"<([^\>]*)>"
          parens-re #"\([^\)]*\)"]
      (if-let [addr-spec (second (re-find angles-re address))]
        {:addr-spec addr-spec
         :name (str/trim (str/replace address angles-re ""))}
        (if-let [name (second (re-find parens-re address))]
          {:addr-spec (str/trim (str/replace address parens-re ""))
           :name name}
          {:addr-spec address :name address})))))

(defn el-for-email-address [address]
  (let [parsed (or (parse-email-address address) {})]
    (if (empty? (:addr-spec parsed))
      [:span "(none)"]
      [:a.email-address
       {:href (str "mailto:" (:addr-spec parsed))
        :title address}
       (:name parsed)])))


(defn render-message [m index]
  [:div.message {:key (:id m)
                 :data-num index
                 :data-mid (:id m)}
   (let [h (:headers m)]
     [:div.headers.compact-headers
      [:div
       (el-for-email-address (:From h))
       " " r-arrow " "
       (el-for-email-address (:To h))]
      [:div (:Date h)]
      (into [:div.tags.headers {}]
            (map #(el-for-tag % (fn [e] #_ (rf/dispatch [:remove-tag index %])))
                 (:tags m)))])
   (into [:div.message-body {}] (map (partial render-message-part m) (:body m)))])

(defn render-thread [messages]
  [:div.thread {:key (str "th:" (:id (first messages)))}
   (map render-message messages (range))])

;; XXX this may change?  e.g. if desktop user resizes window, or mobile user
;; rotates the device
(def viewport-size
  [(or (.-innerWidth js/window) (.. js/document documentElement clientWidth))
   (or (.-innerHeight js/window) (.. js/document documentElement clientHeight))])

(defn bounding-rect [el]
  (let [bounding (.getBoundingClientRect el)]
    [(.-left bounding) (.-top bounding)
     (.-right bounding) (.-bottom bounding)]))

(defn in-viewport? [el]
  (let [[_ top _ bottom] (bounding-rect el)
        [_ height] viewport-size]
    (or
     (and (> top 0) (< bottom height)) ; within viewport
     (and (< top 0) (> bottom 0))      ; crosses upper edge of viewport
     (and (< top height) (> bottom height)) ; crosses lower edge of viewport
     )))


(defn thread-pane
  []
  (let [m @(rf/subscribe [:thread])]
    (render-thread m)))

(defn menu [title & items]
  [:div.titleblock {}
   (into [:div.menu {}] items)
   title])

(defn search-page []
  [:div
   (menu [:span.title "Epsilon"]
         [:div.item.clickable {:key :home :on-click #(rf/dispatch [:search-term-updated ""])}
          (merge-attrs epsilon.logo/svg {:width 30 :height 30})]
         [:div.item.clickable
          {:key :refresh
           :on-click
           #(rf/dispatch
             [:search-requested @(rf/subscribe [:search-term])])}
          (merge-attrs epsilon.icons.refresh-cw.svg {:view-box [0 0 25 25] :width 30 :height 30})])
   [:div.content
    [:div.search
     {:on-focus #(rf/dispatch [:show-suggestions true])
      :tabIndex -1
      :on-blur #(do
                  (rf/dispatch [:show-suggestions false])
                  (println "blurr"))}
     [search-term-input]
     (if @(rf/subscribe [:show-suggestions]) [suggestions])]
    [:div {:id "threads"}
     [search-result]]]])

(defn remove-unread-marks [thread-el]
  (let [message-els (.getElementsByClassName thread-el "message")
        visible-els (filter in-viewport? (array-seq message-els))]
    (run! #(rf/dispatch [:remove-tag (js/parseInt (.. % -dataset -num)) "unread"])
          visible-els)))

(defn thread-page []
  [:div
   (menu
    [:span {:style {:font-size "16px" :vertical-align :top
                    :display "inline-block"
                    :padding-top "4px"
                    :padding-left "22px"
                    :margin-top "4px" }}
     @(rf/subscribe [:thread-subject])]
    [:div.item.clickable {:key :back :on-click #(rf/dispatch [:thread-retrieved nil])}
     (merge-attrs epsilon.icons.chevrons-left/svg {:view-box [5 4 18 18] :width 30 :height 30})])
   [:div.thread.content
    {:on-scroll #(remove-unread-marks (.-target %))}
    [thread-pane]]])


(defn ui
  []
  (if @(rf/subscribe [:thread])
    (thread-page)
    (search-page)))



;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (rf/dispatch-sync [:search-requested "date:yesterday.." 0])
;  (rf/dispatch [:view-thread-requested "00000000000067cd"])
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app")))
