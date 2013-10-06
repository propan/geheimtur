(ns geheimtur.util.url
  (:import [java.net URI URISyntaxException]))

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
