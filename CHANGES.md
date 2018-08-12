# Changes

## v0.3.5

Starting with version 0.3.5 `authenticate-handler` and `callback-handler` of interactive OAuth interceptor accept either a map or a function that returns a map of OAuth providers' configuration.

## v0.3.0

Starting with version 0.3.0 the signatures of all credential/callback functions have changed. Now all the functions accept the request context as their first parameter and a map of parsed credentials (if they accepted them before).

To migrate to version 0.3.0 the following changes are needed:

### credentials-fn

The change is relevant to you if you are using Http-Basic or Form-Based flow. Change your `credentials-fn` as follows:

#### before

```clojure
(defn find-user-identity
  [username password]
  ..)
```

#### after

```clojure
(defn find-user-identity
  [context {:keys [username password]}]
  ..)
```

### on-success-handler

The change is relevant to you only if you are using OAuth2 flow. Change your `on-success-handler` as follows:

#### before

```clojure
(defn on-github-success
  [{:keys [identity return]}]
  ..)
```

#### after

```clojure
(defn on-github-success
  [context {:keys [identity return]}]
  ..)
```
