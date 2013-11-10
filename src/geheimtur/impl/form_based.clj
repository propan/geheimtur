(ns geheimtur.impl.form-based
  (:require [io.pedestal.service.interceptor :refer [defhandler]]
            [geheimtur.util.auth :refer [authenticate logout]]
            [geheimtur.util.response :as response]
            [geheimtur.util.url :as url]))

(defn default-form-reader
  [form]
  [(get form "username") (get form "password")])

(defn- form-based-identity
  [form credential-fn]
  (let [[username password] (default-form-reader form)]
    (and username password (credential-fn username password))))

(defn default-login-handler
  [{:keys [credential-fn login-uri redirect-on-login]
    :or {credential-fn     (constantly nil)
         login-uri         "/login"
         redirect-on-login true}
    :as config}]
  (fn [{:keys [form-params query-params] :as request}]
    (let [return-url (when redirect-on-login (or (:return form-params)
                                                 (:return query-params)))]
      (if-let [identity (form-based-identity form-params credential-fn)]
          (-> (response/redirect-after-post (if (and return-url
                                                   (url/relative? return-url))
                                                      return-url "/"))
              (authenticate identity))
          (let [redirect-url (if return-url
                               (str login-uri "?error=true&return=" return-url)
                               (str login-uri "?error=true"))]
            (response/redirect-after-post redirect-url))))))

(defhandler default-logout-handler
  [requst]
  (-> (response/redirect-after-post "/")
      (logout)))
