(ns geheimtur.util.auth-test
  (:require [clojure.test :refer :all ]
            [geheimtur.util.auth :as core :refer :all ]))

(defn- create-context
  [roles]
  {:request {::core/identity {:roles roles}}})

(deftest authenticate-test
  (are [res context] (= res (authenticated? context))
    false {}
    true (authenticate {} {:name "Dale" :last-name "Cooper" :roles #{:agent }})))

(deftest authorized-test
  (let [allowed-roles #{:editor :writer }]
    (are [forbidden context] (= forbidden (nil? (authorized? context allowed-roles)))
      true {}
      true (create-context {})
      true (create-context #{:user })
      false (create-context #{:user :editor })
      false (create-context #{:user :writer :editor }))))

