(ns geheimtur.impl.form-based
  (:require [geheimtur.util.auth :refer [authenticate logout]]
            [geheimtur.util.response :as response]
            [geheimtur.util.url :as url]))

(defn- default-form-reader
  [form]
  [(get form "username") (get form "password")])

(defn default-login-handler
  "Creates a handler for POST login requests.

  Optional parameters:
      :login-uri         - a login uri where users are redirected on authentication error
      :form-reader       - a function that given :form-params returns a pair of username and password
      :credential-fn     - a function that given username and password returns the identity associated with them
      :redirect-on-login - a flag that enables redirection to :return uri, default is true"
  [{:keys [credential-fn form-reader login-uri redirect-on-login]
    :or   {credential-fn    (constantly nil)
           form-reader       default-form-reader
           login-uri         "/login"
           redirect-on-login true}
    :as   config}]
  (fn [{:keys [form-params query-params] :as request}]
    (let [return-url          (when redirect-on-login (or (:return form-params)
                                                          (:return query-params)))
          [username password] (form-reader form-params)]
      (if-let [identity (and username password (credential-fn username password))]
        (authenticate
         (response/redirect-after-post (if (and return-url
                                                (url/relative? return-url))
                                         return-url "/"))
         identity)
        (let [redirect-url (if return-url
                             (str login-uri "?error=true&return=" return-url)
                             (str login-uri "?error=true"))]
          (response/redirect-after-post redirect-url))))))

(defn default-logout-handler
  "Returns a Ring response that redirects to / and unsets identity associated with corrent session."
  [request]
  (logout (response/redirect-after-post "/")))
