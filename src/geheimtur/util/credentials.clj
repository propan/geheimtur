(ns geheimtur.util.credentials
  "Provides different hashing/verification functions."
  (:import java.security.SecureRandom
           java.security.spec.KeySpec
           javax.crypto.SecretKeyFactory
           javax.crypto.spec.PBEKeySpec
           org.mindrot.jbcrypt.BCrypt
           org.apache.commons.codec.binary.Base64))

(defn gen-salt
  "Generates a random salt array."
  ([]
    (gen-salt 8))
  ([size]
    (let [salt (byte-array size)]
      (-> (SecureRandom/getInstance "SHA1PRNG")
        (.nextBytes salt))
      salt)))

(defn pbkdf2-hash
  "Hashes a given plaintext password using PBKDF2, an optional
   :iterations count (defaults to 10000) and an optional salt
   (a byte array, defaults to a random array of size 8)."
  [^String password & {:keys [iterations salt] :or {iterations 10000}}]
  (let [salt (or salt (gen-salt))
        spec (PBEKeySpec. (.toCharArray password) salt iterations 160)
        bytes (.. (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA1")
                (generateSecret spec)
                (getEncoded))]
    (str (Base64/encodeBase64String salt) ":" iterations ":" (Base64/encodeBase64String bytes))))

(defn pbkdf2-verify
  "Returns true if the plaintext [password] corresponds to [hash],
  the result of previously hashing that password."
  [password hash]
  (if-let [[[_ ^String salt ^String iterations _]] (re-seq #"([^:]*):(\d+):(.*)" hash)]
    (= hash (pbkdf2-hash password :iterations (Integer. iterations) :salt (Base64/decodeBase64 salt)))
    false))

(defn bcrypt-hash
  "Hashes a given plaintext password using bcrypt and an optional
   :log-rounds (log2 of the number of rounds of hashing to apply,
   defaults to 10 as of this writing)."
  [password & {:keys [log-rounds]}]
  (BCrypt/hashpw password (if log-rounds
                            (BCrypt/gensalt log-rounds)
                            (BCrypt/gensalt))))

(defn bcrypt-verify
  "Returns true if the plaintext [password] corresponds to [hash],
  the result of previously hashing that password."
  [password hash]
  (BCrypt/checkpw password hash))

(defn create-credentials-fn
  "Creates a credentials function. Accepts:

  [identity-fn] - a function of one argument, that returns the user's
  identity corresponding to the given argument value.

  :password-key - a key to be used to resolve the hash of user's password
  from the identity map returned by identity-fn. Defaults to :password.

  :hash-verify-fn - a hash verification function. Defaults to `bcrypt-verify`."
  [identity-fn & {:keys [password-key hash-verify-fn]
                  :or {password-key :password
                       hash-verify-fn bcrypt-verify}}]
  (fn [username password]
    (let [identity (identity-fn username)
          pwd-hash (get identity password-key)]
      (and pwd-hash
        (when (hash-verify-fn password pwd-hash)
          (dissoc identity password-key))))))