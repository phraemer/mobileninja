(ns mobilentf.webserver
  (:use [compojure.core]
        [ring.adapter.jetty])
  (:require [mobilentf.routes :as routes]
            [mobilentf.search :as search]))

(defn start
  []
  (do
    (run-jetty #'mobilentf.routes/app {:join? false
                                       :port 8888
                                       })
    (send search/*message-agent* search/check-for-new-messages)))
