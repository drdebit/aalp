(ns assertive-app.core
  "Main ClojureScript entry point for the assertive accounting educational app."
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [assertive-app.state :as state]
            [assertive-app.views :as views]
            [assertive-app.api :as api]))

;; Create root once
(defonce root (rdom/create-root (.getElementById js/document "app")))

(defn mount-root []
  (rdom/render root [views/main-app]))

(defn ^:export init []
  (println "Initializing Assertive Accounting App...")
  ;; Load initial data
  (api/fetch-assertions! 0)
  (api/fetch-problem! 0)
  ;; Mount the app
  (mount-root))

(defn ^:dev/after-load reload []
  (mount-root))
