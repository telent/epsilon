# Epsilon

" an arbitrarily small positive quantity"

Some day, a mobile-friendly web interface for the notmuch mail client

*Today, a giant gaping security hole that does nothing useful and you 
should not run*

![](doc/search.png)

![](doc/thread.png)

Don't say I didn't warn you.  What, you're still reading?

## Building/installing for deployment

This is simplest if you have Nix

```
$ nix-env -f 'https://github.com/telent/epsilon/archive/master.tar.gz' -i
$ epsilon :port 8111
$ $preferred_web_browser http://localhost:8111
```

## Building for development

[ FIXME These instructions don't quite work as is becaue they lack the step
to hiccupise the svg icons.]

You will want a couple of windows or tabs or similar.  First ensure
you have set up [boot-clj](https://github.com/boot-clj/boot), then run

```
tab1$ boot watch cljs target
```

and separately

```
tab2$ boot use-target cider repl

epsilon.server=> (run {})
```

## Dependencies

Whenever you change `deps.edn` you also need to update `deps.json`.
Do this by running something like

```
$ CLJ_CONFIG=. CLJ_CACHE=. nix-shell -p clojure jre --run "clojure -R:build -C:build -Srepro  -Sforce  -m nixtract deps.json"
```


