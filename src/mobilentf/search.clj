(ns mobilentf.search
  (:require
   [clojure.string :as cs]
   [clucy.core :as clucy]
   [net.cgrand.enlive-html :as html]
   [clj-http.client :as client])
  )

(defn- fetch-url
  [url]
  (html/html-snippet (:body (client/get url))))

(defn front-page-thread-ids
  []
  (map #(Integer. %)
       (filter string?
               (map #(re-find #"[0-9]+" (:href (:attrs % )))
                    (html/select (fetch-url "http://www.ninjatune.co.uk/forum/threads.php")
                                 [[:a (html/attr? :target)]])))))


(defn front-page-thread-links
  []
  (->> (html/select
        (fetch-url "http://www.ninjatune.co.uk/forum/threads.php")
        [[:a (html/attr-contains :href "exp") ]])
       (map :attrs)
       (filter #(= 2 (count %)))
       (map :href)))


(defn thread-messages
  [link]
  (->> (html/select
        (fetch-url (str "http://www.ninjatune.co.uk/forum/" link))
        [[:a (html/attr-contains :href "message")]])
       (map :attrs)
       (map :href)))

(defn message-text
  [link]
  (cs/replace
   (cs/trim
    (apply str
           (let [msg (fetch-url (str "http://www.ninjatune.co.uk/forum/" link))]
             (html/select (html/select msg [:div.article]) [html/text-node]))))
   "\t"
   ""))

(defn front-page-index-data
  []
  (let [msg-links (flatten (map thread-messages (take 2 (front-page-thread-links))))]
    (zipmap (sort (pmap #(Integer. (re-find #"[0-9]+" %)) msg-links))
            (map message-text msg-links))))

;;;;;;;;;;;; index

(def *index* (clucy/disk-index "messageindex"))

(defn add-to-index!
  [id msg]
  (clucy/add *index* {:id id :message msg}))

(defn search-index
  [query]
  (for [doc (clucy/search *index* query 100)]
    (let [id (:id doc)
          lines (cs/split-lines (:message doc))
          author (first lines)
          message (second lines)]
      {:id id
       :author (cs/replace author "\t" "")
       :message (cs/replace message "\t" "")})))

; (println (clucy/add *index* {:id id :message message-text}))


;;; Agent checker
;;; i need to make this a startup argument but....
;;; change the value of :id to a very recent message id
;;; the indexer will crawl the site for messages from that number
;;; remember if it's too old it will hammer the server so you 
;;; should know what you are doing here even though i added a
;;; delay of 10 seconds to prevent too many requests
;;; you can get a recent message id from a message link
;;; e.g. http://www.ninjatune.co.uk/forum/messages.php?id=7253124 <-- id
 
(def *message-agent* (agent {:id NEEDMESSAGEIDHERE :active true}))

(defn check-for-new-messages
  [last-msg]
  (when (:active last-msg)
    (let [next-id (inc (:id last-msg))
          msg (try (message-text (str "messages.php?id=" next-id))
                   (catch Exception e
                     (do
                       (println "Error getting message " next-id " " e)
                       (Thread/sleep (* 60 1000))
                       "")))]
      (cond (= msg "")
            (do
              (Thread/sleep (* 10 1000))
              (send *message-agent* check-for-new-messages)
              last-msg)
            :else
            (do
              (println next-id msg)
              (add-to-index! next-id msg)
              (send *message-agent* check-for-new-messages)
              {:id next-id :active true})))))


(defn stop-checking
  [last-msg]
  (assoc last-msg :active false))
