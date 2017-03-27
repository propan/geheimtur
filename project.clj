(defproject geheimtur "0.3.1"
  :description "a secret door to your Pedestal application"
  :url "http://github.com/propan/geheimtur"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :exclusions [org.clojure/clojure]
  :dependencies [[io.pedestal/pedestal.service "0.5.2"]
                 [org.mindrot/jbcrypt "0.4"]
                 [commons-codec "1.10"]
                 [clj-http "3.4.1"]
                 ;; optional dependencies of clj-http
                 ;; to enable response body coersion
                 [cheshire "5.7.0"]
                 [ring/ring-codec "1.0.1"]
                 ;; integration tests
                 [javax.servlet/javax.servlet-api "3.1.0" :scope "test"]
                 [org.slf4j/slf4j-nop "1.7.25" :scope "test"]]
  :deploy-repositories [["releases" :clojars {:creds :gpg}]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :aliases {"all" ["with-profile" "dev,1.7:dev"]})
