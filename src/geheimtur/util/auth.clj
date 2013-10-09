(ns geheimtur.util.auth
  "Namespace that holds authentication/authorization related functions.")

(defn authenticate
  "Authenticates a given request."
  [request identity]
  (assoc-in request [:session ::identity ] identity))

(defn authenticate-response
  "Authenticated a given response."
  [response identity]
  (assoc-in response [:session ::identity ] identity))

(defn authenticated?
  "Checks if a request is authenticated."
  [request]
  (not (nil? (get-in request [:session ::identity ]))))

(defn authorized?
  "Checks if an authenticated request has required roles."
  [request required-roles]
  (when-let [granted-roles (get-in request [:session ::identity :roles ])]
    (some granted-roles required-roles)))

(defn logout
  "Cleans up a given context of identity information."
  [context]
  (assoc-in context [:response :session ::identity] nil))

(defn throw-forbidden
  "Throws the access forbbidden exception with [info] content.
   Default error type :unauthenticated."
  [info]
  (throw (ex-info "403 Forbidden" (merge {::type :unauthenticated} info))))
