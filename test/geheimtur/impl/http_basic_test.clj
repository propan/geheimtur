(ns geheimtur.impl.http-basic-test
  (:require [clojure.test :refer :all]
            [geheimtur.impl.http-basic :refer :all]
            [geheimtur.util.auth :as auth]
            [geheimtur.util.response :refer [forbidden unauthorized]]))

(def user-identity
  {:username "Bobby Briggs" :roles #{:user}})

(defn validate-credentials
  [_ {:keys [username password]}]
  (when (and (= username "user")
             (= password "password"))
    user-identity))

(deftest http-basic-error-handler-test
  (let [realm   "Pedo Mellon a Minno."
        handler (http-basic-error-handler realm)]

    (testing "Rejects request with 401 when called with nil"
      (let [{:keys [response]} (handler {} nil)]
        (is (= (unauthorized realm nil) response))))

    (testing "Rejects request with 401 when called with :unauthenticated error"
      (let [error-reason       "You shall not pass!"
            {:keys [response]} (handler {} {::auth/type :unauthenticated :reason error-reason})]
        (is (= (unauthorized realm error-reason) response))))

    (testing "Rejects request with 403 when called with :unauthorized error"
      (let [{:keys [response]} (handler {} {::auth/type :unauthorized})]
        (is (= (forbidden) response))))))

(deftest http-basic-identity-test
  (testing "Does not explode with nil"
    (is (= nil (http-basic-identity nil validate-credentials)))
    (is (= nil (http-basic-identity {:request {:headers {}}} validate-credentials))))

  (testing "Does not explode with malformed header"
    (let [context {:request {:headers {"authorization" "Basic dXNl"}}}]
      (is (= nil (http-basic-identity context validate-credentials)))))
  
  (testing "Resolves identity with correct credentials"
    (let [context {:request {:headers {"authorization" "Basic dXNlcjpwYXNzd29yZA=="}}}]
      (is (= {:roles #{:user}, :username "Bobby Briggs"} (http-basic-identity context validate-credentials))))))

(deftest http-basic-authenticate-test
  (let [context {:original true}]
    
    (testing "Does not explode with nil"
      (is (= context (http-basic-authenticate context validate-credentials))))

    (testing "Does not explode with malformed header"
      (let [context (assoc-in context [:request :headers "authorization"] "Basic dXN")]
        (is (= context (http-basic-authenticate context validate-credentials)))))
    
    (testing "Authenticates users with correct credentials"
      (let [context (-> context
                        (assoc-in [:request :headers "authorization"] "Basic dXNlcjpwYXNzd29yZA==")
                        (http-basic-authenticate validate-credentials))]
        (is (= {::auth/identity user-identity} (get-in context [:request :session])))))))
