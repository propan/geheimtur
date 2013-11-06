(ns geheimtur.interceptor
  (:require [io.pedestal.service.interceptor :as interceptor :refer [interceptor definterceptorfn]]
            [geheimtur.util.auth :as auth :refer [authorized? authenticated? throw-forbidden]]
            [geheimtur.util.response :as response]
            [io.pedestal.service.log :as log]
            [geheimtur.impl.form-based :as form-based :refer [form-based-error-handler]]
            [geheimtur.impl.http-basic :refer [http-basic-authenticate http-basic-error-handler]]))

(defn access-forbidden-handler
  [silent? & {:keys [type reason reason-fn]
              :or {type :unauthenticated reason "You are not allowed to access to this resource"}}]
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

(definterceptorfn guard
  [& {:keys [roles unauthenticated-fn unauthorized-fn silent?] :or {silent? true}}]
  (let [unauthenticated-fn (or unauthenticated-fn (access-forbidden-handler silent?))
        unauthorized-fn (or unauthorized-fn (access-forbidden-handler silent? :type :unauthorized))]
    (interceptor :name ::guard
                 :enter (guard-with roles unauthenticated-fn unauthorized-fn))))
;;
;;
;;

(defn- access-forbidden-catcher
  [error-handler]
  (fn [context error]
    (let [error-data (ex-data error)
          type (::auth/type error-data)]
      (if-not (nil? type)
        (if (true? (:silent? error-data))
          (dissoc context :response ) ;; that will cause 404 error
          (error-handler context error-data))
        (throw error)))))

(definterceptorfn http-basic
  "An interceptor that provides HTTP Basic authentication for your application
   and handles authentication/authorization errors."
  [realm credential-fn]
  (interceptor :name ::http-basic-auth
               :enter (fn [{request :request
                            :as context}]
                        (if-not (authenticated? request)
                          (http-basic-authenticate context credential-fn)
                          context))
               :error (access-forbidden-catcher (http-basic-error-handler realm))))

(definterceptorfn form-based
  "An interceptor that provides Form-based authentication approach for
   handling authentication/authorization errors in your application."
  [config]
  (let [config (merge {:login-uri "/login"} config)]
    (interceptor :name ::form-based-auth
                 :error (access-forbidden-catcher (form-based-error-handler config)))))
