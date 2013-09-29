(ns geheimtur.core
  (:require [io.pedestal.service.interceptor :as interceptor :refer [interceptor definterceptorfn]]
            [io.pedestal.service.log :as log]
            [geheimtur.response :as http-response])
  (:import org.apache.commons.codec.binary.Base64))

(defn authenticate
  "Authenticates the given request context."
  [context identity]
  (assoc context ::identity identity))

(defn authenticated?
  "Checks if a context is authenticated."
  [context]
  (not (nil? (::identity context))))

(defn authorized?
  "Checks if a context has required roles."
  [context required-roles]
  (when-let [granted-roles (get-in context [::identity :roles ])]
    (some granted-roles required-roles)))

(defn throw-forbidden
  [info]
  (throw (ex-info "403 Forbidden" (merge {::type :unauthorized} info))))

(defn access-forbidden-handler
  [silent? & {:keys [type reason reason-fn]
              :or {type :unauthorized reason "You are not allowed to access to this resource"}}]
  (let [reason-fn (or reason-fn (constantly reason))]
    (fn [context]
      (throw-forbidden {:silent? silent? :reason (reason-fn type context)}))))

(defn- guard-with
  [roles unauthenticated-fn unauthorized-fn]
  (fn [context]
    (if (authenticated? context)
      (if-not (or (empty? roles)
                (authorized? context roles))
        (unauthorized-fn context)
        context)
      (unauthenticated-fn context))))

(definterceptorfn guard
  [& {:keys [roles unauthenticated-fn unauthorized-fn silent?] :or {silent? true}}]
  (let [unauthenticated-fn (or unauthenticated-fn (access-forbidden-handler silent? :type :unauthenticated))
        unauthorized-fn (or unauthorized-fn (access-forbidden-handler silent?))]
    (interceptor :name ::guard
                 :enter (guard-with roles unauthenticated-fn unauthorized-fn))))
;;
;;
;;

(defn access-forbidden-catcher
  [error-handler]
  (fn [context error]
    (let [error-data (ex-data error)
          type (::type error-data)]
      (if-not (nil? type)
        (if (true? (:silent? error-data))
          (dissoc context :response ) ;; that will cause 404 error
          (error-handler context error-data))
        (throw error)))))

;;
;; =========================== BEGIN HTTP BASIC ===========================
;;

(defn http-basic-identity
  [authorization credential-fn]
  (let [[[_ username password]] (try (-> (re-matches #"\s*Basic\s+(.+)" authorization)
                                       ^String second
                                       (.getBytes "UTF-8")
                                       Base64/decodeBase64
                                       (String. "UTF-8")
                                       (#(re-seq #"([^:]*):(.*)" %)))
                                  (catch Exception e
                                    (log/info :msg (str "Invalid Authorization header for HTTP Basic auth: "
                                                     authorization))))]
    (and username password (credential-fn username password))))

(defn http-basic-authorize
  [context credential-fn]
  (if-let [authorization (get-in context [:request :headers "authorization"])]
    (if-let [identity (http-basic-identity authorization credential-fn)]
      (authenticate context identity)
      (assoc context :response (http-response/forbidden)))
    context))

(defn http-basic-error-handler
  "The default handler for HTTP Basic authentication/authorization errors."
  [realm]
  (fn [context error]
    (assoc context :response (http-response/unauthorized realm (:reason error)))))

(definterceptorfn http-basic
  "An interceptor that provides HTTP Basic authentication for your application
   and handles authentication/authorization errors."
  [realm credential-fn]
  (interceptor :name ::http-basic-auth
               :enter (fn [context]
                        (if-not (authenticated? context)
                          (http-basic-authorize context credential-fn)
                          context))
               :error (access-forbidden-catcher (http-basic-error-handler realm))))

;;
;; =========================== END HTTP BASIC ===========================
;;

