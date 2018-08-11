(ns epsilon.client
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax :refer [GET POST]]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [clojure.string :as str]))

;; A detailed walk-through of this source code is provided in the docs:
;; https://github.com/Day8/re-frame/blob/master/docs/CodeWalkthrough.md

;; -- Domino 1 - Event Dispatch -----------------------------------------------

(defn dispatch-timer-event
  []
  (let [now (js/Date.)]
    (rf/dispatch [:timer now])))  ;; <-- dispatch used

;; Call the dispatching function every second.
;; `defonce` is like `def` but it ensures only one instance is ever
;; created in the face of figwheel hot-reloading of this file.
(defonce do-timer (js/setInterval dispatch-timer-event 1000))


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db
  :initialize
  (fn [_ _]
    {:time (js/Date.)
     :search-term ""
     :time-color "#f88"}))

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


(rf/reg-event-db                 ;; usage:  (dispatch [:timer a-js-Date])
  :timer                         ;; every second an event of this kind will be dispatched
  (fn [db [_ new-time]]          ;; note how the 2nd parameter is destructured to obtain the data value
    (assoc db :time new-time)))  ;; compute and return the new application state


;; -- Domino 4 - Query  -------------------------------------------------------

(rf/reg-sub
  :search-term
  (fn [db _]
    (:search-term db)))

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
            :value @(rf/subscribe [:search-term])
            :on-change
            #(rf/dispatch [:search-requested (-> % .-target .-value)])
            }]])

(defn tags
  []
  [:ul.taglist
   (map (fn [tag]
          [:li
           {:on-click #(rf/dispatch [:search-requested (str "tag:" tag)])}
           tag])
        ["inbox" "new" "spam"
         "attachment"
         "deleted"
         "draft"
         "fb"
         "ham"
         "inbox"
         "new"
         "refiled"
         "replied"
         "report"
         "signed"
         "spam"
         "trash"
         "unread"])])


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

(defn thread-pane
  []
  (let [m (first (first (first @(rf/subscribe [:thread]))))]
    (println m)
    [:div.message
     [:div.headers
      (map (fn [[k v]] [:div {:key k} [:b (name k)] v]) (:headers m))]
     [:div (map (fn [tag] [:span.tag {:key tag} tag]) (:tags m))]
     [:div (pr-str (:body m))]]))


(defn ui
  []
  [:div
   [:div {:id "search"}
    [search-term-input]
    [tags]]
   [:div {:id "threads"}
    [search-result]]
   [:div {:id "thread"}
    [thread-pane]]])

;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (rf/dispatch-sync [:search-requested "hack"])
#_  (rf/dispatch [:view-thread-requested (:thread r)])
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app")))
