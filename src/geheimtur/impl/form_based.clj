(ns geheimtur.impl.form-based
  (:require [geheimtur.util.auth :as auth :refer [authenticate-response authenticated? logout]]
            [geheimtur.util.response :as response]
            [geheimtur.util.url :as url]))

(defn default-form-reader
  [form]
  [(get form "username") (get form "password")])

(defn- form-basic-identity
  [form credential-fn]
  (let [[username password] (default-form-reader form)]
    (and username password (credential-fn username password))))

(defn default-login-handler
  [context config]
  (let [{:keys [request-method form-params] :as request} (:request context)]
    (if (= :post request-method)
      (let [redirect? (:redirect-on-login config)
            return-url (when redirect?
                         (get-in request [:params :return ]))]
        (if-let [identity (form-basic-identity form-params (:credential-fn config))]
          (-> context
            (assoc :response (response/redirect-after-post (if (and return-url
                                                                 (url/relative? return-url))
                                                             return-url "/")))
            (update-in [:response ] authenticate-response identity))
          (let [login-uri (:login-uri config)
                redirect-url (if return-url
                               (str login-uri "?error&return=" return-url)
                               (str login-uri "?error"))]
            (assoc context :response (response/redirect-after-post redirect-url)))))
      context)))

(defn default-logout-handler
  [context config]
  (-> context
    (assoc :response (response/redirect-after-post "/"))
    (logout)))

(defn form-based-authenticate
  [context config]
  (let [request (:request context)
        path (or (:path-info request)
               (:uri request))]
    (cond
      (= path (:login-uri config))
      (if-not (authenticated? request)
        ((:login-handler config) context config)
        (assoc context :response (response/redirect-after-post "/")))

      (= path (:logout-uri config))
      (if (authenticated? request)
        ((:logout-handler config) context config)
        (assoc context :response (response/redirect-after-post "/")))

      :else context)))

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
      (cond
        (= type :unauthorized )
        (unauthorized-handler context config)
        :else (unauthenticated-handler context config)))))