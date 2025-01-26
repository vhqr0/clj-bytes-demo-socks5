(ns clj-socks5.conn
  (:require [clojure.core.async :as a]
            [clj-bytes.core :as b]
            [clj-bytes.struct :as st]
            [clj-socks5.struct :as sst]))

(defn ->state
  []
  {:stage :wait-auth-req :buffer (b/empty)})

(defn update-buffer
  [state b]
  (update state :buffer b/concat! b))

(defmulti advance
  (fn [state] (:stage state)))

(defmethod advance :wait-auth-req [state]
  (let [{:keys [buffer]} state]
    (if-let [[{:keys [meths]} buffer] (-> buffer (st/unpack sst/socks5-auth-req))]
      (do
        (assert (contains? (set meths) :no-auth))
        (let [rep (-> {:ver :socks5 :meth :no-auth} (st/pack sst/socks5-auth-req))]
          [rep (assoc state :stage :wait-req :buffer buffer)]))
      [nil state])))

(defmethod advance :wait-req [state]
  (let [{:keys [buffer]} state]
    (if-let [[{:keys [cmd addr]} buffer] (-> buffer (st/unpack sst/socks5-req))]
      (do
        (assert (= cmd :connect))
        (let [rep (-> {:ver :socks5 :res :ok :rsv 0 :addr ["0.0.0.0" 0]} (st/pack sst/socks5-rep))]
          [rep (assoc state :stage :connected :buffer buffer :addr addr)]))
      [nil state])))

(defn handshake
  [[ich och]]
  (let [state (sst/->state)]
    (a/go-loop [state state]
      (if (= (:stage state) :connected)
        state
        (when-let [b (a/<! ich)]
          (let [state (sst/update-buffer state b)
                [to-send state] (sst/advance state)]
            (when (or (nil? to-send) (b/empty? to-send) (a/>! och to-send))
              (recur state))))))))

(defn handle
  [client connect-fn]
  (a/go
    (when-let [state (a/<! (handshake client))]
      (let [{:keys [buffer addr]} state]
        (when-let [server (a/<! (connect-fn addr))]
          (when (or (b/empty? buffer) (a/>! (second server) buffer))
            (a/pipe (first client) (second server))
            (a/pipe (first server) (second client))))))))
