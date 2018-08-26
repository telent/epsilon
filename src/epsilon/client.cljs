(ns epsilon.client
  (:require [reagent.core :as reagent]
            [ajax.core :as ajax :refer [GET POST]]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [clojure.string :as str]
            [epsilon.hiccup :as hiccup]
            ["feather-icons" :as feather]
            ))

;; some day, my son, all this will be user-selectable
(def preferred-multipart-alternative "text/html")

(defn inner-html [s]
  {:dangerouslySetInnerHTML {:__html s}})

(defn html-entity
  ([name attrs]
   [:span (merge attrs (inner-html (str name ";")))])
  ([name] (html-entity name {})))

(defn logo [attrs]
  [:svg {:viewBox [0 0 30 30] :width 24 :height 24}
   [:path.logo
    (assoc attrs
           :d "m 22.086698,21.479981 c -0.704022,2.816003 -2.43202,4.608001 -5.184,5.376 -2.400015,0.672 -5.136012,0.768 -8.2080003,0.288 -3.072006,-0.479999 -5.488004,-1.647998 -7.248,-3.504 -1.15200096,-1.279995 -1.61599996,-2.799994 -1.39199996,-4.56 0.255999,-1.79199 0.943999,-3.279989 2.06399996,-4.464 1.183996,-1.215986 2.559995,-1.999985 4.128,-2.352 -0.960006,-0.351985 -1.808005,-0.879984 -2.544,-1.584 -0.736003,-0.703983 -1.136003,-1.615982 -1.2,-2.736 -0.192003,-1.791979 0.223997,-3.311977 1.248,-4.56 1.023995,-1.279975 2.351994,-2.207974 3.984,-2.78399998 2.11199,-0.639973 4.3039883,-0.767973 6.5760003,-0.384 2.303983,0.352027 4.303981,1.28002598 6,2.78399998 1.087978,0.960023 1.583978,2.016022 1.488,3.168 -0.160022,1.15202 -0.832021,1.792019 -2.016,1.92 -1.184019,0.160019 -2.112018,-0.143981 -2.784,-0.912 -0.640017,-0.511979 -0.928016,-1.279979 -0.864,-2.304 -0.288016,-1.023977 -0.896016,-1.695976 -1.824,-2.016 -1.312013,-0.415975 -2.784012,-0.239975 -4.4160003,0.528 -1.600009,0.736023 -2.464008,1.840022 -2.592,3.312 -0.192007,1.376019 0.111992,2.528018 0.912,3.456 0.831991,0.928016 1.9679903,1.440016 3.4080003,1.536 0.799987,0.06402 1.631986,0.08002 2.496,0.048 0.895985,-0.03198 1.727984,0.112015 2.496,0.432 0.575982,0.288015 0.831982,0.752014 0.768,1.392 -0.03202,0.608013 -0.288017,1.056013 -0.768,1.344 -0.672016,0.416012 -1.552015,0.448012 -2.64,0.096 -1.088013,-0.351987 -2.016012,-0.575987 -2.784,-0.672 -1.3120103,-0.127987 -2.5440093,0.224013 -3.6960003,1.056 -1.152007,0.832011 -1.968006,1.82401 -2.448,2.976 -0.768005,1.760007 -0.320005,3.200006 1.344,4.32 1.663992,1.088004 3.18399,1.728003 4.5600003,1.92 1.727987,0.288002 3.423985,0.208002 5.088,-0.24 1.695982,-0.479997 3.055981,-1.503996 4.08,-3.072 l 1.968,0.192")]])

(defn icon
  ([name] (icon name {}))
  ([name attrs]
   (let [o (aget (.-icons feather) name)
         attrs-from-icon (js->clj (.-attrs o) :keywordize-keys true)
         attrs (if-let [v (:view-box attrs)]
                 (assoc attrs :viewBox (str/join " " v))
                 attrs)]
     [:svg (merge attrs-from-icon
                  (inner-html (.-contents o))
                  attrs)])))


;; -- Domino 1 - Event Dispatch -----------------------------------------------


;; -- Domino 2 - Event Handlers -----------------------------------------------

(rf/reg-event-db
  :initialize
  (fn [_ _]
    {:search-term ""
     :suggestions []
     }))

