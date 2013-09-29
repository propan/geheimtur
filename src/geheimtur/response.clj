(ns geheimtur.response)

(defn forbidden
  "Returns an HTTP 403 Forbidden response."
  []
  {:status 403
   :body "You are not allowed to access to this resource"
   :headers {}})

(defn unauthorized
  "Returns an HTTP 401 Unauthorized response."
  [realm reason]
  {:status 401
   :body reason
   :headers {"WWW-Authenticate" (format "Basic realm=\"%s\"" realm)}})
