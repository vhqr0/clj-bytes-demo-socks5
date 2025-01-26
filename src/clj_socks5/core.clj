(ns clj-socks5.core
  (:require [clojure.core.async :as a]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [aleph.tcp :as tcp]
            [clj-bytes.core :as b]
            [clj-socks5.conn :as conn]))

(defn deferred->ch
  ([deferred]
   (deferred->ch deferred identity))
  ([deferred xf]
   (let [ch (a/chan)]
     (-> deferred
         (d/on-realized
          (fn [data] (a/>!! ch (xf data)))
          (fn [_error] (a/close! ch))))
     ch)))

(defn stream->ch-pair
  [stream]
  (let [ich (a/chan 1024)
        och (a/chan 1024)]
    (s/connect stream ich)
    (s/connect och stream)
    [ich och]))

(defn tcp-connect
  [[host port]]
  (println "connect to" host port)
  (deferred->ch
   (tcp/client {:host host :port port})
   stream->ch-pair))

(comment
  (a/go
    (when-let [[ich och] (a/<! (tcp-connect ["www.baidu.com" 80]))]
      (when (a/>! och (b/of-str "GET / HTTP/1.1\r\nHost: www.baidu.com\r\n\r\n"))
        (when-let [res (a/<! ich)]
          (println (b/str res)))))))

(defn socks5-handle
  [stream _info]
  (let [client (stream->ch-pair stream)]
    (conn/handle client tcp-connect)))

(defn socks5-start-server [opts]
  (-> socks5-handle
      (tcp/start-server opts)))

(comment
  (def server (socks5-start-server {:port 1080})))
