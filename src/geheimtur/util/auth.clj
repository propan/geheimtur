(ns geheimtur.util.auth
  "Namespace that holds authentication/authorization related functions.")

(defn authenticate
  "Authenticates the given request context."
  [context identity]
  (assoc-in context [:request ::identity] identity))

(defn authenticated?
  "Checks if a context is authenticated."
  [context]
  (not (nil? (get-in context [:request ::identity]))))

(defn authorized?
  "Checks if a context has required roles."
  [context required-roles]
  (when-let [granted-roles (get-in context [:request ::identity :roles ])]
    (some granted-roles required-roles)))
