# Geheimtür

a Clojure library that allows you to secure your Pedestal applications with minimum efforts.

[Live Demo] [1]

## Motivation

I do know that there is a great [friend] [2] library out there, but I had some problems making it work with a Pedestal
application and do that the way I wanted, so I decided to implement something that does (hopefully) some good work securing
Pedestal applications as easily (hopefully) as [friend] [2] does with Ring applications.

Also, I didn't want to mess around with routing that is handled quite nicely by Pedestal itself, so if an authentication flow
requires some extra routes to be added, those route should be pluged into the Pedestal routing system manually.

## Usage

Include the library in your leiningen project dependencies:

```clojure
[geheimtur "0.1.1"]
```

## Examples

You can find the sources of a demo application in [geheimtur-demo] [3] repository.

**The examples below does not duplicate information available as docstrings, if want to know all available options - check the docs in the code.**

### Securing a page

When you need to limit access to a specific page or a sub-tree of pages, you just add `guard` interceptor to the desired location.
You can adjust the interceptor behaviour using the following optional parameters:

- `:roles` - a set of roles that are allowed to access the page, if not defined users are required to be just authenticated
- `:silent?` - a flag to effect unauthorized/unauthenticated behavious. If set to `true` (default), users will be getting 404 Not Found error page, when they don't have enougth access rights
- `:unauthenticated-fn` - a handler of unauthenticated error state, it's a function that accepts a Pedestal context. The default implementation, throws an exception with `:unauthenticated` type
- `:unauthorized-fn` - a handler of unauthorized error state, it's a function that accepts a Pedestal context. The default implementation, throws an exception with `:unauthorized` type

In the example below, only administrators are allowed to access the pages under `/admin` path, the rest will be getting 404 responses (Note:
this is an illustration of `guard` usage and not a completely functional example.)

```clojure
(defroutes routes
  [[["/" {:get views/home-page}
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

### Enabling a flow

When a unauthenticated user or a user with missing access rigths reaches a page secured with `guard`, the `guard` will throw an exception that
can be handled either by `http-basic` or `interactive` interceptor that determines which flow is going to be triggered.

#### Http-Basic

You can enable http-basic authentication by putting `http-basic` interceptor before any of your guards.  It takes the following parameters:

- `realm` - a string that will be shown to a user when s/he is prompted to enter credentials
- `credential-fn` - a function that given username and password returns a corresponding identity

```clojure
(defroutes routes
  [[["/" {:get views/home-page ^:interceptors [(http-basic "Secure App" get-identity-from-db)]}
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

#### Form-based

You can use `interactive` interceptor to redirect users to the login page when they are requested to be authenticated by a guard.
At this moment, it accepts only one configuration option - `:login-uri`, by default users are redirected to `/login` page.

```clojure
(defroutes routes
  [[["/" {:get views/home-page ^:interceptors [(interactive {:login-uri "/users/login"})]
     ["/admin" {:get views/admin} ^:interceptors [(guard :roles #{:admin})]]]]])
```

After doing so, you just need to add handlers that render login page and authenticate users. Geheimtur comes with a default :POST handler
that can be user to authenticate users when you don't want to implement your own. `form-based` interceptor requires sessions to be enabled.

```clojure
(def login-post-handler
  (default-login-handler {:credential-fn credentials}))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor]
     ["/login" {:get views/login-page :post login-post-handler}]
     ["/logout" {:get default-logout-handler}]
     ["/interactive" {:get views/interactive-index} ^:interceptors [access-forbidden-interceptor (interactive {})]
      ["/restricted" {:get views/interactive-restricted} ^:interceptors [(guard :silent? false)]]]]]])
```

A complete example can be found [here] [3].

#### OAuth 2.0

You can use the same `interactive` inteceptor to redirect users to a page where they choose supported identity providers.
Geheimtur provides handlers for users redirection and callbacks out of the box, all you need to do - is to configure 
providers available for your users.

```clojure
(def providers
  {:github {:auth-url           "https://github.com/login/oauth/authorize"
            :client-id          (or (System/getenv "github.client_id") "client-id")
            :client-secret      (or (System/getenv "github.client_secret") "client-secret")
            :scope              "user:email"
            :token-url          "https://github.com/login/oauth/access_token"
            :user-info-url      "https://api.github.com/user"
            :user-info-parse-fn #(parse-string % true)}})

(def oath-handler
  (authenticate-handler providers))

(def oath-callback-handler
  (callback-handler providers))

(defroutes routes
  [[["/" {:get views/home-page}
     ^:interceptors [(body-params/body-params)
                     bootstrap/html-body
                     session-interceptor]
     ["/login" {:get views/login-page :post login-post-handler}]
     ["/logout" {:get default-logout-handler}]
     ["/oauth.login" {:get oath-handler}]
     ["/oauth.callback" {:get oath-callback-handler}]
     ["/interactive" {:get views/interactive-index} ^:interceptors [access-forbidden-interceptor (interactive {})]
      ["/restricted" {:get views/interactive-restricted} ^:interceptors [(guard :silent? false)]]]]]])
```

## License

Copyright © 2013 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://geheimtur.herokuapp.com
[2]: https://github.com/cemerick/friend
[3]: https://github.com/propan/geheimtur-demo