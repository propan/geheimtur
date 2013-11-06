# Geheimtür

a Clojure library that allows you to secure your Pedestal applications with minimum efforts.

## Motivation

I do know that there is a great [friend] [1] library out there, but I had some problems making it work with a Pedestal
application and do that the way I wanted, so I decided to implement something that does (hopefully) some good work securing
Pedestal applications as easily (hopefully) as [friend] [1] does with Ring applications.

Also, I didn't want to mess around with routing that is handled quite nicely by Pedestal itself, so if an authentication flow
requires some extra routes to be added, those route should be pluged into the Pedestal routing system manually.

## Usage

Include the library in your leiningen project dependencies:

```clojure
[geheimtur "0.1.0"]
```

## Examples

-TODO

## License

Copyright © 2013 Pavel Prokopenko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: https://github.com/cemerick/friend