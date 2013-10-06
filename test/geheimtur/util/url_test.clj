(ns geheimtur.util.url-test
  (:require [clojure.test :refer :all ]
            [geheimtur.util.url :refer :all ]))

(deftest relative-test
  (are [res url] (= res (relative? url))
    true "/"
    true "/../../test"
    true "./../test"
    true ""
    false "http://evil.com"
    false "https://more-evil.com/test"))