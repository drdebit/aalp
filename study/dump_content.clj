#!/usr/bin/env bb
;; Dump the platform's own copy -- the walkthrough episodes and the level
;; tutorials -- to JSON, so the text-mode client always shows students the
;; text the real UI shows. Run from the repo root:  bb study/dump_content.clj
(require '[cheshire.core :as json]
         '[clojure.java.io :as io])

(load-file "src/cljs/assertive_app/episodes.cljs")
(load-file "src/cljs/assertive_app/tutorials.cljs")

(defn keyword->str [x]
  (cond (keyword? x) (name x)
        (set? x) (mapv keyword->str x)
        (map? x) (into {} (map (fn [[k v]] [(keyword->str k) (keyword->str v)]) x))
        (sequential? x) (mapv keyword->str x)
        :else x))

(let [episodes (keyword->str assertive-app.episodes/episodes)
      tutorials (into {} (map (fn [[k v]] [(str k) (keyword->str v)])
                              assertive-app.tutorials/level-tutorials))
      out {:episodes episodes
           :walkthrough-assertion-level assertive-app.episodes/assertion-level
           :tutorials tutorials}]
  (io/make-parents "study/content/content.json")
  (spit "study/content/content.json" (json/generate-string out {:pretty true}))
  (println "wrote study/content/content.json:"
           (count episodes) "episodes,"
           (count tutorials) "tutorial levels"))
