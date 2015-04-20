(ns ^{:doc "Integration tests of request handling with the geheimtur interceptors."}
  geheimtur.request-handling-test
  (:require [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.helpers :refer [defhandler]]
            [io.pedestal.http :as service]
            [geheimtur.util.auth :refer [authenticate]]
            [geheimtur.interceptor :as interceptor :refer :all ])
  (:use [clojure.test]
        [io.pedestal.test]))

(defhandler request-handler
  "An interceptor that creates a valid ring response and places it in
  the context."
  [request]
  {:status 200
   :body "Request handled!"
   :headers {}})

(defn identity-injector
  "An intercepter that authenticates a request with the given identity."
  [identity]
  (interceptor {:name  ::identity-injector
                :enter (fn [context]
                         (update-in context [:request] authenticate identity))}))

(defroutes routes
  [[:request-handling "geheimtur.io"
    ["/" {:get request-handler}
     ["/http-basic" ^:interceptors [(http-basic "Test Realm" (constantly true))]

      ["/anonymous"
        ["/loud" {:get [:anonymous-silent request-handler]} ^:interceptors [(guard :silent? false)]]
        ["/silent" {:get [:anonymous-loud request-handler]} ^:interceptors [(guard)]]
       ]

      ["/identified"
       ["/no-role" ^:interceptors [(identity-injector {})]
         ["/ok" {:get [:no-role-ok request-handler]} ^:interceptors [(guard)]]
         ["/not-ok" {:get [:no-role-not-ok request-handler]} ^:interceptors [(guard :roles #{:user} :silent? false)]]
         ]
       ["/user" ^:interceptors [(identity-injector {:roles #{:user }})]
          ["/ok" {:get [:user-ok request-handler]} ^:interceptors [(guard :roles #{:user})]]
          ["/not-enough-rights" {:get [:not-enough-rights request-handler]} ^:interceptors [(guard :roles #{:admin})]]
          ["/not-enough-rights-loud" {:get [:not-enough-rights-silent request-handler]} ^:interceptors [(guard :roles #{:admin} :silent? false)]]
        ]
       ["/admin" ^:interceptors [(identity-injector {:roles #{:admin }})]
        ["/ok" {:get [:admin-ok request-handler]} ^:interceptors [(guard :roles #{:admin})]]
        ["/user/ok" {:get [:admin-user-ok request-handler]} ^:interceptors [(guard :roles #{:user :admin})]]
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
    ;; user with no roles assigned
    "http://geheimtur.io/http-basic/identified/no-role/ok" "Request handled!"
    "http://geheimtur.io/http-basic/identified/no-role/not-ok" "You are not allowed to access to this resource"
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
