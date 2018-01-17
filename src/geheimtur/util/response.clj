(ns geheimtur.util.response
  "Contains functions to create Ring responses.")

(defn redirect
  "Returns an HTTP 302 redirect response."
  [url]
  {:status  302
   :headers {"Location" url}
   :body    ""})

(defn redirect-after-post
  "Returns an HTTP 303 redirect response."
  [url]
  {:status  303
   :headers {"Location" url}
   :body    ""})

(defn forbidden
  "Returns an HTTP 403 Forbidden response."
  []
  {:status  403
   :headers {}
   :body    "You are not allowed to access this resource"})

(defn unauthorized
  "Returns an HTTP 401 Unauthorized response."
  [realm reason]
  {:status  401
   :headers {"WWW-Authenticate" (format "Basic realm=\"%s\"" realm)}
   :body    reason})
