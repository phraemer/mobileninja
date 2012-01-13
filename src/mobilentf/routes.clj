(ns mobilentf.routes
  (:use [compojure.core]
        [ring.adapter.jetty]
        [ring.util.response]
        [ring.middleware
         params
         keyword-params
         nested-params
         multipart-params
         cookies
         session]
        [hiccup core page-helpers]
        )
  
  (:require [compojure.route :as route]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as cs]
            [clj-http.client :as client]
            [cache-dot-clj.cache :as cache]
            [mobilentf.search :as search]))

(comment
  (defn splaow
    [x]
    (time (do (println "User session: "@sandbar.stateful-session/sandbar-session)
              (.replace (apply str x) "\u00A0" "&nbsp;")))))

(defn render [t]
  (apply str t))

(defn render-snippet [s]
  (apply str (html/emit* s)))

(def render-to-response
     (comp response render))

(defn page-not-found [req]
  {:status 404
   :headers {"Content-type" "text/html"}
   :body "Page Not Found"})

(def *base-url2* "http://www.ninjatune.co.uk/forum/threads.php?exp=&show=&?1298057096#1")
(def *base-url* "http://www.ninjatune.co.uk/forum/threads.php?offset=")

(defn fetch-url1 [url]
  (html/html-resource (java.net.URL. url)))

(defn fetch-url
  [url]
  (html/html-snippet (:body (client/get url))))

(defn hn-headlines []
  (map html/text (html/select (fetch-url *base-url*) [:td.title :a])))

(defn hn-points []
  (map html/text (html/select (fetch-url *base-url*) [:td.subtext html/first-child])))

