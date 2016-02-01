(ns angular-phonecat-re-frame.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.util.response :refer [response resource-response content-type]]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-js include-css]]
            [angular-phonecat-re-frame.middleware :refer [wrap-middleware]]
            [environ.core :refer [env]]
            [ring.middleware.json :refer [wrap-json-response]]))

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(def loading-page
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]
     (include-css (if (env :dev) "css/site.css" "css/site.min.css"))]
    [:body
     mount-target
     (include-js "js/app.js")]]))

(def phones [{:name "Nexus S" :snippet "Fast just got faster with Nexus S."}
             {:name "Motorola XOOM™ with Wi-Fi" :snippet "The Next, Next Generation tablet."}
             {:name "Motoral Xoom" :snippet "The Next, Next Generation tablet."}])

(defn handler [request]
  (response phones))

(defroutes routes
           (GET "/" [] loading-page)
           (GET "/about" [] loading-page)
           (GET "/phones/phones.json" [] (wrap-json-response handler))

           (resources "/")
           (not-found "Not Found"))

(def app (wrap-middleware #'routes))