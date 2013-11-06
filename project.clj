(defproject geheimtur "0.1.0"
  :description "a secret door to your Pedestal application"
  :url "http://github.com/propan/geheimtur"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.1"]
                 ;; BCrypt
                 [org.mindrot/jbcrypt "0.3m"]
                 ;; base64
                 [commons-codec "1.6"]
                 ;; integration tests
                 [javax.servlet/javax.servlet-api "3.0.1" :scope "test"]
                 [org.slf4j/slf4j-nop "1.7.5" :scope "test"]])
