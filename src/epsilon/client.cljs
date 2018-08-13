(ns epsilon.client
  (:require [reagent.core :as reagent]
            [clojure.string :as str]
            [ajax.core :as ajax :refer [GET POST]]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [clojure.string :as str]))

;; -- Domino 1 - Event Dispatch -----------------------------------------------

#_(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (rf/dispatch [:timer now])))  ;; <-- dispatch used

;; Call the dispatching function every second.
;; `defonce` is like `def` but it ensures only one instance is ever
;; created in the face of figwheel hot-reloading of this file.
#_ (defonce do-timer (js/setInterval dispatch-timer-event 1000))


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db
  :initialize
  (fn [_ _]
    {:search-term ""
     }))

(rf/reg-event-fx
 :search-requested
 (fn [{:keys [db]} [_ term]]
   {:db (-> db
            (assoc :search-term term)
            (dissoc :thread-id :thread :search-result))
    :http-xhrio
    {:method          :get
     :uri             "/search"
     :params 	  {:q term :limit 10}
     :format          (ajax/url-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [:search-result]
     :on-failure      [:search-error]}}))

(rf/reg-event-db
 :show-completions
 (fn [db [_ val]]
   ;; XXX dispatch the autocomplete xhr req here
   (assoc db :show-completions val)))

(rf/reg-event-db
 :search-result
 (fn [db [_ result]]
   (assoc db :search-result result)))

(rf/reg-event-db
 :search-error
 (fn [db [_ error]]
   (.log js/console error)
   (assoc db :search-error error)))

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

(rf/reg-event-db
 :thread-retrieved
 (fn [db [_ result]]
   (assoc db :thread result)))

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
  :show-completions
  (fn [db _]
    (:show-completions db)))

(rf/reg-sub
  :search-result
  (fn [db _]
    (:search-result db)))

(rf/reg-sub
  :thread
  (fn [db _]
    (:thread db)))


;; -- Domino 5 - View Functions ----------------------------------------------

(defn search-term-input
  []
  [:div.search-term-input
   {:placeholder "Search messages"}
   [:input {:type "text"
            :auto-complete "off"
            :value @(rf/subscribe [:search-term])
            :on-change
            #(rf/dispatch [:search-requested (-> % .-target .-value)])
            }]])


(defn tags
  []
  [:div.taglist
   [:ul
    (map (fn [tag]
           [:li
            {:key tag
             :on-click
             (fn [e]
               (rf/dispatch [:show-completions false])
               (rf/dispatch [:search-requested (str "tag:" tag)]))
             }
            tag])
         ["inbox"
          "attachment"
          "deleted"
          "draft"
          "fb"
          "ham"
          "new"
          "refiled"
          "replied"
          "report"
          "signed"
          "spam"
          "trash"
          "unread"])]])


(defn search-result
  []
  (let [rs @(rf/subscribe [:search-result])]
    [:table.search-results
     [:tbody
      (map (fn [r]
             [:tr
              {:key (:thread r)
               :on-click #(rf/dispatch [:view-thread-requested (:thread r)])}
              [:td.authors [:div (:authors r)]]
              [:td.subject [:div (:subject r)]]
              [:td.when [:div (:date_relative r)]]
              ])
           rs)]]))

(defmulti render-message-part (fn [message part] (:content-type part)))

(defmethod render-message-part "multipart/alternative" [m p]
  (let [c (:content p)
        plain (first (filter #(= (:content-type %) "text/plain") c))]
    (render-message-part m plain)))

(defmethod render-message-part "multipart/mixed" [m p]
  (let [c (:content p)]
    (map (partial render-message-part m) c)))

(defmethod render-message-part "text/plain" [m p]
  [:div {:key (:id p)} [:pre (:content p)]])

(defmethod render-message-part :default [m p]
  ;; XXX apparently the DOMParser is the best way to do html
  [:div {:key (:id p)} [:pre "mime type " (:content-type p) " not supported"]])

(def r-arrow [:span {:dangerouslySetInnerHTML {:__html "&rarr;"}}])

;; XXX this is not 100% according to rfc 282x or whatever it is now.  More like 30%
(defn parse-email-address [address]
  (let [angles-re #"<([^\>]*)>"
        parens-re #"\([^\)]*\)"]
    (if-let [addr-spec (second (re-find angles-re address))]
      {:addr-spec addr-spec
       :name (str/trim (str/replace address angles-re ""))}
      (if-let [name (second (re-find parens-re address))]
        {:addr-spec (str/trim (str/replace address parens-re ""))
         :name name}
        {:addr-spec address :name address}))))

(defn el-for-email-address [address]
  (let [parsed (parse-email-address address)]
    [:a.email-address
     {:href (str "mailto:" (:addr-spec parsed))
      :title address}
     (:name parsed)]))

(defn render-message [m]
  [:div.message {:key (:id m)}
   (if (or true (:collapse-headers m))
     (let [h (:headers m)]
       [:div.headers.compact-headers
        (el-for-email-address (:From h))
        " " r-arrow " "
        (el-for-email-address (:To h))
        [:span {:style {:float "right"}} (:Date h)]])
     [:div.headers
      (map (fn [[k v]] [:div.header {:key k} [:span.name (name k) ":"] v]) (:headers m))])
   [:div (map (fn [tag] [:span.tag {:key tag} tag]) (:tags m))]
   [:div.message-body (map (partial render-message-part m) (:body m))]])

(defn render-thread [[frst & replies]]
  [:div.thread {:key (str "th:" (:id frst))}
   (render-message frst)
   (map render-thread (first replies))])

(defn thread-pane
  []
  (let [m (first (first  @(rf/subscribe [:thread])))]
    (render-thread m)))


(defn ui
  []
  [:div
   (if @(rf/subscribe [:thread])
     [:div
      [:div {:on-click #(rf/dispatch [:thread-retrieved nil])}
       "Back"
       ]
      [:div.thread
       [thread-pane]]]
     [:div
      [:div.search
       {:on-focus #(rf/dispatch [:show-completions true])}
       [search-term-input]
       (if (or false  @(rf/subscribe [:show-completions])) [tags])]
      [:div {:id "threads"}
       [search-result]]])])


;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (rf/dispatch-sync [:search-requested "hack"])
  (rf/dispatch [:view-thread-requested "00000000000067cd"])
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app")))
