(ns geheimtur.impl.token
  (:require [geheimtur.util.auth :as auth]
            [geheimtur.util.response :as response]))

(defn- default-error-handler
  [context error]
  (response/forbidden))

(defn- bearer-token-parser
  "Extracts bearer type tokens from a request context."
  [context]
  (when-let [authorization (get-in context [:request :headers "authorization"])]
    (-> (re-matches #"\s*Bearer\s+(.+)" authorization) ^String second)))

(defn- token-identity
  [context credential-fn token]
  (when token
    (credential-fn context token)))

(defn token-authenticate
  "Creates token-based authentication handler.
  
  Optional parameters:
      :token-fn      - a function that given a request context returns the token associated with it
      :credential-fn - a function that given a request context and an authentication token returns the identity associated with it"
  [{:keys [token-fn credential-fn]
    :or   {token-fn      bearer-token-parser
           credential-fn (constantly nil)}}]
  (fn [context]
    (if-let [identity (->> (token-fn context) (token-identity context credential-fn))]
      (update-in context [:request] auth/authenticate identity)
      context)))

(defn token-error-handler
  "The default handler for token-based authentication/authorization errors.

  Optional parameters:
      :error-fn - a function that given a request context and an authentication error returns a corresponding HTTP response"
  [{:keys [error-fn] :or {error-fn default-error-handler}}]
  (fn [context error]
    (assoc context :response (error-fn context error))))

