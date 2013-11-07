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
[geheimtur "0.1.0"]
```

## Examples

You can find the sources of a demo application in [geheimtur-demo] [3] repository.

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

## License

Copyright © 2013 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://geheimtur.herokuapp.com
[2]: https://github.com/cemerick/friend
[3]: https://github.com/propan/geheimtur-demo