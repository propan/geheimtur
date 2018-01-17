(ns geheimtur.impl.token-test
  (:require [clojure.test :refer :all]
            [geheimtur.impl.token :refer :all]
            [geheimtur.util.auth :as auth]))

(deftest default-error-handler-test
  (testing "Default error handler return 403 response"
    (is (= {:status  403
            :headers {}
            :body    "You are not allowed to access this resource"}
           (#'geheimtur.impl.token/default-error-handler nil (ex-info "403 Forbidden" {::auth/type ::auth/unauthenticated}))))))

(deftest bearer-token-parser-test
  (testing "Returns no token if the header is missing"
    (is (= nil (#'geheimtur.impl.token/bearer-token-parser {:request {}}))))

  (testing "Returns no token if the header is malformed"
    (is (= nil (#'geheimtur.impl.token/bearer-token-parser {:request {:headers {"authorization" "Basic 123456"}}}))))

  (testing "Returns a token"
    (is (= "123456" (#'geheimtur.impl.token/bearer-token-parser {:request {:headers {"authorization" "Bearer 123456"}}})))))

(deftest token-identity-test
  (testing "Returns nothing when no token is given"
    (is (= nil (#'geheimtur.impl.token/token-identity nil identity nil))))

  (testing "Returns a result of the given credential-fn"
    (is (= {:context {:context true}
            :token   "token"} (#'geheimtur.impl.token/token-identity
                               {:context true}
                               (fn [context token]
                                 {:context context :token token})
                               "token")))))

(deftest token-authenticate-test
  (testing "Default credential-fn does not authenticate"
    (let [interceptor (token-authenticate {})]
      (is (= {:request {:headers {"authorization" "Bearer 123456"}}} (interceptor {:request {:headers {"authorization" "Bearer 123456"}}})))))

  (testing "Authenticates when credential-fn returns an identity"
    (let [interceptor (token-authenticate {:credential-fn (fn [_ token]
                                                            (when (= token "123456")
                                                              {:name "Bob"}))})]
      (is (= {:request {:headers {"authorization" "Bearer 123456"}
                        :session {::auth/identity {:name "Bob"}}}} (interceptor {:request {:headers {"authorization" "Bearer 123456"}}}))))))

(deftest token-error-handler-test
  (testing "Uses default handler when non is given"
    (let [error-handler (token-error-handler {})]
      (is (= {:request  {}
              :response {:status  403
                         :headers {}
                         :body    "You are not allowed to access this resource"}}
             (error-handler {:request {}} {:error true})))))

  (testing "Uses the given error handler"
    (let [error-handler (token-error-handler {:error-fn (fn [ctx err] {:status 403 :body {:context ctx :error err}})})]
      (is (= {:request  {}
              :response {:status 403
                         :body   {:context {:request {}}
                                  :error   {:error true}}}}
             (error-handler {:request {}} {:error true}))))))
