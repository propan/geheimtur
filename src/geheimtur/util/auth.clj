(ns geheimtur.util.auth
  "Namespace that holds authentication/authorization related functions.")

(defn get-identity
  "Returns the identity associated with the given request."
  [request]
  (get-in request [:session ::identity]))

(defn authenticate
  "Authenticates the given request."
  [request identity]
  (assoc-in request [:session ::identity] identity))

(defn authenticate-response
  "Authenticated the given response."
  [response identity]
  (assoc-in response [:session ::identity] identity))

(defn authenticated?
  "Checks if the given request is authenticated."
  [request]
  (not (nil? (get-identity request))))

(defn authorized?
  "Checks if an authenticated request has required roles."
  [request required-roles]
  (when-let [granted-roles (get-in request [:session ::identity :roles])]
    (some granted-roles required-roles)))

(defn logout
  "Cleans up the given response of identity information."
  [response]
  (assoc-in response [:session ::identity] nil))

(defn throw-forbidden
  "Throws the access forbbidden exception with [info] content.
   Default error type :unauthenticated."
  [info]
  (throw (ex-info "403 Forbidden" (merge {::type :unauthenticated} info))))
