(ns geheimtur.impl.oauth2-test
  (:require [clojure.test :refer :all]
            [geheimtur.impl.oauth2 :refer :all]
            [geheimtur.util.auth :as auth]
            [geheimtur.util.url :refer [get-query]])
  (:import [java.net URL]))

(def providers
  {:github {:auth-url      "https://github.com/login/oauth/authorize"
            :token-url     "https://github.com/login/oauth/access_token"
            :user-info-url "https://api.github.com/user"
            :client-id     "client-id"
            :client-secret "client-secret"
            :scope         "user:email"
            :callback-uri  "/oauth.callback"
            :user-info-parse-fn (constantly :success)}})

(deftest create-url-test
  (let [url "http://domain.com"]
    (are [result query] (= result (create-url url query))
         (str url "?")        {}
         (str url "?a=b&c=d") {:a "b" :c "d"})))

(deftest authenticate-handler-test
  (testing "Causes 404 when a provider is not defined or provider parameter is not set"
    (let [handler (authenticate-handler {})]
      (is (nil? (handler {:query-params {}})))
      (is (nil? (handler {:query-params {:provider "github"}})))))
  
  (testing "Successfuly redirects and stores state in the session"
    (let [handler        (authenticate-handler providers)
          response       (handler {:query-params {:provider "github" :return "/return"}})
          session-status (get-in response [:session ::geheimtur.impl.oauth2/callback-state])
          location       (get-in response [:headers "Location"])]
      (is (not (nil? response)))
      (is (= 302 (:status response)))
      (is (not (nil? session-status)))
      (is (not (nil? (:token session-status))))
      (is (= "github" (:provider session-status)))
      (is (= "/return" (:return session-status)))
      (is (not (nil? location)))
      (let [query (get-query location)]
        (is (= "client-id" (:client_id query)))
        (is (= "user:email" (:scope query)))
        (is (= "/oauth.callback" (:redirect_uri query)))
        (is (= (:token session-status) (:state query)))))))

(deftest fetch-token-test
  (let [code     "123-ASD-456-QWE"
        provider (:github providers)]

    (testing "Handles any exception thrown while fetching"
      (with-redefs-fn {#'clj-http.client/post (fn [url query] (throw (Exception. "Troubles!")))}
        #(is (nil? (fetch-token code provider)))))

    (testing "Successful case"
      (with-redefs-fn {#'clj-http.client/post (fn [url query]
                                                (when (and (= url "https://github.com/login/oauth/access_token")
                                                           (= query {:form-params {:code          code
                                                                                   :client_id     "client-id"
                                                                                   :client_secret "client-secret"
                                                                                   :grant_type    "authorization_code"
                                                                                   :redirect_uri  "/oauth.callback"}}))
                                                  {:status 200
                                                   :body   "access_token=a72e16c7e42f292c6912e7710c838347ae178b4a&scope=user&token_type=bearer"}))}
        #(is (= {:token_type   "bearer"
                 :scope        "user"
                 :access_token "a72e16c7e42f292c6912e7710c838347ae178b4a"} (fetch-token code provider)))))))

(deftest fetch-user-info-test
  (let [url   "https://api.github.com/user"
        token "a72e16c7e42f292c6912e7710c838347ae178b4a"]

    (testing "Handles any exception thrown while fetching"
      (with-redefs-fn {#'clj-http.client/get (fn [url param] (throw (Exception. "Troubles!")))}
        #(is (nil? (fetch-user-info url token)))))

    (testing "Successful case"
      (with-redefs-fn {#'clj-http.client/get (fn [url param]
                                               (when (and (= url "https://api.github.com/user")
                                                        (= param {:oauth-token token}))
                                                 {:status 200
                                                  :body "I'm your body!"}))}
        #(is (= "I'm your body!" (fetch-user-info url token)))))))

(deftest resolve-identity-test
  (let [token    {:access_token "a72e16c7e42f292c6912e7710c838347ae178b4a"}
        provider (:github providers)]

    (testing "Ignores failed fetching"
      (with-redefs-fn {#'geheimtur.impl.oauth2/fetch-user-info (fn [url token] nil)}
        #(is (nil? (resolve-identity token provider)))))

    (testing "Does not do fetching if no URL is defined"
      (is (= {:access-token "a72e16c7e42f292c6912e7710c838347ae178b4a"} (resolve-identity token {}))))

    (testing "Returns fetched identity"
      (with-redefs-fn {#'geheimtur.impl.oauth2/fetch-user-info (fn [url token]
                                                                 (when (and (= url "https://api.github.com/user")
                                                                            (= token "a72e16c7e42f292c6912e7710c838347ae178b4a"))
                                                                   :user-success))}
        #(is (= {:identity :success
                 :access-token "a72e16c7e42f292c6912e7710c838347ae178b4a"} (resolve-identity token provider)))))))

(deftest callback-handler-test
  (let [handler (callback-handler providers)
        request {:query-params {:state "123"
                                :code  "456"}
                 :session {::geheimtur.impl.oauth2/callback-state {:return   "/return"
                                                                   :token    "123"
                                                                   :provider "github"}}}]
    (testing "Redirects on authorization error"
      (let [response (handler {})]
        (is (= 302 (:status response)))
        (is (= "/unauthorized" (get-in response [:headers "Location"]))))

      (let [response (handler (assoc-in request [:session ::geheimtur.impl.oauth2/callback-state :token] "123456"))]
        (is (= 302 (:status response)))
        (is (= "/unauthorized" (get-in response [:headers "Location"])))))

    (testing "Success without :on-success-handler"
      (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (fn [code provider]
                                                                  (when (and (= code "456")
                                                                             (= provider (:github providers)))
                                                                    :authenticated-user))}
        #(let [response (handler request)]
          (is (= 302 (:status response)))
          (is (= "/return" (get-in response [:headers "Location"])))
          (is (= {::geheimtur.util.auth/identity :authenticated-user} (:session response))))))

    (testing "Success with :on-success-handler"
      (let [handler (callback-handler (assoc-in providers [:github :on-success-handler] (constantly :success)))]
        (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (fn [code provider] {:access-token "token-token"})}
          #(is (= :success (handler request))))))))
