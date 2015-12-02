(ns geheimtur.util.credentials-test
  (:require [clojure.test :refer :all ]
            [geheimtur.util.credentials :refer :all ]))

(deftest pbkdf2-hashing
  (are [res hash] (= res (pbkdf2-verify "password" hash))
    true (pbkdf2-hash "password")
    true (pbkdf2-hash "password" :iterations 5000)
    true (pbkdf2-hash "password" :iterations 5000 :salt (gen-salt 16))
    false (pbkdf2-hash "wrong-password")
    false (pbkdf2-hash "wrong-password" :iterations 5000)
    false (pbkdf2-hash "wrong-password" :iterations 5000 :salt (gen-salt 16))))

(deftest bcrypt-hashing
  (are [res hash] (= res (bcrypt-verify "password" hash))
    true (bcrypt-hash "password")
    true (bcrypt-hash "password" :log-rounds 5)
    false (bcrypt-hash "wrong-password")
    false (bcrypt-hash "wrong-password" :log-rounds 5)))

(def users {"dale.cooper"  {:name "Dale" :last-name "Cooper" :password (bcrypt-hash "password")}
            "jack.isidore" {:name "Jack" :last-name "Isidore" :pwd (pbkdf2-hash "password")}})

(deftest default-credentials-fn
  (let [credentials-fn (create-credentials-fn users)]
    (are [res user password] (= res (:last-name (credentials-fn {} {:username user :password password})))
      "Cooper" "dale.cooper" "password"
      nil "dale.cooper" "wrong-password"
      nil "dale" "password"
      nil "jack.isidore" "password")))

(deftest pbkdf2-credentials-fn
  (let [credentials-fn (create-credentials-fn
                        users :hash-verify-fn pbkdf2-verify :password-key :pwd)]
    (are [res user password] (= res (:last-name (credentials-fn {} {:username user :password password})))
      "Isidore" "jack.isidore" "password"
      nil "jack.isidore" "wrong-password"
      nil "jack" "password"
      nil "dale.cooper" "password")))
