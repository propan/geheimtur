(ns geheimtur.impl.form-based
  (:require [geheimtur.util.auth :refer [authenticate logout]]
            [geheimtur.util.response :as response]
            [geheimtur.util.url :as url]
            [io.pedestal.interceptor.helpers :as h]))

(defn default-login-handler
  "Creates a handler for POST login requests.

  Optional parameters:
      :login-uri         - a login uri where users are redirected on authentication error
      :form-reader       - a function that given :form-params returns a map that contains :username and :password keys
      :credential-fn     - a function that given a request context and a map of credentials returns the identity associated with them
      :redirect-on-login - a flag that enables redirection to :return uri, default is true"
  [{:keys [credential-fn form-reader login-uri redirect-on-login]
    :or   {credential-fn    (constantly nil)
           form-reader       identity
           login-uri         "/login"
           redirect-on-login true}
    :as   config}]
  (h/before
   ::default-login-handler
   (fn [{request :request :as context}]
     (let [{:keys [form-params query-params]} request
           return-url                         (when redirect-on-login (or (:return form-params)
                                                                          (:return query-params)))
           credentials                        (form-reader form-params)]
       (assoc context :response
              (if-let [identity (and credentials (credential-fn context credentials))]
                (authenticate
                 (response/redirect-after-post (if (and return-url (url/relative? return-url))
                                                 return-url
                                                 "/"))
                 identity)
                (let [redirect-url (if return-url
                                     (str login-uri "?error=true&return=" return-url)
                                     (str login-uri "?error=true"))]
                  (response/redirect-after-post redirect-url))))))))

(def default-logout-handler
  "Returns a Ring response that redirects to / and unsets identity associated with corrent session."
  (h/handler
   ::default-logout-handler
   (fn [request]
     (logout (response/redirect-after-post "/")))))
