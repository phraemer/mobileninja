(defproject mobilentf "1.0.0-SNAPSHOT"
  :description "FIXME: write"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.6.0"]
                 [ring/ring-jetty-adapter "0.3.5"]
                 [hiccup "0.3.0"]
                 [enlive "1.0.0-SNAPSHOT"]
                 [clj-http "0.1.3"]
                 [uk.org.alienscience/cache-dot-clj "0.0.3"]
                 [clucy "0.1.0"]]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]
                     [org.clojars.technomancy/clj-stacktrace "0.2.1-SNAPSHOT"]]
  :main mobilentf.main)
