(ns geheimtur.util.url
  (:require [clojure.walk :refer [keywordize-keys]]
            [ring.util.codec :refer [form-decode]])
  (:import [java.net URI URL URISyntaxException MalformedURLException]))

(defn absolute?
  "Checks if a URL is absolute."
  [^String url]
  (try
    (..
      (URI. url)
      isAbsolute)
    (catch URISyntaxException e
      false)))

(defn relative?
  "Checks if a URL is relative."
  [^String url]
  (not (absolute? url)))

(defn get-query
  "Returns a query map resolved from the given URL."
  [url]
  (try
    (when-let [query (.. (URL. url) getQuery)]
      (-> query
          form-decode
          keywordize-keys))
    (catch MalformedURLException e
      nil)))
