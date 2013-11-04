(ns geheimtur.impl.form-based
  (:require [io.pedestal.service.interceptor :refer [defhandler]]
            [geheimtur.util.auth :as auth :refer [authenticate-response authenticated? logout]]
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
    :or {credential-fn (constantly nil)
         login-uri "/login"
         redirect-on-login true}
    :as config}]
  (fn [{:keys [form-params query-params] :as request}]
    (let [return-url (when redirect-on-login (or (:return form-params)
                                                 (:return query-params)))]
      (if-let [identity (form-based-identity form-params credential-fn)]
          (-> (response/redirect-after-post (if (and return-url
                                                   (url/relative? return-url))
                                                      return-url "/"))
              (authenticate-response identity))
          (let [redirect-url (if return-url
                               (str login-uri "?error=true&return=" return-url)
                               (str login-uri "?error=true"))]
            (response/redirect-after-post redirect-url))))))

(defhandler default-logout-handler
  [requst]
  (-> (response/redirect-after-post "/")
      (logout)))

(defn- default-unauthorized-handler
  [context config]
  (assoc context :response (response/forbidden)))

(defn- default-unauthenticated-handler
  [context config]
  (let [login-uri (:login-uri config)
        path (or (get-in context [:request :path-info ])
               (get-in context [:request :uri ]))
        redirect-url (str login-uri "?return=" path)]
    (assoc context :response (response/redirect-after-post redirect-url))))

(defn form-based-error-handler
  "Default form-based error handler. Redirects user to the login
   page and sets `return` parameter of the redirect URL."
  [{:keys [unauthorized-handler unauthenticated-handler]
    :or {unauthorized-handler default-unauthorized-handler
         unauthenticated-handler default-unauthenticated-handler}
    :as config}]
  (fn [context error]
    (let [type (::auth/type error)]
      (if (= type :unauthorized)
        (unauthorized-handler context config)
        (unauthenticated-handler context config)))))