(defn print-headlines-and-points []
  (doseq [line (map #(str %1 " (" %2 ")") (hn-headlines) (hn-points))]
    (println line)))

(defn extract-link
  [offset s]
  (let [n (count (cs/split s #"="))]
    (cond (= n 6)
          (str "/topic/" offset "/" (first (cs/split (nth (cs/split s #"=") 4) #"&")))
          :else
          (str "/message/" (first (cs/split (second (cs/split s #"\(")) #","))))))

(defn re-link
  [offset]
  #(assoc % :attrs
          (assoc (:attrs %) :href (extract-link offset (:onclick (:attrs %))))))

(defn extract-link-nav
  [s]
  (str "/page/" (re-find #"[0-9]+" s)))

(defn re-link-nav
  []
  (fn [x]
    (assoc x :attrs
           (assoc (:attrs x) :href (extract-link-nav (:href (:attrs x)))))))

(defn extract-link-imgs
  [s]
  (str "/" s))

(defn re-link-imgs
  []
  (fn [x]
    (assoc x :attrs
           (assoc (:attrs x) :src (extract-link-imgs (:src (:attrs x)))))))

(comment
[:body] (html/prepend
                    (html/select
                     (fetch-url "http://www.ninjatune.co.uk/forum/nav.php?search=")
                     [[:span (html/attr-contains :class "buttonset")]]))
)

(defn clean-her-up
  [offset url]
  (html/at (fetch-url url)
           [:body :> :table] (html/prepend
                              (html/html-snippet
                               (str  "<a href='addmessage' class='addmessage' style='padding:5;'>Add Message</a>"
                                     "<form action='/search' method='PUT'>
                                     <input name='query' placeholder='search for...' style='width:200;'>
                                     <input type=\"submit\" value=\"Go\" style='width:40;'>
                                     </form>")))
           [:table] (html/remove-attr :width)
           [:img] (html/remove-attr :width)
           [:style] (html/substitute)
           [:div] (html/remove-attr :style)
           [:link] (html/set-attr :href "http://www.3dengineer.com/forum.css")
           [:img] (re-link-imgs)
           [:td] (html/remove-attr :width)
           [[:img (html/attr-starts :name "pnt")]] (html/substitute)
           [:td :> [:a (html/attr-starts :href "threads.php")]] (html/substitute)
           [[:div (html/attr-contains :class "pagination")]] (html/substitute)
           [[:a (html/attr-starts :href "threads.php?offset")]] (re-link-nav)
           [[:h2 (html/attr-contains :class "headline")]] (html/remove-attr :style)
           [[:a (html/attr-contains :href "messages.php?id=")]] (html/do->
                                                                 (re-link offset)
                                                                 (html/remove-attr :target :onclick :onmouseover))))

(defn clean-up-sendmessage
  [node]
  (html/at node           
           [:table] (html/remove-attr :width)
           [:img] (html/remove-attr :width)
           [:style] (html/substitute)
           [:div] (html/remove-attr :style)
           [:link] (html/set-attr :href "http://www.3dengineer.com/forum.css")
           [:td] (html/remove-attr :width)
           [:form] (html/set-attr :action "/sendmessage")
;;           [[:input (html/attr-starts :name "action")]] (html/substitute)
           [[:a (html/attr? :onmouseout)]] (html/substitute (html/html-snippet "<input type='submit' value='Post Message' />"))))

;; (def retrieve-cached-message (memoize retrieve-message))
(cache/defn-cached retrieve-cached-message
  (cache/ttl-cache-strategy (* 1000 86400)) ;; cache for one day
  [id]
  (render-snippet (clean-her-up 0 (str "http://www.ninjatune.co.uk/forum/messages.php?id=" id))))

(html/defsnippet query-result-line
  "mobilentf/results.html" [:div.article]
  [{:keys [id author message]}]
  [:a] (html/do->
        (html/set-attr :href
                  (str "/message/" id))
        (html/content author))
  [:span] (html/content message))

(html/deftemplate search-results-page "mobilentf/results.html"
  [results]
  [:div] (html/substitute (map query-result-line results)))

(defroutes mobilentf-routes
  (GET "/" []
       (render-snippet (clean-her-up "0" (str *base-url* "0"))))

  (GET ["/page/:offset"]
       [offset]
       (render-snippet (clean-her-up offset (str *base-url* offset))))

  (GET ["/topic/:offset/:id"]
       [offset id]
       (render-snippet
        (clean-her-up offset
         (str "http://www.ninjatune.co.uk/forum/threads.php?offset=" offset "&by=5&exp=" id))))

  (GET ["/message/:id", :id #"[0-9]+"] 
       [id]
       (retrieve-cached-message id))

  (GET ["/message/:reply", :reply #".*"]
       [& reply]
       (redirect (str "http://www.ninjatune.co.uk/forum/sendmessage.php?reply_to=" (reply "reply_to"))))

  (GET ["/addmessage"]
       []
       (redirect "http://www.ninjatune.co.uk/forum/sendmessage.php"))

  (POST "/sendmessage" {params :params}
        (client/post "/sendmessage2"
                     (merge params
                            {:host "www.ninjatune.co.uk"
                             :referer "http://www.ninjatune.co.uk/forum/sendmessage.php"})))

  (POST "/sendmessage2" request
        (pr-str "hello " request))

  (GET ["/search"]
       [query]
       (apply str
              (search-results-page (search/search-index query))))
  
  (route/files "/" {:root "public"})
  (route/files "/images" {:root "public/images"})
  (route/files "/topic/images" {:root "public/images"})
  (route/files "/message/images" {:root "public/images"})
  
  (route/not-found "Page not found!"))

; This is a temporary hack to fix those unrecognised chars
(defn wrap-charset [handler charset] 
  (fn [request] 
    (if-let [response (handler request)]
      (cond (seq? (:body response))
            (assoc-in response 
                      [:headers "Content-Type"] 
                      (str content-type "; charset=" charset))
            :else
            (if-let [content-type (get-in response [:headers "Content-Type"])] 
              (if (.contains content-type "charset") 
                response 
                (assoc-in response 
                          [:headers "Content-Type"] 
                          (str content-type "; charset=" charset))) 
              response))))) 

(wrap! mobilentf-routes (:charset "utf8")) 

(defn- log [msg & vals]
  (let [line (apply format msg vals)]
    (locking System/out (println (.toLocaleString (new java.util.Date)) line))))

(defn wrap-request-logging [handler]
  (fn [{:keys [request-method uri params remote-addr headers] :as req}]
    (let [start  (System/currentTimeMillis)
          resp (handler req)
          finish (System/currentTimeMillis)
          total  (- finish start)]
      (log "[%s] [%s] %s %s [%s] (%dms)"
           remote-addr (headers "user-agent") request-method uri params total)
      resp)))

(def app
     (-> mobilentf-routes
         (wrap-request-logging)
         (wrap-params)
         (wrap-session)
         (wrap-cookies)
         (wrap-charset "utf8")))
