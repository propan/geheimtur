(ns geheimtur.impl.form-based-test
  (:require [clojure.test :refer :all]
            [geheimtur.impl.form-based :refer :all]
            [geheimtur.util.auth :as auth]))

(deftest default-login-handler-test
  (let [{handler :enter} (default-login-handler {})]
    (testing "Redirects to login page on error"
      (let [{response :response}  (handler {:request {}})]
        (is (= 303 (:status response)))
        (is (= "/login?error=true" (get-in response [:headers "Location"])))))

    (testing "Redirects to login page on error without loosing return url"
      (let [{response :response} (handler {:request {:query-params {:return "/some-url"}}})]
        (is (= 303 (:status response)))
        (is (= "/login?error=true&return=/some-url" (get-in response [:headers "Location"]))))))

  (let [{handler :enter}
        (default-login-handler
          {:credential-fn (fn [context {:keys [username password]}]
                            (and (contains? context :request)
                                 (= username "user")
                                 (= password "password")
                                 :success))})]
    (testing "Redirects on success to return url"
      (let [{response :response}
            (handler {:request {:form-params  {"username" "user" "password" "password"}
                                :query-params {:return "/redirect"}}})]
        (is (= 303 (:status response)))
        (is (= "/redirect" (get-in response [:headers "Location"])))))

    (testing "Ignores absolute return urls"
      (let [{response :response}
            (handler {:request {:form-params  {"username" "user" "password" "password"}
                                :query-params {:return "http://evil.com"}}})]
        (is (= 303 (:status response)))
        (is (= "/" (get-in response [:headers "Location"])))))))

(deftest default-logout-handler-test
  (let [{handler :enter}     default-logout-handler
        {response :response} (handler {:request {}})]
    (is (= 303 (:status response)))
    (is (= "/" (get-in response [:headers "Location"])))
    (is (nil? (get-in response [:session ::auth/identity] :not-found)))))
