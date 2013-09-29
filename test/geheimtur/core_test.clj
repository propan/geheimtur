(ns geheimtur.core-test
  (:require [clojure.test :refer :all ]
            [geheimtur.core :as core :refer :all ]
            ))

(defn create-context
  [roles]
  {::core/identity {:roles roles}})

(deftest authorized-test
  (let [allowed-roles #{:editor :writer }]
    (are [forbidden context] (= forbidden (nil? (authorized? context allowed-roles)))
      true {}
      true (create-context {})
      true (create-context #{:user })
      false (create-context #{:user :editor })
      false (create-context #{:user :writer :editor }))))