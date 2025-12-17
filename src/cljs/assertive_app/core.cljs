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
  ;; Mount the app first
  (mount-root)
  ;; Try to restore session from localStorage
  ;; This will fetch assertions and problems if session is valid
  (api/restore-session!))

(defn ^:dev/after-load reload []
  (mount-root))
