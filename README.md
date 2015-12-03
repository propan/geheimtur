# Geheimtür [![Build Status](https://travis-ci.org/propan/geheimtur.png)](https://travis-ci.org/propan/geheimtur)

a Clojure library that allows you to secure your Pedestal applications with minimum efforts.

[Live Demo] [1]

## Motivation

I do know that there is a great [friend] [2] library out there, but I had some problems making it work with a Pedestal
application and do that the way I wanted, so I decided to implement something that does (hopefully) some good work securing
Pedestal applications as easily (hopefully) as [friend] [2] does with Ring applications.

Also, I didn't want to mess around with routing that is handled quite nicely by Pedestal itself, so if an authentication flow
requires some extra routes to be added, those route should be plugged into the Pedestal routing system manually.

## Usage

Include the library in your leiningen project dependencies:

```clojure
[geheimtur "0.3.0"]
```

## Examples

You can find the sources of a demo application in [geheimtur-demo] [3] repository.

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
(defroutes routes
  [[["/" {:get views/home-page}
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

### Enabling a flow

When an unauthenticated user or a user with missing access rights reaches a page secured with `guard`, the `guard` will throw an exception that
can be handled either by the `http-basic` or `interactive` interceptor that determines which flow is going to be triggered.

#### Http-Basic

You can enable http-basic authentication by putting the `http-basic` interceptor before any of your guards. It takes the following parameters:

- `realm` - a string that will be shown to a user when s/he is prompted to enter credentials
- `credential-fn` - a function that, given a request context and a map with username and password, returns a corresponding identity

```clojure
(defroutes routes
  [[["/" {:get views/home-page ^:interceptors [(http-basic "Secure App" get-identity-from-db)]}
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

#### Form-based

You can use the `interactive` interceptor to redirect users to the login page when they are requested to be authenticated by a guard.
At this moment, it accepts only one configuration option - `:login-uri`, by default users are redirected to the `/login` page.

```clojure
(defroutes routes
  [[["/" {:get views/home-page ^:interceptors [(interactive {:login-uri "/users/login"})]
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

After doing so, you just need to add handlers that render the login page and authenticate users. Geheimtur comes with a default :POST handler
that can be used to authenticate users when you don't want to implement your own. The `form-based` interceptor requires sessions to be enabled.

```clojure
(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor]
     ["/login" {:get views/login-page :post (default-login-handler {:credential-fn credentials})}]
     ["/logout" {:get default-logout-handler}]
     ["/interactive" {:get views/interactive-index} ^:interceptors [access-forbidden-interceptor (interactive {})]
      ["/restricted" {:get views/interactive-restricted} ^:interceptors [(guard :silent? false)]]]]]])
```

A complete example can be found [here] [3].

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

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor]
     ["/login" {:get views/login-page :post login-post-handler}]
     ["/logout" {:get default-logout-handler}]
     ["/oauth.login" {:get (authenticate-handler providers)}]
     ["/oauth.callback" {:get (callback-handler providers)}]
     ["/interactive" {:get views/interactive-index} ^:interceptors [access-forbidden-interceptor (interactive {})]
      ["/restricted" {:get views/interactive-restricted} ^:interceptors [(guard :silent? false)]]]]]])
```

A complete example can be found [here] [3].

## License

Copyright © 2015 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://geheimtur.herokuapp.com
[2]: https://github.com/cemerick/friend
[3]: https://github.com/propan/geheimtur-demo
