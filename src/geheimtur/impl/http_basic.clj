(ns geheimtur.impl.http-basic
  (:require [io.pedestal.log :as log]
            [geheimtur.util.auth :as auth :refer [authenticate]]
            [geheimtur.util.response :as response])
  (:import org.apache.commons.codec.binary.Base64))

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

(defn http-basic-authenticate
  [context credential-fn]
  (if-let [identity (-> (get-in context [:request :headers "authorization"])
                        (http-basic-identity credential-fn))]
    (update-in context [:request] authenticate identity)
    context))

(defn http-basic-error-handler
  "The default handler for HTTP Basic authentication/authorization errors."
  [realm]
  (fn [context error]
    (assoc context :response
           (if (= :unauthorized (::auth/type error))
             (response/forbidden)
             (response/unauthorized realm (:reason error))))))
