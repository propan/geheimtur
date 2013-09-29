(ns ^{:doc "Integration tests of request handling with the geheimtur interceptors."}
  geheimtur.request-handling-test
  (:require [io.pedestal.service.http.route.definition :refer [defroutes]]
            [io.pedestal.service.interceptor :as interceptor :refer [definterceptorfn defhandler interceptor]]
            [io.pedestal.service.http :as service]
            [geheimtur.core :as core :refer :all ])
  (:use [clojure.test]
        [io.pedestal.service.test]))

(defhandler request-handler
  "An interceptor that creates a valid ring response and places it in
  the context."
  [request]
  {:status 200
   :body "Request handled!"
   :headers {}})

(definterceptorfn identity-injector
  "An intercepter that authenticates a request with the given identity."
  [identity]
  (interceptor :name ::identity-injector
               :enter (fn [context]
                        (authenticate context identity))))

(defroutes routes
  [[:request-handling "geheimtur.io"
    ["/" {:get request-handler}
     ["/http-basic" ^:interceptors [(http-basic "Test Realm" (constantly true))]

      ["/anonymous"
        ["/loud" {:get [:anonymous-silent request-handler]} ^:interceptors [(guard #{:user} :silent? false)]]
        ["/silent" {:get [:anonymous-loud request-handler]} ^:interceptors [(guard #{:user})]]
       ]

      ["/identified"
       ["/user" ^:interceptors [(identity-injector {:roles #{:user }})]
          ["/ok" {:get [:user-ok request-handler]} ^:interceptors [(guard #{:user})]]
          ["/not-enough-rights" {:get [:not-enough-rights request-handler]} ^:interceptors [(guard #{:admin})]]
          ["/not-enough-rights-loud" {:get [:not-enough-rights-silent request-handler]} ^:interceptors [(guard #{:admin} :silent? false)]]
        ]
       ["/admin" ^:interceptors [(identity-injector {:roles #{:admin }})]
        ["/ok" {:get [:admin-ok request-handler]} ^:interceptors [(guard #{:admin})]]
        ["/user/ok" {:get [:admin-user-ok request-handler]} ^:interceptors [(guard #{:user :admin})]]
        ]
       ]

      ]
     ]]])

(def app
  (::service/service-fn (-> {::service/routes routes}
                          service/default-interceptors
                          service/service-fn)))

(deftest http-basic-test
  (are [url body] (= body (->> url
                            (response-for app :get)
                            :body))
    ;; anonymous access
    "http://geheimtur.io/http-basic/anonymous/loud" "You are not allowed to access to this resource"
    "http://geheimtur.io/http-basic/anonymous/silent" "Not Found"
    ;; user access
    "http://geheimtur.io/http-basic/identified/user/ok" "Request handled!"
    "http://geheimtur.io/http-basic/identified/user/not-enough-rights" "Not Found"
    "http://geheimtur.io/http-basic/identified/user/not-enough-rights-loud" "You are not allowed to access to this resource"
    ;; admin access
    "http://geheimtur.io/http-basic/identified/admin/ok" "Request handled!"
    "http://geheimtur.io/http-basic/identified/admin/user/ok" "Request handled!"
    ;; basic routes
    "http://geheimtur.io/unrouted" "Not Found"
    "http://geheimtur.io/" "Request handled!"))
