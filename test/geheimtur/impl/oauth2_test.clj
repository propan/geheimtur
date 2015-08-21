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
         (str url "?a=b&c=d") (sorted-map :a "b" :c "d"))))


(deftest authenticate-handler-test
  (testing "Causes 404 when a provider is not defined or provider parameter is not set"
    (let [{handler :enter} (authenticate-handler {})]
      (is (nil? (:response (handler {:request {:query-params {}}}))))
      (is (nil? (:response (handler {:request {:query-params {:provider "github"}}}))))))
  
  (testing "Successfuly redirects and stores state in the session"
    (let [{handler :enter} (authenticate-handler providers)
          {response :response}         (handler {:request {:query-params {:provider "github" :return "/return"}}})
          session-status   (get-in response [:session ::geheimtur.impl.oauth2/callback-state])
          location         (get-in response [:headers "Location"])]
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

    (testing "Successful case with auto parsing"
      (let [response-body {:token_type   "bearer"
                           :scope        "user"
                           :access_token "a72e16c7e42f292c6912e7710c838347ae178b4a"}]
        (with-redefs-fn
          {#'clj-http.client/post
           (fn [url query]
             (when (and (= url "https://github.com/login/oauth/access_token")
                        (= query {:form-params           {:code          code
                                                          :client_id     "client-id"
                                                          :client_secret "client-secret"
                                                          :grant_type    "authorization_code"
                                                          :redirect_uri  "/oauth.callback"}
                                  :throw-entire-message? true
                                  :as                    :auto}))
               {:status 200
                :body   response-body}))}
          #(is (= response-body (fetch-token code provider))))))

    (testing "Successful case with custom parse-token-fn"
      (let [parsed-token {:access_token "a72e16c7e42f292c6912e7710c838347ae178b4a"}
            provider     (assoc provider :token-parse-fn
                                (fn [response]
                                  (when (= (:body response)
                                           "access_token=a72e16c7e42f292c6912e7710c838347ae178b4a&scope=user&token_type=bearer")
                                    parsed-token)))]
        (with-redefs-fn {#'clj-http.client/post (fn [url query]
                                                  (when (and (= url "https://github.com/login/oauth/access_token")
                                                             (= query {:form-params           {:code          code
                                                                                               :client_id     "client-id"
                                                                                               :client_secret "client-secret"
                                                                                               :grant_type    "authorization_code"
                                                                                               :redirect_uri  "/oauth.callback"}
                                                                       :throw-entire-message? true
                                                                       :as                    nil}))
                                                    {:status 200
                                                     :body   "access_token=a72e16c7e42f292c6912e7710c838347ae178b4a&scope=user&token_type=bearer"}))}
          #(is (= parsed-token (fetch-token code provider))))))))

(deftest fetch-user-info-test
  (let [url   "https://api.github.com/user"
        token "a72e16c7e42f292c6912e7710c838347ae178b4a"]

    (testing "Handles any exception thrown while fetching"
      (with-redefs-fn {#'clj-http.client/get (fn [url param] (throw (Exception. "Troubles!")))}
        #(is (nil? (fetch-user-info token url nil)))))

    (testing "Successful fetching using auto-parsing"
      (with-redefs-fn {#'clj-http.client/get (fn [url param]
                                               (when (and (= url "https://api.github.com/user")
                                                          (= param {:oauth-token           token
                                                                    :throw-entire-message? true
                                                                    :as                    :auto}))
                                                 {:status 200
                                                  :body "I'm your body!"}))}
        #(is (= "I'm your body!" (fetch-user-info token url nil)))))

    (testing "Successful fetching using a custom response parser"
      (let [identity {:name "Bob" :last-name "Belcher"}]
        (with-redefs-fn {#'clj-http.client/get (fn [url param]
                                                 (when (and (= url "https://api.github.com/user")
                                                            (= param {:oauth-token           token
                                                                      :throw-entire-message? true
                                                                      :as                    nil}))
                                                   {:status 200
                                                    :body   "I'm your body!"}))}
          #(is (= identity (fetch-user-info token url (fn [resp]
                                                        (when (= "I'm your body!" (:body resp))
                                                          identity))))))))))

(deftest resolve-identity-test
  (let [token    {:access_token  "a72e16c7e42f292c6912e7710c838347ae178b4a"
                  :expires_in    600
                  :refresh_token "a72e16c7e42f292c6912e7710c838347ae178b4b"}
        provider (:github providers)]

    (testing "Ignores failed fetching"
      (with-redefs-fn {#'geheimtur.impl.oauth2/fetch-user-info (fn [token url parse-fn] nil)}
        #(is (nil? (resolve-identity token provider)))))

    (testing "Does not do fetching if no URL is defined"
      (is (= {:access-token "a72e16c7e42f292c6912e7710c838347ae178b4a"
              :expires-in    600
              :refresh-token "a72e16c7e42f292c6912e7710c838347ae178b4b"} (resolve-identity token {}))))

    (testing "Returns fetched identity"
      (with-redefs-fn {#'geheimtur.impl.oauth2/fetch-user-info (fn [token url parse-fn]
                                                                 (when (and (= url "https://api.github.com/user")
                                                                            (= token "a72e16c7e42f292c6912e7710c838347ae178b4a")
                                                                            (not (nil? parse-fn)))
                                                                   :success))}
        #(is (= {:identity      :success
                 :access-token  "a72e16c7e42f292c6912e7710c838347ae178b4a"
                 :expires-in    600
                 :refresh-token "a72e16c7e42f292c6912e7710c838347ae178b4b"} (resolve-identity token provider)))))))

(deftest callback-handler-test
  (let [{handler :enter} (callback-handler providers)
        request {:request {:query-params {:state "123"
                                          :code  "456"}
                           :session {::geheimtur.impl.oauth2/callback-state {:return   "/return"
                                                                             :token    "123"
                                                                             :provider "github"}}}}]
    (testing "Redirects on authorization error"
      (let [{response :response} (handler {:request {}})]
        (is (= 302 (:status response)))
        (is (= "/unauthorized" (get-in response [:headers "Location"]))))

      (let [{response :response}
            (handler (assoc-in request [:session ::geheimtur.impl.oauth2/callback-state :token] "123456"))]
        (is (= 302 (:status response)))
        (is (= "/unauthorized" (get-in response [:headers "Location"])))))

    (testing "Success without :on-success-handler"
      (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (fn [code provider]
                                                                  (when (and (= code "456")
                                                                             (= provider (:github providers)))
                                                                    :authenticated-user))}
        #(let [{response :response} (handler request)]
           (is (= 302 (:status response)))
           (is (= "/return" (get-in response [:headers "Location"])))
           (is (= {::geheimtur.util.auth/identity :authenticated-user} (:session response))))))

    (testing "Success with :on-success-handler"
      (let [{handler :enter}
            (callback-handler (assoc-in providers [:github :on-success-handler] (constantly :success)))]
        (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback
                         (fn [code provider] {:access-token "token-token"})}
          #(is (= :success (:response (handler request)))))))))
