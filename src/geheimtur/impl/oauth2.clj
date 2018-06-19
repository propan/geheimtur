(ns geheimtur.impl.oauth2
  (:require [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [geheimtur.util.response :as response]
            [geheimtur.util.auth :refer [authenticate]]
            [io.pedestal.log :as log]
            [io.pedestal.interceptor.helpers :as h]
            [ring.util.codec :as ring-codec])
  (:import [java.math BigInteger]
           [java.security SecureRandom]))

(defn create-afs-token
  "Creates a random state token to prevent request forgery."
  [_]
  (.toString (BigInteger. 130 (SecureRandom.)) 32))

(defn create-url
  "Creates a URL from a url string and a map with query parameters."
  [url query]
  (->> query
       ring-codec/form-encode
       (str url "?")))

(defn authenticate-handler
  "Creates a handler that redirects users to OAuth2 providers based on a map of providers.

   Example:
   (def providers
       {:github {:auth-url           \"https://github.com/login/oauth/authorize\"
                 :client-id          \"your-client-id\"
                 :client-secret      \"your-client-secret\"
                 :scope              \"user:email\"
                 :client-params      {:foo \"bar\"}
                 :create-state-fn    (fn [req] (generate-a-token req))
                 :token-url          \"https://github.com/login/oauth/access_token\"
                 :token-parse-fn     (fn [resp] (parse-string (:body resp)))
                 :user-info-url      \"https://api.github.com/user\"
                 :user-info-parse-fn (fn [resp] (parse-string (:body resp)))
                 :on-success-handler on-github-success}})

  The following keys in provider's configuration are optional:
      :client-params      - a map of extra query parameters to be included in the authorization request
      :create-state-fn    - a function that accepts the authentication request and returns a state token.
      :token-parse-fn     - a function that accepts the token endpoint response and returns a map with the parsed
                            OAuth2 token response. The successfuly parsed response must have at least :access_token key.
      :user-info-url      - if defined, will be used to get user's details after successful access token acquisition
      :user-info-parse-fn - if defined, will be applied to the response of user's details endpoint
      :on-success-handler - a function that accepts a request context and an obtained identity/access token map and returns a correct ring response.
                            It is called only if an identity/access token is resolved."
  [providers]
  (h/handler
   ::authenticate-handler
   (fn [req]
     (let [{:keys [query-params] :as request} req]
       (when-let [provider (:provider query-params)]
         (when-let [{:keys [auth-url
                            client-id
                            scope
                            callback-uri
                            client-params
                            create-state-fn]
                     :or {create-state-fn
                          create-afs-token}} (get providers (keyword provider))]
           (let [token (create-state-fn request)
                 query (merge client-params {:client_id     client-id
                                             :response_type "code"
                                             :scope         scope
                                             :state         token})
                 query (if callback-uri
                         (assoc query :redirect_uri callback-uri)
                         query)
                 path  (or (:return query-params) "/")]
             (-> (create-url auth-url query)
                 (response/redirect)
                 (assoc-in [:session ::callback-state] {:return   path
                                                        :token    token
                                                        :provider provider})))))))))

(defn fetch-token
  "Fetches an OAuth access token using the given code and provider's configuration."
  [code {:keys [token-url client-id client-secret callback-uri token-parse-fn] :as provider}]
  (let [query {:code          code
               :client_id     client-id
               :client_secret client-secret
               :grant_type    "authorization_code"}
        query (if callback-uri
                (assoc query :redirect_uri callback-uri)
                query)]
    (try
      (let [response (client/post token-url {:form-params           query
                                             :throw-entire-message? true
                                             :as                    (when (nil? token-parse-fn) :auto)})]
        (when (client/success? response)
          (if (nil? token-parse-fn)
            (:body response)
            (token-parse-fn response))))
      (catch Exception ex
        (log/warn :msg (str "Could not fetch OAuth access token from " token-url)
                  :exception ex)
        nil))))

(defn fetch-user-info
  [token url response-parse-fn]
  "Fetches user's details using the given URL and an OAuth access token.

   Accepts:
       token             - an OAuth token to be used to fetch the user details
       url               - a URL to fetch user details from
       response-parse-fn - a function to be used to parse the response of the endpoint (optional)"
  (try
    (let [response (client/get url {:oauth-token           token
                                    :throw-entire-message? true
                                    :as                    (when (nil? response-parse-fn) :auto)})]
      (if (nil? response-parse-fn)
        (:body response)
        (response-parse-fn response)))
    (catch Exception ex
      (log/warn :msg (str "Could not fetch user details from " url)
                :exception ex)
      nil)))

(defn resolve-identity
  "Resolves user's identity based on provider's configuration.

   Accepts:
       token    - an OAuth access token response. Must contain at least :access_token.
       provider - a provider's configuration"
  [{:keys [access_token expires_in refresh_token] :as token}
   {:keys [user-info-url user-info-parse-fn]}]
  (when access_token
    (let [result {:access-token  access_token
                  :expires-in    expires_in
                  :refresh-token refresh_token}]
      (if user-info-url
        (when-let [user-info (fetch-user-info access_token user-info-url user-info-parse-fn)]
          (assoc result :identity user-info))
        result))))

(defn- process-callback
  [code provider]
  (when-let [token (fetch-token code provider)]
    (resolve-identity token provider)))

(defn callback-handler
  "Creates an OAuth call-back handler based on a map of OAuth providers.

  If authentication flow fails for any reason, the user will be redirected to /unauthorized url."
  [providers]
  (h/before
   ::callback-handler
   (fn [{request :request :as context}]
     (let [{:keys [query-params session]}     request
           {:keys [state code]}               query-params
           {:keys [return token provider]}    (::callback-state session)
           {:keys [on-success-handler] :as p} (get providers (keyword provider))]
       (assoc context :response
              (if (and state code return token provider (= state token) p)
                (if-let [identity (process-callback code p)]
                  (if on-success-handler
                    (on-success-handler context (assoc identity :return return))
                    (authenticate (response/redirect return) identity))
                  (response/redirect "/unauthorized"))
                (response/redirect "/unauthorized")))))))
