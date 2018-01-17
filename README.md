# Geheimtür [![Build Status](https://travis-ci.org/propan/geheimtur.png)](https://travis-ci.org/propan/geheimtur)

a Clojure library that allows you to secure your Pedestal applications with minimum efforts.

[Live Demo](http://geheimtur.herokuapp.com)

## Motivation

I do know that there is a great [friend](https://github.com/cemerick/friend) library out there, but I had some problems making it work with a Pedestal application and do that the way I wanted, so I decided to implement something that does (hopefully) some good work securing Pedestal applications as easily (hopefully) as [friend](https://github.com/cemerick/friend) does with Ring applications.

Also, I didn't want to mess around with routing that is handled quite nicely by Pedestal itself, so if an authentication flow
requires some extra routes to be added, those route should be plugged into the Pedestal routing system manually.

## ChangeLog

The ChangeLog and migration instructions can be found in [CHANGES.md](CHANGES.md).

## Usage

Include the library in your leiningen project dependencies:

```clojure
[geheimtur "0.3.4"]
```

## Examples

You can find the sources of a demo application in [geheimtur-demo](https://github.com/propan/geheimtur-demo) repository.

**The examples below do not duplicate information available as docstrings, if you want to know all available options - check the docs in the code.**

### Securing a page

When you need to limit access to a specific page or a sub-tree of pages, you just add the `guard` interceptor to the desired location.
You can adjust the interceptor behaviour using the following optional parameters:

- `:roles` - a set of roles that are allowed to access the page, if not defined users are required to just be authenticated
- `:silent?` - a flag to affect unauthorized/unauthenticated behaviours. If set to `true` (default), users will be getting a 404 Not Found error page when they don't have access rights
- `:unauthenticated-fn` - an unauthenticated error state handler. It's a function that accepts a Pedestal context. The default implementation, throws an exception with a type of `:unauthenticated`
- `:unauthorized-fn` - an unauthorized error state handler. It's a function that accepts a Pedestal context. The default implementation, throws an exception with a type of `:unauthorized`

In the example below, only administrators are allowed to access the pages under the `/admin` path, the rest will be getting a 404 responses (Note:
this is an illustration of `guard` usage and not a completely functional example.)

```clojure
(def routes
  #{["/" :get `views/home-page]
    ["/admin" :get [(guard :roles #{:admin}) `views/admin]]})
```

### Enabling a flow

When an unauthenticated user or a user with missing access rights reaches a page secured with `guard`, the `guard` will throw an exception that
can be handled either by the `http-basic` or `interactive` interceptor that determines which flow is going to be triggered.

#### Http-Basic

You can enable http-basic authentication by putting the `http-basic` interceptor before any of your guards. It takes the following parameters:

- `realm` - a string that will be shown to a user when s/he is prompted to enter credentials
- `credential-fn` - a function that, given a request context and a map with username and password, returns a corresponding identity

```clojure
(def routes
  #{["/"      :get [(http-basic "Secure App" get-identity-from-db) `views/home-page]]
    ["/admin" :get [(http-basic "Secure App" get-identity-from-db) (guard :roles #{:admin}) `views/admin]]})
```

#### Form-based

You can use the `interactive` interceptor to redirect users to the login page when they are requested to be authenticated by a guard.
At this moment, it accepts only one configuration option - `:login-uri`, by default users are redirected to the `/login` page.

```clojure
(def routes
  #{["/"      :get [(interactive {:login-uri "/users/login"}) `views/home-page]]
    ["/admin" :get [(interactive {:login-uri "/users/login"}) (guard :roles #{:admin}) `views/admin]])
```

After doing so, you just need to add handlers that render the login page and authenticate users. Geheimtur comes with a default :POST handler
that can be used to authenticate users when you don't want to implement your own. The `form-based` interceptor requires sessions to be enabled.

```clojure
(def common-interceptors [(body-params/body-params) bootstrap/html-body session-interceptor])
(def interactive-interceptors (into common-interceptors [access-forbidden-interceptor (interactive {})]))

(def routes
  #{["/"                       :get (conj common-interceptors `views/home-page)]
    ["/login"                  :get (conj common-interceptors `view/login-page)]
    ["/login"                  :post (conj common-interceptors (default-login-handler {:credential-fn credentials
                                                                                       :form-reader   identity}))]
    ["/logout"                 :get (conj common-interceptors default-logout-handler)]
    ["/interactive"            :get (conj interactive-interceptors `views/interactive-index)]
    ["/interactive/restricted" :get (into interactive-interceptors [(guard :silent? false) `views/interactive-restricted])]})
```

A complete example can be found [here](https://github.com/propan/geheimtur-demo).

#### OAuth 2.0

You can use the same `interactive` inteceptor to redirect users to a page where they choose supported identity providers.
Geheimtur provides handlers for users redirection and callbacks out of the box, all you need to do is to configure providers available for your users.

**Please see `authenticate-handler` documentation for the description of all possible provider options.**

```clojure
(def providers
  {:github {:auth-url           "https://github.com/login/oauth/authorize"
            :client-id          (or (System/getenv "github.client_id") "client-id")
            :client-secret      (or (System/getenv "github.client_secret") "client-secret")
            :scope              "user:email"
            :token-url          "https://github.com/login/oauth/access_token"
            :user-info-url      "https://api.github.com/user"
            :user-info-parse-fn #(-> % :body (parse-string true))}})

(def common-interceptors [(body-params/body-params) bootstrap/html-body session-interceptor])
(def interactive-interceptors (into common-interceptors [access-forbidden-interceptor (interactive {})]))

(def routes
  #{["/"                       :get (conj common-interceptors `views/home-page)]
    ["/login"                  :get (conj common-interceptors `view/login-page)]
    ["/login"                  :post (conj common-interceptors (default-login-handler {:credential-fn credentials
                                                                                       :form-reader   identity}))]
    ["/oauth.login"            :get (conj common-interceptors (authenticate-handler providers))]
    ["/oauth.callback"         :get (conj common-interceptors (callback-handler providers))]
    ["/logout"                 :get (conj common-interceptors default-logout-handler)]
    ["/interactive"            :get (conj interactive-interceptors `views/interactive-index)]
    ["/interactive/restricted" :get (into interactive-interceptors [(guard :silent? false) `views/interactive-restricted])]})
```

A complete example can be found [here](https://github.com/propan/geheimtur-demo).

#### Token-based

If you would like to secure your API using bearer tokens (or any other kind of tokens), `token` interceptor is something you might want to consider.

**Please see `token` interceptor documentation for the description of all possible options.**

```clojure
(def routes
  #{["/api/restricted" :get  [http/json-body (token token-credentials) (guard :silent? false) `views/api-restricted]]})
```

A complete example can be found [here](https://github.com/propan/geheimtur-demo).

## License

Copyright © 2015-2018 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