(rf/reg-event-fx
 :search-requested
 (fn [{:keys [db]} [_ term]]
   {:db (-> db (dissoc :show-suggestions :thread-id :thread :search-result))
    :http-xhrio
    {:method          :get
     :uri             "/search"
     :params 	      {:q term :limit 10}
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
   (println result)
   (assoc db :suggestions result)))

(rf/reg-event-db
 :new-suggestions-received-error
 (fn [db [_ err]]
   (println "suggestions error" err)))

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
               (rf/dispatch [:search-requested term])
               (.stopPropagation e)
               (.preventDefault e))}
      [:span {:style {:position "relative"}}
       [:input {:type "text"
                :placeholder "Search messages"
                :auto-complete "off"
                :value term
                :on-change #(rf/dispatch [:search-term-updated (-> % .-target .-value)])
                }]
       [:span {:style {:position "absolute" :top "2px" :right "0.5em"}}
        (icon "search" {:style {:color "grey"} :view-box [2 2 24 24] :height 19 :width 19})]]
      [:span {:on-click #(rf/dispatch [:search-term-updated ""])}
       (html-entity "&nbsp")
       (icon "delete" {})
       (html-entity "&nbsp")
       ]]]))


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
               (rf/dispatch [:search-requested suggestion]))
             }
            suggestion])
         @(rf/subscribe [:suggestions]))]])


(defn search-result
  []
  (let [rs @(rf/subscribe [:search-result])]
    [:div.search-results
     (map (fn [r]
            [:div.result
             {:key (:thread r)
              :on-click #(rf/dispatch [:view-thread-requested (:thread r)])}
             [:div.subject
              [:span.subject (:subject r)]]
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
    (map (partial render-message-part m) c)))

(defmethod render-message-part "text/plain" [m p]
  [:div {:key (:id p)} [:pre (:content p)]])

;; FIXME Probably we should do HTML parsing when the message is fetched, not every time
;; we render it.

(defmethod render-message-part "text/html" [m p]
  (let [[_ attrs & content]  (hiccup/parse-html (:content p))]
    (into [:div attrs] content)))


(defmethod render-message-part "application/octet-stream" [m p]
  [:div.attachment {:key (:id p)}
   [:a {:href (str "/raw?"
                   "id=" (:id m) "&"
                   "content-type=" (:content-type p) "&"
                   "part=" (:id p))
        :target "_download"} "Download " (:content-type p) " attachment " (:filename p)]])

(defmethod render-message-part :default [m p]
  [:div {:key (:id p)} [:pre "mime type " (:content-type p) " not supported"]])

(def r-arrow (html-entity "&rarr"))

;; XXX this is not 100% according to rfc 282x or whatever it is now.  More like 30%
(defn parse-email-address [address]
  (let [;address (or address "(no address) <>")
        angles-re #"<([^\>]*)>"
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
    (if (empty? (:addr-spec parsed))
      [:span "(none)"]
      [:a.email-address
       {:href (str "mailto:" (:addr-spec parsed))
        :title address}
       (:name parsed)])))

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

(defn menu [title & items]
  [:div.menu
   [:ul items]
   [:span.title title]])



(defn search-page []
  [:div
   (menu "Epsilon"
         [:li.clickable {:key :home :on-click #(rf/dispatch [:search-term-updated ""])}
          (logo {:style {:fill "white"}})]
         [:li.clickable {:key :refresh :on-click #(.log js/console "refresh")}
          (icon "refresh-cw" {:stroke-width 2.5} )])
   [:div.content
    [:div.search
     {:on-focus #(rf/dispatch [:show-suggestions true])
      :tabIndex -1
      :on-blur #(do
                  (rf/dispatch [:show-suggestions false])
                  (println "blurr"))}
     [:span
      [search-term-input]]
     (if   @(rf/subscribe [:show-suggestions]) [suggestions])]
    [:div {:id "threads"}
     [search-result]]]])


(defn thread-page []

  [:div
   (menu
    @(rf/subscribe [:thread-subject])
    [:span.clickable {:key :back :on-click #(rf/dispatch [:thread-retrieved nil])}
     (icon "chevrons-left" {:view-box [0 4 22 15]})])
   [:div.thread.content
    [thread-pane]]])



(aset js/window "feather" feather)

(defn ui
  []
  (if @(rf/subscribe [:thread])
    (thread-page)
    (search-page)))



;; -- Entry Point -------------------------------------------------------------

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])     ;; puts a value into application state
  (rf/dispatch-sync [:search-requested "tag:new"])
;  (rf/dispatch [:view-thread-requested "00000000000067cd"])
  (reagent/render [ui]              ;; mount the application's ui into '<div id="app" />'
                  (js/document.getElementById "app")))
