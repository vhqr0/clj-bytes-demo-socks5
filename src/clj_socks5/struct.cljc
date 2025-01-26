(ns clj-socks5.struct
  (:require [clj-bytes.core :as b]
            [clj-bytes.struct :as st]))

;; socks5: https://www.rfc-editor.org/rfc/rfc1928

(def socks5-rsv
  (-> st/uint8 (st/wrap-validator #(= % 0))))

(def socks5-str
  (-> (st/bytes-var st/uint8)
      st/wrap-str))

^:rct/test
(comment
  (-> (b/concat! (b/of-int 5 :uint8) (b/of-str "hello"))
      (st/unpack socks5-str)
      first)
  ;; => "hello"
  )

(def ver->int
  {:socks5 5})

(def socks5-ver
  (st/enum st/uint8 ver->int))

(def cmd->int
  {:connect 1 :bind 2 :udp-assoc 3})

(def socks5-cmd
  (st/enum st/uint8 cmd->int))

(def res->int
  {:ok 0 :error 1 :no-perm 2 :net-unreach 3 :host-unreach 4
   :conn-refused 5 :ttl-expired 6 :cmd-no-support 7 :atype-no-support 8})

(def socks5-res
  (st/enum st/uint8 res->int))

(def atype->int
  {:domain 3 :ipv4 1 :ipv6 4})

(def socks5-atype
  (st/enum st/uint8 atype->int))

(def socks5-domain-host socks5-str)

(def socks5-addr
  (-> (st/key-fns
       :atype (constantly socks5-atype)
       :host (fn [{:keys [atype]}]
               (case atype
                 :domain socks5-domain-host
                 ;; TODO: add ipv4/ipv6 host support
                 ))
       :port (constantly st/uint16-be))
      (st/wrap
       (fn [[host port]] {:atype :domain :host host :port port})
       (juxt :host :port))))

(def meth->int
  {:no-auth 0 :gssapi 1 :password 2 :no-accepct 0xff})

(def socks5-meth
  (st/enum st/uint8 meth->int))

(def socks5-meths
  (-> (st/bytes-var st/uint8)
      (st/wrap-struct
       (st/coll-of socks5-meth))))

^:rct/test
(comment
  (-> (b/of-seq [3 0 1 2])
      (st/unpack socks5-meths)
      first)
  ;; => [:no-auth :gssapi :password]
  )

(def socks5-auth-req
  (st/keys
   :ver socks5-ver
   :meths socks5-meths))

(def socks5-auth-rep
  (st/keys
   :ver socks5-ver
   :meth socks5-meth))

(def socks5-req
  (st/keys
   :ver socks5-ver
   :cmd socks5-cmd
   :rsv socks5-rsv
   :addr socks5-addr))

(def socks5-rep
  (st/keys
   :ver socks5-ver
   :res socks5-res
   :rsv socks5-rsv
   :addr socks5-addr))
