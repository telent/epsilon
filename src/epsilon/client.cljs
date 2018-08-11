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



(rf/reg-event-db                ;; usage:  (dispatch [:time-color-change 34562])
  :time-color-change            ;; dispatched when the user enters a new colour into the UI text field
  (fn [db [_ new-color-value]]  ;; -db event handlers given 2 parameters:  current application state and event (a vector)
    (assoc db :time-color new-color-value)))   ;; compute and return the new application state

(rf/reg-event-fx
 :search-requested
 (fn [{:keys [db]} [_ term]]
   {:db (assoc db :search-term term)
    :http-xhrio {:method          :get
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
  :time
  (fn [db _]     ;; db is current app state. 2nd unused param is query vector
    (:time db))) ;; return a query computation over the application state

(rf/reg-sub
  :time-color
  (fn [db _]
    (:time-color db)))

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

(defn clock
  []
  [:div.example-clock
   {:style {:color @(rf/subscribe [:time-color])}}
   (-> @(rf/subscribe [:time])
       .toTimeString
       (str/split " ")
       first)])

(defn color-input
  []
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value @(rf/subscribe [:time-color])
            :on-change #(rf/dispatch [:time-color-change (-> % .-target .-value)])}]])  ;; <---

(defn search-term-input
  []
  [:div.search-term-input
   {:placeholder "Search messages"}
   [:input {:type "text"
            :value @(rf/subscribe [:search-term])
            :on-change #(rf/dispatch [:search-requested (-> % .-target .-value)])}]])

(defn search-result
  []
  (let [rs @(rf/subscribe [:search-result])]
    [:table.search-results
     (map (fn [r]
            [:tr
             {:key (:thread r)
              :on-click #(rf/dispatch [:view-thread-requested (:thread r)])}
             [:td.authors [:div (:authors r)]]
             [:td.subject [:div (:subject r)]]
             [:td.when [:div (:date_relative r)]]
             ])
          rs)]))

(defn thread-pane
  []
  (let [m (first (first (first @(rf/subscribe [:thread]))))]
    (println m)
    [:div.message
     [:div.headers
      (map (fn [[k v]] [:div {:key k} [:b (name k)] v]) (:headers m))]
     [:div (map (fn [tag] [:span.tag tag]) (:tags m))]
     [:div (pr-str (:body m))]]))


(defn ui
  []
  [:div
   [:h1 "Epsilon"]
   [search-term-input]
   [:hr]
   [search-result]
   [:hr]
   [thread-pane]

   ])

;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (rf/dispatch-sync [:search-requested "hack"])
  (rf/dispatch [:view-thread-requested (:thread r)])
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app")))
