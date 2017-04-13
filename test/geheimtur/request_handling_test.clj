(ns geheimtur.request-handling-test
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.interceptor.helpers :refer [defhandler]]
            [io.pedestal.http :as service]
            [geheimtur.util.auth :refer [authenticate]]
            [geheimtur.interceptor :as interceptor :refer :all])
  (:use [clojure.test]
        [io.pedestal.test]))

(defhandler request-handler
  "An interceptor that creates a valid ring response and places it in
  the context."
  [request]
  {:status 200
   :body "Request handled!"
   :headers {}})

(defn make-app
  [options]
  (-> options
      service/default-interceptors
      service/service-fn
      ::service/service-fn))

(defn credentials-fn
  [_ {:keys [username password]}]
  (let [credentials (str username ":" password)]
    (case credentials
      "user:123456" {:name "Bob"}
      "admin:67890" {:name "Carol" :roles #{:admin}}
      nil)))

(deftest http-basic-test
  (let [interceptor (http-basic "Demo" credentials-fn)
        routes      #{["/silent"       :get [interceptor (guard :silent? true)  request-handler]                  :route-name :protected-silent]
                      ["/loud"         :get [interceptor (guard :silent? false) request-handler]                  :route-name :protected-loud]
                      ["/admin/silent" :get [interceptor (guard :roles #{:admin} :silent? true) request-handler]  :route-name :admin-silent]
                      ["/admin/loud"   :get [interceptor (guard :roles #{:admin} :silent? false) request-handler] :route-name :admin-loud]}
        app         (make-app {::service/routes routes})]
    (testing "Unauthenticated requests are rejected"
      (is (= "Not Found" (:body (response-for app :get "/silent"))))
      (is (= "You are not allowed to access to this resource" (:body (response-for app :get "/loud")))))

    (testing "Authenticated requests go through"
      (is (= "Request handled!" (:body (response-for app :get "/silent" :headers {"Authorization" "Basic dXNlcjoxMjM0NTY="}))))
      (is (= "Request handled!" (:body (response-for app :get "/loud" :headers {"Authorization" "Basic dXNlcjoxMjM0NTY="})))))

    (testing "Unauthorised requests are rejected"
      (is (= "Not Found" (:body (response-for app :get "/admin/silent" :headers {"Authorization" "Basic dXNlcjoxMjM0NTY="}))))
      (is (= "You are not allowed to access to this resource" (:body (response-for app :get "/admin/loud" :headers {"Authorization" "Basic dXNlcjoxMjM0NTY="})))))

    (testing "Authorised requests go through"
      (is (= "Request handled!" (:body (response-for app :get "/admin/silent" :headers {"Authorization" "Basic YWRtaW46Njc4OTA="}))))
      (is (= "Request handled!" (:body (response-for app :get "/admin/loud" :headers {"Authorization" "Basic YWRtaW46Njc4OTA="})))))))

(deftest token-test
  (let [interceptor (token #(case %2 "123456" {:name "Bob"} "67890" {:name "Carol" :roles #{:admin}} nil))
        routes #{["/"       :get [interceptor (guard :silent? false) request-handler]                  :route-name :protected-route]
                 ["/secret" :get [interceptor (guard :silent? false :roles #{:admin}) request-handler] :route-name :admin-only-route]}
        app    (make-app {::service/routes routes})]
    (testing "Unauthenticated requests are rejected"
      (is (= "You are not allowed to access to this resource" (:body (response-for app :get "/")))))

    (testing "Authenticated requests go through"
      (is (= "Request handled!" (:body (response-for app :get "/" :headers {"Authorization" "Bearer 123456"})))))

    (testing "Unauthorised requests are rejected"
      (is (= "You are not allowed to access to this resource" (:body (response-for app :get "/secret" :headers {"Authorization" "Bearer 123456"})))))

    (testing "Authorised requests go through"
      (is (= "Request handled!" (:body (response-for app :get "/secret" :headers {"Authorization" "Bearer 67890"})))))))
