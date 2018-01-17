(ns geheimtur.impl.interactive-test
  (:require [clojure.test :refer :all ]
            [geheimtur.impl.interactive :refer :all ]
            [geheimtur.util.auth :as auth]))

(deftest interactive-error-handler-test
  (let [handler (interactive-error-handler {:login-uri "/login"})]
    (testing
        "Unauthenticated error"
      (let [context (handler {:request {:path-info "/some-path"}} {})]
        (is (= 303 (get-in context [:response :status ])))
        (is (= "/login?return=/some-path" (get-in context [:response :headers "Location"])))))
    (testing
        "Unauthorized error"
      (let [context (handler {:request {:path-info "/some-path"}} {::auth/type :unauthorized})]
        (is (= 403 (get-in context [:response :status ])))
        (is (= "You are not allowed to access this resource" (get-in context [:response :body ])))))))
