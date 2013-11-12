(ns geheimtur.impl.form-based-test
  (:require [clojure.test :refer :all ]
            [geheimtur.impl.form-based :refer :all ]
            [geheimtur.util.auth :as auth]))

(deftest default-login-handler-test
  (let [handler (default-login-handler {})]
    (testing "Redirects to login page on error"
      (let [response (handler {})]
        (is (not (nil? response)))
        (is (= 303 (:status response)))
        (is (= "/login?error=true" (get-in response [:headers "Location"])))))

    (testing "Redirects to login page on error without loosing return url"
      (let [response (handler {:query-params {:return "/some-url"}})]
        (is (not (nil? response)))
        (is (= 303 (:status response)))
        (is (= "/login?error=true&return=/some-url" (get-in response [:headers "Location"]))))))

  (let [handler (default-login-handler {:credentials-fn (fn [user password]
                                                            (when (and (= user "user")
                                                                       (= password "password"))
                                                              :success))})]
    (testing "Redirects on success to return url"
      (let [response (handler {:form-params  {"username" "user" "password" "password"}
                               :query-params {:return "/redirect"}})]
        (is (= 303 (:status response)))
        (is (= "/redirect" (get-in response [:headers "Location"])))))

    (testing "Ignores absolute return urls"
      (let [response (handler {:form-params  {"username" "user" "password" "password"}
                               :query-params {:return "http://evil.com"}})]
        (is (= 303 (:status response)))
        (is (= "/" (get-in response [:headers "Location"])))))))

(deftest default-logout-handler-test
  (let [response (default-logout-handler {})]
    (is (= 303 (:status response)))
    (is (= "/" (get-in response [:headers "Location"])))
    (is (nil? (get-in response [:session ::auth/identity] :not-found)))))
