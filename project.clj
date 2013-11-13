(defproject geheimtur "0.1.1"
  :description "a secret door to your Pedestal application"
  :url "http://github.com/propan/geheimtur"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.1"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [commons-codec "1.6"]
                 [clj-http "0.7.7"]
                 [ring/ring-codec "1.0.0"]
                 ;; integration tests
                 [javax.servlet/javax.servlet-api "3.0.1" :scope "test"]
                 [org.slf4j/slf4j-nop "1.7.5" :scope "test"]])
