(ns harokun.core
  (:require
   [harokun.settings :as settings]
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [clojure.core.async :as async])
  (:import [java.net URLEncoder]))

;;
;; Developer tools :P Print Debuger
;;

(defn debug-print [output]
  (if settings/debug-mode (println "[Debug]" output)))

;;
;; HipChat Settings
;;

(def hipchat-v2-api "https://api.hipchat.com/v2/")
(def hipchat-room "room/")
(def polling-room settings/polling-room)
(defn token-param [token] (str "?auth_token=" token))

(def hipchat-previous-getting)

(defn hipchat-history-json [room-id]
  (json/read-str
   ((client/get
    (str hipchat-v2-api hipchat-room room-id "/history" (token-param settings/hipchat-read-token)
         "&max-results=1"))
    :body)
   :key-fn keyword))

(defn hipchat-post-url [room-id]
  (str hipchat-v2-api hipchat-room room-id "/message" (token-param settings/hipchat-write-token)))

(defn post-json [url json]
  (client/post url
               {:body json
                :content-type :json}))


(defn hipchat-post [room-id message]
  (post-json (hipchat-post-url polling-room)
             (json/write-str {:color "yellow", :message message})))

(defn hipchat-json-for-haro [elem]
        {:message (elem :message)
        :date (elem :date)
        :from (elem :from)})

(defn hipchat-get-message [room-id]
  (let [get-data
        (doall (map hipchat-json-for-haro ((hipchat-history-json room-id) :items)))]
    (debug-print get-data)
    get-data))


;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  Hero-kun logic (l _ l) !
;;
(defn bot-says [says]
  (str "[Harokun says...]" says))


(def head-channel
  (async/chan 1))

(def talk-channel
  (async/chan 2))

(defn bot-think-about [message]
  (debug-print (str "[Harokun think about ..]" message))
  (if (and (not (nil? (re-matches #"@haro" (message :message))))
           (not (= (message :date) hipchat-previous-getting))) 
                 (do
                   (debug-print "Haro find my name")
                   (def hipchat-previous-getting (message :date))
                   (async/>!! talk-channel "Hi!"))))

(defn -main []
  (println (bot-says "Hello!"))

  ;;
  ;; Haro kun Heads
  ;;
  
  (let [c head-channel]
    (async/go
     (loop []
       (debug-print "Start Go")
       (Thread/sleep 6000)
       (debug-print "Done Go")
       (let [result (hipchat-get-message polling-room)]
         (async/>! c result))
       (recur)))
    (async/go
     (loop []
       (let [input-from-channel (async/<!! c)]
         (dorun (map bot-think-about input-from-channel)))
       (recur))))

  ;;
  ;; Haro kun Talks
  ;;
  
  (let [d talk-channel]
    (async/go
     (loop []
         (let [input-from-channel (async/<!! d)]
           (hipchat-post polling-room input-from-channel))
         (recur))))
  (loop [] (recur)))
