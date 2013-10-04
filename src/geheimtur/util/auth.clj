(ns geheimtur.util.auth)

(defn authenticate
  "Authenticates the given request context."
  [context identity]
  (assoc context ::identity identity))

(defn authenticated?
  "Checks if a context is authenticated."
  [context]
  (not (nil? (::identity context))))

(defn authorized?
  "Checks if a context has required roles."
  [context required-roles]
  (when-let [granted-roles (get-in context [::identity :roles ])]
    (some granted-roles required-roles)))
