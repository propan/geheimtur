(ns geheimtur.impl.interactive
  (:require [geheimtur.util.auth :as auth]
            [geheimtur.util.response :as response]))

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

(defn interactive-error-handler
  "Redirects user to the login page and sets `return` parameter of the redirect URL."
  [{:keys [unauthorized-handler unauthenticated-handler]
    :or {unauthorized-handler default-unauthorized-handler
         unauthenticated-handler default-unauthenticated-handler}
    :as config}]
  (fn [context error]
    (let [type (::auth/type error)]
      (if (= type :unauthorized)
        (unauthorized-handler context config)
        (unauthenticated-handler context config)))))
