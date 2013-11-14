(ns geheimtur.util.auth-test
  (:require [clojure.test :refer :all ]
            [geheimtur.util.auth :as auth :refer :all ]))

(defn- create-request
  [roles]
  {:session {::auth/identity {:roles roles}}})

(deftest get-identity-test
  (let [identity {:name "Dale"}]
    (is (= identity (get-identity (authenticate {} identity))))))

(deftest authenticate-test
  (are [res request] (= res (authenticated? request))
    false {}
    true (authenticate {} {:name "Dale" :last-name "Cooper" :roles #{:agent }})))

(deftest authorized-test
  (let [allowed-roles #{:editor :writer }]
    (are [forbidden request] (= forbidden (nil? (authorized? request allowed-roles)))
      true {}
      true (create-request {})
      true (create-request #{:user })
      false (create-request #{:user :editor })
      false (create-request #{:user :writer :editor }))))

