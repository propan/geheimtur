(ns geheimtur.impl.http-basic
  (:require [io.pedestal.service.log :as log]
            [geheimtur.util.auth :refer [authenticate]]
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
  (if-let [authorization (get-in context [:request :headers "authorization"])]
    (if-let [identity (http-basic-identity authorization credential-fn)]
      (authenticate context identity)
      (assoc context :response (response/forbidden)))
    context))

(defn http-basic-error-handler
  "The default handler for HTTP Basic authentication/authorization errors."
  [realm]
  (fn [context error]
    (assoc context :response (response/unauthorized realm (:reason error)))))
