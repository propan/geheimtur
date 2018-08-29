(ns geheimtur.impl.oauth2-test
  (:require [clojure.test :refer :all]
            [geheimtur.impl.oauth2 :refer :all]
            [geheimtur.util.auth :as auth]
            [geheimtur.util.url :refer [get-query]])
  (:import [java.net URL]))

(def PROVIDERS
  {:github   {:auth-url           "https://github.com/login/oauth/authorize"
              :token-url          "https://github.com/login/oauth/access_token"
              :user-info-url      "https://api.github.com/user"
              :client-id          "client-id"
              :client-secret      "client-secret"
              :scope              "user:email"
              :callback-uri       "/oauth.callback"
              :user-info-parse-fn (constantly :success)}

   :github-2 {:auth-url           "https://github.com/login/oauth/authorize"
              :token-url          "https://github.com/login/oauth/access_token"
              :user-info-url      "https://api.github.com/user"
              :client-id          "client-id"
              :client-secret      "client-secret"
              :client-params      {:foo "bar"}
              :scope              "user:email"
              :callback-uri       "/oauth.callback"
              :user-info-parse-fn (constantly :success)
              :on-success-handler (fn [context {:keys [access-token]}]
                                    (when (and
                                           (contains? context :request)
                                           (= access-token "token-token"))
                                      :success))}})

(defn- assoc-in-ps
  [ps ks v]
  (if (fn? ps)
    (fn [] (assoc-in (ps) ks v))
    (assoc-in ps ks v)))

(deftest create-url-test
  (let [url "http://domain.com"]
    (are [result query] (= result (create-url url query))
      (str url "?")        {}
      (str url "?a=b&c=d") (sorted-map :a "b" :c "d")))
  (let [url "http://domain.com?partner_id=abacaba"]
    (are [result query] (= result (create-url url query))
      (str url "&")        {}
      (str url "&a=b&c=d") (sorted-map :a "b" :c "d"))))

(defn authenticate-handler-with-providers
  [label providers]
  (testing (str "Providers as " label ":")
    (testing "causes 404 when a provider is not defined or provider parameter is not set"
      (let [{handler :enter} (authenticate-handler {})]
        (is (nil? (:response (handler {:request {:query-params {}}}))))
        (is (nil? (:response (handler {:request {:query-params {:provider "github"}}}))))))

    (testing "merges client-params map into request params"
      (let [{handler :enter}     (authenticate-handler providers)
            {response :response} (handler {:request {:query-params {:provider "github-2" :return "/return"}}})
            location             (get-in response [:headers "Location"])]
        (is (not (nil? location)))
        (let [query (-> location (get-query) (dissoc :state))]
          (is (= {:foo           "bar"
                  :client_id     "client-id"
                  :response_type "code"
                  :scope         "user:email"
                  :redirect_uri  "/oauth.callback"} query)))))

    (testing "successfuly redirects and stores state in the session"
      (let [{handler :enter}     (authenticate-handler providers)
            {response :response} (handler {:request {:query-params {:provider "github" :return "/return"}}})
            session-status       (get-in response [:session ::geheimtur.impl.oauth2/callback-state])
            location             (get-in response [:headers "Location"])]
        (is (not (nil? response)))
        (is (= 302 (:status response)))
        (is (not (nil? session-status)))
        (is (not (nil? (:token session-status))))
        (is (= "github" (:provider session-status)))
        (is (= "/return" (:return session-status)))
        (is (not (nil? location)))
        (let [query (-> location (get-query) (dissoc :state))]
          (is (= {:client_id     "client-id"
                  :response_type "code"
                  :scope         "user:email"
                  :redirect_uri  "/oauth.callback"} query)))))

    (testing "stores custom state in the session"
      (let [providers            (assoc-in-ps providers [:github :create-state-fn] (constantly "foo"))
            {handler :enter}     (authenticate-handler providers)
            {response :response} (handler {:request {:query-params {:provider "github" :return "/return"}}})
            session-status       (get-in response [:session ::geheimtur.impl.oauth2/callback-state])
            location             (get-in response [:headers "Location"])
            query                (get-query location)]
        (is (= "foo" (:token session-status) (:state query)))))))

(deftest authenticate-handler-test
  (authenticate-handler-with-providers "map" PROVIDERS)
  (authenticate-handler-with-providers "fn" (fn [] PROVIDERS)))

(deftest fetch-token-test
  (let [code     "123-ASD-456-QWE"
        provider (:github PROVIDERS)]

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
        provider (:github PROVIDERS)]

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

(defn callback-handler-with-providers
  [label providers]
  (testing (str "Providers as " label ":")
    (let [{handler :enter} (callback-handler providers)
          request          {:request {:query-params {:state "123" :code  "456"}
                                      :session      {::geheimtur.impl.oauth2/callback-state {:return   "/return"
                                                                                             :token    "123"
                                                                                             :provider "github"}}}}]
      (testing "redirects on authorization error"
        (let [{response :response} (handler {:request {}})]
          (is (= 302 (:status response)))
          (is (= "/unauthorized" (get-in response [:headers "Location"]))))

        (let [{response :response}
              (handler (assoc-in request [:session ::geheimtur.impl.oauth2/callback-state :token] "123456"))]
          (is (= 302 (:status response)))
          (is (= "/unauthorized" (get-in response [:headers "Location"])))))

      (testing "success without :on-success-handler"
        (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (fn [code provider]
                                                                    (when (and (= code "456")
                                                                               (= provider (:github PROVIDERS)))
                                                                      :authenticated-user))}
          #(let [{response :response} (handler request)]
             (is (= 302 (:status response)))
             (is (= "/return" (get-in response [:headers "Location"])))
             (is (= {::geheimtur.util.auth/identity :authenticated-user} (:session response))))))

      (testing "success with :on-success-handler"
        (let [request (assoc-in request [:request :session ::geheimtur.impl.oauth2/callback-state :provider] "github-2")]
          (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (fn [code provider]
                                                                      {:access-token "token-token"})}
            #(is (= :success (:response (handler request)))))))

      (testing "success with custom :check-state-fn true/false"
        (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (constantly true)}
          #(let [{true-handler :enter}  (callback-handler
                                         (assoc-in-ps providers
                                                      [:github :check-state-fn]
                                                      (constantly true)))
                 {false-handler :enter} (callback-handler
                                         (assoc-in-ps providers
                                                      [:github :check-state-fn]
                                                      (constantly false)))]
             (is (= "/return" (get-in (:response (true-handler request)) [:headers "Location"])))
             (is (= "/unauthorized" (get-in (:response (false-handler request)) [:headers "Location"]))))))

      (testing "failure with custom :check-state-fn true/false"
        (with-redefs-fn {#'geheimtur.impl.oauth2/process-callback (constantly false)}
          #(let [{true-handler :enter} (callback-handler
                                        (assoc-in-ps providers
                                                     [:github :check-state-fn]
                                                     (constantly true)))
                 {false-handler :enter} (callback-handler
                                         (assoc-in-ps providers
                                                      [:github :check-state-fn]
                                                      (constantly false)))]
             (is (= "/unauthorized" (get-in (:response (true-handler request)) [:headers "Location"])))
             (is (= "/unauthorized" (get-in (:response (false-handler request)) [:headers "Location"])))))))))

(deftest callback-handler-test
  (callback-handler-with-providers "map" PROVIDERS)
  (callback-handler-with-providers "fn" (fn [] PROVIDERS)))

