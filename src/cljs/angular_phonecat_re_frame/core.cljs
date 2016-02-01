(ns angular-phonecat-re-frame.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary :include-macros true]
            [accountant.core :as accountant]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react]
            [re-frame.core :as re-frame]
            [ajax.core :as ajax])
  (:require-macros [reagent.ratom :refer [reaction]])
  (:import goog.History))


;; -------------------------
;; Re-frame data

(re-frame/register-sub                                      ;; a new subscription handler
  :phones                                                   ;; usage (subscribe [:phones])
  (fn [db]
    (reaction (:phones @db))))                              ;; pulls out :phones

(re-frame/register-handler
  :process-phones-response
  (fn
    [app-state [_ response]]
    (assoc-in app-state [:phones] response)))

(re-frame/register-handler
  :process-phones-bad-response
  (fn
    [app-state [_ response]]
    (println "Error getting phones" response)
    app-state))

(re-frame/register-handler
  :load-phones
  (fn
    [app-state _]
    (ajax/GET "phones/phones.json"
      {:handler #(re-frame/dispatch [:process-phones-response %1])
      :error-handler #(re-frame/dispatch [:process-phones-bad-response %1])
      :response-format :json
      :keywords? true})
    app-state))

(re-frame/register-handler
  :initialise-db                                            ;; usage: (dispatch [:initialise-db])
  (fn
    [_ _]                                                   ;; Ignore both params (db and v).
    {:phones []
    :search-input ""
    :order-prop "name" }))

(defn handle-search-input-entered
  [app-state [_ search-input]]
  (assoc-in app-state [:search-input] search-input))

(re-frame/register-handler
  :search-input-entered
  handle-search-input-entered)

(defn handle-order-prop-changed
  [app-state [_ order-prop]]
  (assoc-in app-state [:order-prop] order-prop))

(re-frame/register-handler
  :order-prop-changed
  handle-order-prop-changed)

(re-frame/register-sub
  :search-input
  (fn [db]
    (reaction (:search-input @db))))

(re-frame/register-sub
  :order-prop
  (fn [db]
    (reaction (:order-prop @db))))

;; -------------------------
;; Views

(defn phone-component
  [phone]
  [:li
   [:span (:name phone)]
   [:p (:snippet phone)]])

(defn matches-query?
  [search-input phone]
  (if (= "" search-input)
    true
    (boolean (or
               (re-find (re-pattern search-input) (:name phone))
               (re-find (re-pattern search-input) (:snippet phone))))))

(defn phones-component
  []
  (let [phones (re-frame/subscribe [:phones])
        search-input (re-frame/subscribe [:search-input])
        order-prop (re-frame/subscribe [:order-prop])]
    (fn []
      [:ul {:class "phones"}
       (for [phone (->> @phones
                        (filter (partial matches-query? @search-input))
                        (sort-by (keyword @order-prop)))]
         ^{:key (:name phone)} [phone-component phone])])))


(defn search-component
  []
  (let [search-input (re-frame/subscribe [:search-input])])
  (fn []
    [:div "Search"
     [:input {:on-change #(re-frame/dispatch [:search-input-entered (-> % .-target .-value)])}]]))

(defn mark-selected
  [props order-prop current-prop-value]
  (if (= order-prop current-prop-value)
    (reagent/merge-props props {:selected "selected"})
    props))

(defn order-by-component
  []
  (let [order-prop (re-frame/subscribe [:order-prop])]
    (fn []
      [:div "Sort by: "
       [:select {:on-change #(re-frame/dispatch [:order-prop-changed (-> % .-target .-value)])}
        [:option (mark-selected {:value "name"} @order-prop "name") "Alphabetical"]
        [:option (mark-selected {:value "age"} @order-prop "age") "Newest"]]])))

(defn home-page []
  [:div {:class "container-fluid"}
   [:div {:class "row"}
    [:div {:class "col-md-2"}
     [search-component]]]
   [:div {:class "row"}
    [:div {:class "col-md-6"}
     [order-by-component]]]
   [:div {:class "row"}
    [:div {:class "col-md-10"}
     [phones-component]]]])

(defn about-page []
  [:div [:h2 "About angular-phonecat-re-frame"]
   [:div [:a {:href "/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
                    (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
                    (session/put! :current-page #'about-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (re-frame/dispatch [:initialise-db])
  (re-frame/dispatch [:load-phones])
  (accountant/configure-navigation!)
  (accountant/dispatch-current!)
  (mount-root))
