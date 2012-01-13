(ns mobilentf.main
  (:require [mobilentf.webserver :as web])
  (:gen-class))

(defn -main [& args]
	(web/start))
