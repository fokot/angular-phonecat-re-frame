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


(enable-console-print!)

;; -------------------------
;; Re-frame data

(re-frame/register-handler
  :initialise-db                                            ;; usage: (dispatch [:initialise-db])
  (fn
    [_ _]                                                   ;; Ignore both params (db and v).
    {:phones       []
     :phone-details  {}
     :search-input ""
     :order-prop   "name"}))

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
    (ajax/GET "/phones/phones.json"
              {:handler         #(re-frame/dispatch [:process-phones-response %1])
               :error-handler   #(re-frame/dispatch [:process-phones-bad-response %1])
               :response-format :json
               :keywords?       true})
    app-state))

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

(re-frame/register-sub
  :phone-details
  (fn [db]
    (reaction (:phone-details @db))))

(re-frame/register-sub
  :phone-query
  (fn [db [_ phone-id]]
    (let [phone-details-reaction (reaction (:phone-details @db))]
      (reaction ((keyword phone-id) @phone-details-reaction)))))

(re-frame/register-handler
  :process-phone-detail-response
  (fn
    ;; store info for the specific phone-id in the db
    [app-state [_ phone-id response]]
    (assoc-in app-state [:phone-details (keyword phone-id)] response)))

(re-frame/register-handler
  :process-phone-detail-bad-response
  (fn
    [app-state [_ [phone-id response]]]
    (println "Error getting phone detail for id: " phone-id)
    (println response)
    app-state))

(re-frame/register-handler
  :load-phone-detail
  (fn
    ;; fetch information for the phone with the given phone-id
    [app-state [_ phone-id]]
    (ajax/GET (str "/phones/" phone-id ".json")
              {:handler #(re-frame/dispatch [:process-phone-detail-response phone-id %1])
               :error-handler #(re-frame/dispatch [:process-phone-detail-bad-response phone-id %1])
               :response-format :json
               :keywords? true})
    app-state))

(re-frame/register-handler
  :set-image
  (fn
    [app-state [_ selected-image-url]]
    (assoc-in app-state [:phone-details :selected-image-url] selected-image-url)))

(re-frame/register-sub
  :selected-image-url
  (fn [db [_ phone-id]]
    (let [phone (re-frame/subscribe [:phone-query phone-id])
          phone-details (re-frame/subscribe [:phone-details])
          images (reaction (:images @phone))]
      (reaction
        (if @phone-details
          (if-let [image-url (:selected-image-url @phone-details)]
            image-url
            (first @images)))))))

;; -------------------------
;; Views

(defn phone-component
  [phone]
  [:li {:class "thumbnail phone-listing"}
   [:a {:href  (str "#/phones/" (:id phone))
        :class "thumb"}
    [:img {:src (str "/" (:imageUrl phone))}]]
   [:a {:href (str "/phones/" (:id phone))} (:name phone)]
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
  [(session/get :current-page) (session/get :params)])

;; -------------------------
;; Phone details views

(defn phone-info-template
  [section-title attributes-map]
  [:li
   [:span section-title]
   [:dl
    (map (fn [attribute]
           ^{:key attribute} [:div
                              [:dt (:name attribute)]
                              [:dd (:value attribute)]])
         attributes-map)]])

(defn thumbnails
  [phone]
  [:ul {:class "phone-thumbs"}
   (for [image (:images @phone)]
     ^{:key image} [:li [:img {:src (str "/" image)
                               :class "phone"
                               :on-click #(re-frame/dispatch [:set-image image])}]])])

(defn availability
  [availability]
  [:li
   [:span "Availability and Networks"]
   [:dl
    [:dt "Availability"]
    (for [availability @availability]
      availability)]])

(defn battery
  [battery]
  [phone-info-template "Battery" [{:name  "Type"
                                   :value (:type @battery)}
                                  {:name  "Talk Time"
                                   :value (:talkTime @battery)}
                                  {:name  "Standby time (max)"
                                   :value (:standbyTime @battery)}]])

(defn storage-and-memory
  [storage]
  [phone-info-template "Storage And Memory" [{:name  "RAM"
                                              :value (:ram @storage)}
                                             {:name  "Internal Storage"
                                              :value (:flash @storage)}]])

(defn connectivity
  [connectivity]
  [phone-info-template "Connectivity" [{:name  "Network Support"
                                        :value (:cell @connectivity)}
                                       {:name  "Wifi"
                                        :value (:wifi @connectivity)}
                                       {:name  "Bluetooth"
                                        :value (:bluetooth @connectivity)}]])

(defn android
  [android]
  [phone-info-template "Android" [{:name  "OS Version"
                                   :value (:os @android)}
                                  {:name  "UI"
                                   :value (:ui @android)}]])

(defn size-and-weight
  [size-and-weight]
  [phone-info-template "Size And Weight" [{:name  "Dimensions"
                                           :value (:dimensions @size-and-weight)}
                                          {:name  "Weight"
                                           :value (:weight @size-and-weight)}]])

(defn display
  [display]
  [phone-info-template "Display" [{:name  "Screen size"
                                   :value (:screenSize @display)}
                                  {:name  "Screen resolution"
                                   :value (:screenResolution @display)}
                                  {:name  "Touch screen"
                                   :value (:touchScreen @display)}]])

(defn hardware
  [hardware]
  [phone-info-template "Hardware" [{:name  "CPU"
                                    :value (:cpu @hardware)}
                                   {:name  "USB"
                                    :value (:usb @hardware)}
                                   {:name  "Audio / headphone jack"
                                    :value (:audioJack @hardware)}
                                   {:name  "FM Radio"
                                    :value (:fmRadio @hardware)}
                                   {:name  "Accelerometer"
                                    :value (:accelerometer @hardware)}]])

(defn camera
  [camera]
  [phone-info-template "Camera" [{:name  "Primary"
                                  :value (:primary @camera)}
                                 {:name  "Features"
                                  :value (clojure.string/join ", " (:features @camera))}]])

(defn additional-features
  [additional-features]
  [:li
   [:span "Additional Features"]
   [:dd @additional-features]])

(defn specs
  [phone]
  [:ul {:class "specs"}
   [availability (reaction (:availiability @phone))]
   [battery (reaction (:battery @phone))]
   [storage-and-memory (reaction (:storage @phone))]
   [connectivity (reaction (:connectivity @phone))]
   [android (reaction (:android @phone))]
   [display (reaction (:display @phone))]
   [hardware (reaction (:hardware @phone))]
   [camera (reaction (:camera @phone))]
   [additional-features (reaction (:additionalFeatures @phone))]])

(defn phone-page [{phone-id :phone-id}]
  (let [phone (re-frame/subscribe [:phone-query phone-id])
        image-url (re-frame/subscribe [:selected-image-url phone-id])]
    (fn []
      [:div
       [:img {:src (str "/" @image-url)
              :class "phone"}]
       [:h1 (:name @phone)]
       [:p (:description @phone)]
       [thumbnails phone]
       [specs phone]])))

;; -------------------------
;; Routes
(defn redirect-to
  [resource]
  (secretary/dispatch! resource)
  (.setToken (History.) resource))

(secretary/defroute "/phones/:phone-id" {:as params}
                    (session/put! :current-page #'phone-page)
                    (session/put! :params params)
                    (re-frame/dispatch [:load-phone-detail (:phone-id params)]))

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
