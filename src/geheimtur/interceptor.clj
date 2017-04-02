(ns geheimtur.interceptor
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [geheimtur.util.auth :as auth :refer [authorized? authenticated? throw-forbidden]]
            [geheimtur.util.response :as response]
            [io.pedestal.log :as log]
            [geheimtur.impl.interactive :refer [interactive-error-handler]]
            [geheimtur.impl.http-basic :refer [http-basic-authenticate http-basic-error-handler]]
            [geheimtur.impl.token :refer [token-authenticate token-error-handler]]))

(defn access-forbidden-handler
  [silent? & {:keys [type reason reason-fn]
              :or   {type :unauthenticated reason "You are not allowed to access to this resource"}}]
  (let [reason-fn (or reason-fn (constantly reason))]
    (fn [context]
      (throw-forbidden {:silent? silent? ::auth/type type :reason (reason-fn type context)}))))

(defn- guard-with
  [roles unauthenticated-fn unauthorized-fn]
  (fn [{request :request :as context}]
    (if (authenticated? request)
      (if-not (or (empty? roles)
                  (authorized? request roles))
        (unauthorized-fn context)
        context)
      (unauthenticated-fn context))))

(defn guard
  "An interceptor that allows only authenticated users that have any of :roles to access unterlying pages.

   Accepts optional parameters:
   :roles              - a set of roles that are allowed to access the page, if not defined users are required to be just authenticated
   :silent?            - if set to `true` (default), users will be getting 404 Not Found error page, when they don't have enougth access rights
   :unauthenticated-fn - a handler of unauthenticated error state
   :unauthorized-fn    - a handler of unauthorized error state"
  [& {:keys [roles unauthenticated-fn unauthorized-fn silent?] :or {silent? true}}]
  (let [unauthenticated-fn (or unauthenticated-fn (access-forbidden-handler silent?))
        unauthorized-fn    (or unauthorized-fn (access-forbidden-handler silent? :type :unauthorized))]
    (interceptor {:name  ::guard
                  :enter (guard-with roles unauthenticated-fn unauthorized-fn)})))

(defn- access-forbidden-catcher
  [error-handler]
  (fn [context error]
    (let [error-data (ex-data error)
          type       (::auth/type error-data)]
      (if-not (nil? type)
        (if (true? (:silent? error-data))
          (dissoc context :response ) ;; that will cause 404 error
          (error-handler context error-data))
        (throw error)))))

(defn http-basic
  "An interceptor that provides HTTP Basic authentication for your application
   and handles authentication/authorization errors."
  [realm credential-fn]
  (interceptor {:name  ::http-basic-auth
                :enter (fn [{request :request :as context}]
                         (if-not (authenticated? request)
                           (http-basic-authenticate context credential-fn)
                           context))
                :error (access-forbidden-catcher (http-basic-error-handler realm))}))

(defn interactive
  "An interceptor that provides interactive authentication flow for
   handling authentication/authorization errors in your application."
  [config]
  (let [config (merge {:login-uri "/login"} config)]
    (interceptor {:name  ::interactive-auth
                  :error (access-forbidden-catcher (interactive-error-handler config))})))

(defn token
  "An interceptor that provides token-based authentication.
      credential-fn - a function that given a request context and an authentication token returns the identity associated with it

   Accepts optional parameters:
      :token-fn      - a function that given a request context returns the token associated with it
      :error-fn      - a function to handle authentication/authorization errors"
  [credential-fn & options]
  (let [options (assoc options :credential-fn credential-fn)]
    (interceptor {:name  ::token-auth
                  :enter (token-authenticate options)
                  :error (access-forbidden-catcher (token-error-handler options))})))
