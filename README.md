# Epsilon

" an arbitrarily small positive quantity"

Aspires to be a mobile-friendly web interface for
the [notmuch mail client](https://notmuchmail.org/).  As of September
2018 it mostly works for my needs except that it does not provide any
message composition/sending interface, but *has not been tested on
anybody else and has not been audited for security*.

Don't say I didn't warn you.  What, you're still reading?

## UI

The default view is a "search" page that allows search by tags or any
other syntax that Notmuch understands

![](doc/search.png)

Clicking on a search result shows the messages in the thread.  Some
attempt is made to display HTML messages (the ability to turn this off
is forthcoming, I promise)

![](doc/thread.png)

Clicing on a tag in the thread page pops up a tag editor which allows
you to add/delete tags on the current message

![](doc/tags.png)


# Setup

See `INSTALL.md` for installation instructions.

## Security

By default Epsilon runs as a web server with the privileges of your
regular username (in principle you can change this by arranging to
start it as a less-privileged user, but you will still need to permit
it to read your mail, so ...)

### Use an SSH tunnel or HTTPS proxy

Epsilon is intended for mobile use so it's unlikely you really want to
access it only run it on the local machine - but obviously you should
not connect to it from any other device without considering the
security implications.

* Android ConnectBot has port forwarding.  Similar options exist for
  other mobile operating systems, I assume
* you can put an HTTPS proxy in front of it using stunnel or hitch or
  even nginx

### Protect against local attackers with a password

If there might be other users on your host machine (legitimate or
otherwise), there is nothing to stop them from connect to the port you
started epsilon on.  A rudimentary safeguard is to run

    notnuch config set epsilon.password my-secret-code

which will cause epsilon to prompt you for that code when you visit
it.  It passes whatever you type in plaintext, so see note above about
encryption - this is not a substitute for a secure transport layer.
Quite the opposite.


## Developing Epsilon

You will need to have installed:

* nodejs (icons are installed from an npm package)
* clojure 1.9 with the cli tools (`clojure` should be on your path)
* [boot-clj](http://boot-clj.com/)

(On Nix you can do this using `nix-shell`)

First, install and convert the icons - this will create about 260 cljs
files in `generated/epsilon/icons/`

```
$ npm install
$ clojure  -A:build -m hiccupize-icons node_modules/feather-icons/dist/icons/
```

(On Nix you can do this by running `eval "$makeIcons"` in the nix-shell environemnt)


Next, you will want a couple of windows or tabs or similar.  In the first one run

```
tab1$ boot watch cljs target
```

and in the second

```
tab2$ boot use-target cider repl

epsilon.server=> (run {})
```


### Dependencies

Whenever you change `deps.edn`, and assuming you care about building
the production build with Nix, you will also need to update
`deps.json`.  Do this by running something like

```
$ CLJ_CONFIG=. CLJ_CACHE=. clojure -A:build -A:nixtract -Srepro  -Sforce  -m nixtract deps.json
```
