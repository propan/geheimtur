(ns geheimtur.impl.oauth2
  (:require [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [geheimtur.util.response :as response]
            [geheimtur.util.auth :refer [authenticate]]
            [io.pedestal.service.log :as log]
            [ring.util.codec :as ring-codec])
  (:import [java.math BigInteger]
           [java.security SecureRandom]))

(defn create-afs-token
  "Creates a random state token to prevent request forgery."
  []
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
                 :token-url          \"https://github.com/login/oauth/access_token\"
                 :user-info-url      \"https://api.github.com/user\"
                 :user-info-parse-fn #(parse-string % true)
                 :on-success-handler on-github-success}})

  The following keys in provider's configuration are optional:
      :user-info-url      - if defined, will be used to get user's details after successful access token acquisition
      :user-info-parse-fn - if definded, will be applied to the response body of user's details response
      :on-success-handler - a function that accepts an obtained identity/access token map, that should return correct ring response.
                            It is called only if an identity/access token is resolved."
  [providers]
  (fn [{:keys [query-params] :as request}]
    (when-let [provider (:provider query-params)]
      (when-let [{:keys [auth-url client-id scope callback-uri]} (get providers (keyword provider))]
        (let [token (create-afs-token)
              query {:client_id     client-id
                     :response_type "code"
                     :scope         scope
                     :state         token}
              query (if callback-uri
                      (assoc query :redirect_uri callback-uri)
                      query)
              path  (or (:return query-params) "/")]
          (-> (create-url auth-url query)
              (response/redirect)
              (assoc-in [:session ::callback-state] {:return   path
                                                     :token    token
                                                     :provider provider})))))))

(defn fetch-token
  "Fetches an OAuth access token using the given code and provider's configuration."
  [code {:keys [token-url client-id client-secret callback-uri] :as provider}]
  (let [query {:code          code
               :client_id     client-id
               :client_secret client-secret
               :grant_type    "authorization_code"}
        query (if callback-uri
                (assoc query :redirect_uri callback-uri)
                query)]
    (try
      (let [response (client/post token-url {:form-params query})]
        (when (client/success? response)
          (keywordize-keys (ring-codec/form-decode (:body response)))))
      (catch Exception ex
        (log/warn :msg (str "Could not fetch OAuth access token from " token-url)
                  :exception ex)
        nil))))

(defn fetch-user-info
  [url token]
  "Fetches user's details using the given URL and OAuth access token."
  (try
    (let [response (client/get url {:oauth-token token})]
      (when (client/success? response)
        (:body response)))
    (catch Exception ex
      (log/warn :msg (str "Could not fetch user details from " url)
                :exception ex)
      nil)))

(defn resolve-identity
  "Resolves user's identity based on provider's configuration.

   Accepts:
       token    - an OAuth access token
       provider - a provider's configuration"
  [{:keys [access_token] :as token}
   {:keys [user-info-url user-info-parse-fn] :or {user-info-parse-fn identity} :as provider}]
  (let [result {:access-token access_token}]
    (if user-info-url
      (when-let [user-info (fetch-user-info user-info-url access_token)]
        (assoc result :identity (user-info-parse-fn user-info)))
      result)))

(defn- process-callback
  [code provider]
  (when-let [token (fetch-token code provider)]
    (resolve-identity token provider)))

(defn callback-handler
  "Creates an OAuth call-back handler based on a map of OAuth providers.

  If authentication flow fails for any reason, the user will be redirected to /unauthorised url."
  [providers]
  (fn [{:keys [query-params session] :as request}]
    (let [{:keys [state code]}               query-params
          {:keys [return token provider]}    (::callback-state session)
          {:keys [on-success-handler] :as p} (get providers (keyword provider))]
      (if (and state code return token provider (= state token) p)
        (if-let [identity (process-callback code p)]
          (if on-success-handler
            (on-success-handler (assoc identity :return return))
            (-> (response/redirect return)
                (authenticate identity)))
          (response/redirect "/unauthorized"))
        (response/redirect "/unauthorized")))))
