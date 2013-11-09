(ns geheimtur.impl.oauth2
  (:require [clj-http.client :as client]
            [clojure.walk :refer [keywordize-keys]]
            [geheimtur.util.response :as response]
            [geheimtur.util.auth :refer [authenticate]]
            [io.pedestal.service.interceptor :refer [defhandler]]
            [ring.util.codec :as ring-codec])
  (:import [java.math BigInteger]
           [java.security SecureRandom]))

(defn create-afs-token
  "Creates a random state token to prevent request forgery."
  []
  (.toString (BigInteger. 130 (SecureRandom.)) 32))

(defn create-url
  [url query]
  (->> query
       ring-codec/form-encode
       (str url "?")))

(defn authenticate-handler
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
              path  (or (:path-info request)
                        (:uri request))]
          (-> (create-url auth-url query)
              (response/redirect)
              (assoc-in [:session ::callback-state] {:return   path
                                                     :token    token
                                                     :provider provider})))))))

(defn fetch-token
  [code {:keys [token-url client-id client-secret callback-uri]}]
  (let [query {:code          code
               :client_id     client-id
               :client_secret client-secret
               :grant_type    "authorization_code"}
        query (if callback-uri
                (assoc query :redirect_uri callback-uri)
                query)]
    (let [response (client/post token-url {:form-params query})]
      (when (client/success? response)
        (keywordize-keys (ring-codec/form-decode (:body response)))))))

(defn fetch-user-info
  [url token]
  (let [response (client/get url {:oauth-token token})]
    (when (client/success? response)
      (:body response))))

(defn resolve-identity
  [{:keys [access_token]}
   {:keys [user-info-url user-info-parse-fn] :or {user-info-parse-fn identity}}]
  (let [result {:access-token access_token}]
    (if user-info-url
      (when-let [user-info (fetch-user-info user-info-url access_token)]
        (assoc result :identity (user-info-parse-fn user-info)))
      result)))

(defn callback-handler
  [providers]
  (fn [{:keys [query-params session] :as request}]
    (when-let [{:keys [state code]} query-params]
      (when-let [{:keys [return token provider]} (::callback-state session)]
        (if (= state token)
          (let [{:keys [on-success-handler] :as p} (get providers (keyword provider))]
            (if-let [token (fetch-token code p)]
              (if-let [identity (resolve-identity token p)]
                (if on-success-handler
                  (on-success-handler identity) ;; TODO: here we are loosing return url
                  (-> (response/redirect "/")
                      (authenticate identity)))
                (response/redirect "/unauthorized"));;TODO
              (response/redirect "/unauthorized")))
          (response/redirect-after-post "/unauthorized")))))) ;; TODO
