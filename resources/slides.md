<h1 style="border: none; font-size: 96px">
Portal
</h1>
<h2 style="border: none; font-size: 32px">
Exploring new Workflows with Visual Tools
</h2>

---

# Who AM I

- Chris Badahdah
- [@djblue](https://github.com/djblue/) on the internet
- Software Development for ~10 years
  - Lots of Java and Javascript
  - Mostly Web Application
- Grew appreciation for Lisp / Scheme via SICP
  - Found Clojure!
  - Immutable by default
- Doing Clojure Professionally for last ~5 years
  - Working on Portal for 4.5 years

---

# Goals

- Brief intro of Portal
- Not an in-depth UI tutorial
- Showcase of various workflows
- Motivate people to try and explore new workflows

---

# What is Portal?

- Data visualization tool
  - Data structure focused
- Great place to send `tap>` values
  - Added in Clojure `1.10`
- Portal is like stdout for `tap>`
  - Focus on data over string
- Web App with Ring server + Reagent client
  - Multi-platform (jvm, node, clr)

---

# Basic `tap>` Workflow

```clojure
(require '[portal.api :as p])
(p/open) ;; Open a new inspector
```

```clojure
(add-tap #'p/submit) ;; Add portal as a tap> target
```

```clojure
(tap> :hello/world) ;; Start tapping out values
(tap> (read-string (slurp "deps.edn")))
```

```clojure
(remove-tap #'p/submit) ;; Remove portal from tap> targetset
```

### Pros

- Minimal effort setup
- Reasonable default behavior
- Start learning how Portal works
  - Viewers, Commands, History
- Points the way to what's possible

### Cons

- Accumulates all values until clear
- Least common denominator

---

# Default Viewers

How do I configure default viewers?

### Challenges

- How can a user specify this mapping?
  - pure data?
  - code?
    - where does the code run?
    - user's runtime?

### Portal's approach

- Start with the information model
- `:portal.viewer/default` in metadata
  - Not every value supports metadata
  - `portal.viewer` makes this easier

---

# Default Viewers - Part 2

- Leverage custom submit function
  - Arbitrary dispatch
  - **Arbitrary transformations!**

```clojure
(require '[portal.viewer :as v])

(def defaults
  [[string? v/text] [bytes? v/bin] [any? v/tree]])

(defn- get-viewer-f [value]
  (some (fn [[predicate viewer]] (when (predicate value) viewer)) defaults))
```

```clojure
(require '[portal.api :as p])
(p/open)

(defn submit [value]
  (let [f (get-viewer-f value)]
    (p/submit (f value))))

(add-tap #'submit)
```

```clojure
(tap> "hello, world")
(tap> (byte-array [0 1 2 3]))
(tap> (range 10))
```

- Not obvious but very powerful
- Can be tricky for those unfamiliar with metadata
- This is why `portal.api/tap` is deprecated

---

# Datafy 

> return a representation of o as data (default identity)

How do I turn on `clojure.datafy/datafy` by default?

```clojure
(require '[clojure.datafy :as d])
(require '[portal.api :as p])

(p/open)
(def submit (comp p/submit d/datafy))
(add-tap #'submit)
```

```clojure
(tap> (find-ns 'clojure.core))
```

```clojure
(tap> java.lang.Long)
```

- Opt-in instead of opt-out
- Apply recursively via `clojure.walk`

---

# Async Workflow

Printing / tapping promises by default isn't very useful

```clojure
(require '[portal.api :as p])
(p/open)

(defn promise? [x]
  (and
   (instance? clojure.lang.IDeref x)
   (instance? clojure.lang.IPending x)))

(defn async-submit [value]
  (p/submit
   (if-not (promise? value)
     value
     (deref value 5000 :timeout))))

(add-tap #'async-submit)
```

```clojure
(def p (promise))
(tap> p)
```

```clojure
(deliver p :delivered)
```

- Reduces cognitive load during debugging
- Equally applicable to JavaScript promises
  - Great for node based lambda environments
  - Beats `#object [Promise ...]`

---

# Nav

> Returns (possibly transformed) v in the context of coll and k (a key/index or
> nil). Callers should attempt to provide the key/index context k for
> Indexed/Associative/ILookup colls if possible, but not to fabricate one e.g. for
> sequences (pass nil). nav returns the value of clojure.core.protocols/nav.

```clojure
(require '[clojure.datafy :refer [nav]])
(nav {:hello :world} :hello :world)
```

> As of Clojure 1.10, protocols can optionally elect to be extended via per-value metadata

```clojure
(require '[portal.api :as p])
(p/inspect
 (with-meta
   {:hello :world}
   {'clojure.core.protocols/nav (fn [_ _ _] (rand))}))
```

```clojure
(require '[examples.hacker-news :as hn])
(p/inspect hn/stories)
```

---

# Test Report Workflow

- Leverage the `:portal.viewer/test-report` for visualizing test results
- Easily compare values

```clojure
(require '[portal.api :as p]
         '[clojure.test :refer [deftest is run-test]])

(deftest example-test
  (is (= 1 1))
  (is (= 1 2))
  (is (= (vec (range 10))
         (vec (rest (range 11))))))
```

```clojure
(add-tap #'p/submit)
(p/open)
```

```clojure
(let [out (atom [])]
  (binding [clojure.test/report #(swap! out conj %)]
    (run-test example-test))
  (tap> @out))
```

---

# `portal.nrepl/middleware` Workflow

Using `portal.nrepl`, we can capture more context:

- Capture more context
  - Source / Runtime info
  - Timing, Stdio, Test assertions and Exceptions

```clojure
(reset! portal.workflows/nrepl true)
(require '[portal.api :as p])
(add-tap #'p/submit)
(p/open)
```

```clojure
(range 10)
```

```clojure
(Thread/sleep (long (* 1000 (rand))))
```

```clojure
(println "hello *out*")
(binding [*out* *err*] (println "hello *err*"))
```

```clojure
(clojure.test/run-test slide-11/example-test)
```

```clojure
(/ 1 0)
```

---

# `p/submit` Explored

```clojure
(defonce tap-list (atom []))

(defn submit [value]
  (swap! tap-list conj value))

(add-tap #'submit)
```

```clojure
(tap> :world)
```

```clojure
(require '[portal.api :as p])
(p/inspect tap-list)
```

```clojure
(tap> :hello)
```

- Portal actually only cares about data + atoms
- You can `p/inspect` multiple values simultaneously

```clojure
(p/inspect (read-string (slurp "deps.edn")))
```

---

# Corfield  Workflow

```clojure
(require '[portal.api :as p])

(defonce logs (atom []))

(defn submit [value]
  (cond
    (= :portal.viewer/log
       (:portal.viewer/default (meta value)))
    (swap! logs conj value)

    (:portal.nrepl/eval (meta value))
    (swap! logs conj value)

    :else (p/submit value)))

(add-tap #'submit)
```

```clojure
(p/open {:window-title "taps" :theme :portal.colors/zerodark})
(p/inspect logs {:window-title "logs" :theme :portal.colors/material-ui})
```

Logs go to bottom Portal instance

```clojure
(require '[portal.console :as console])
(console/log (range 10))
```

Taps go to top Portal

```clojure
(reset! portal.workflows/nrepl true)
(tap> (+ 1 2 3))
```

---

# Emmy Workflow

```clojure
(require '[emmy.portal :refer [start!]])

(start!
 {:emmy.portal/tex
  {:macros {"\\f" "#1f(#2)"}}
  :theme :portal.colors/zenburn})  
```

```clojure
(require '[emmy.mathlive :as ml])
(tap> (ml/mathfield {:default-value "1+x"}))
```

```clojure
(require '[emmy.env :as e :refer :all])
(require '[emmy.mafs :as mafs])
(require '[emmy.viewer :as ev])
(tap>
 (mafs/mafs-meta
  (ev/with-let [!phase [0 0]]
    (let [shifted (ev/with-params {:atom !phase :params [0]}
                    (fn [shift]
                      (((cube D) tanh) (- identity shift))))]
      (mafs/mafs
       (mafs/cartesian)
       (mafs/of-x shifted)
       (mafs/inequality
        {:y {:<= shifted :> cos} :color :blue})
       (mafs/movable-point {:atom !phase :constrain "horizontal"}))))))
```

- Extensive use of npm dependencies
  - Targets Reagent via ClojureScript eval
- Initially designed for other tools

[https://github.com/mentat-collective/emmy-viewers](https://github.com/mentat-collective/emmy-viewers)

---

# Multi-Runtime Workflow

- Funnel taps from multiple runtimes into a single instance of Portal

```clojure
(require '[portal.api :as p])
(p/start {:port 4444})
(p/open)
```

```clojure
(require '[portal.client.jvm :as c])

(def submit
  (partial c/submit {:port 4444
                     ;; default encoding if none specified
                     ;; edn | json | transit
                     :encoding :edn}))

(add-tap  #'submit)
(tap> (range 10))
```

- Clients implemented for multiple runtimes
  - jvm, web, node, bb, nbb, planck, clr, dart
- Data must be serializable via the provided encodings
- `portal.shadow.remote/hook` for automatic shadow-cljs wiring
- Can also be used in deployed applications

---

# My Current Workflow

```clojure
(require '[portal :as p])
(p/open-taps)
```

```clojure
(require '[model.entities :as e]) ;; Pull in data access namespace
(def message-log (e/get-entities :message-log {})) ;; get entities from db
(tap> message-log)
```

```clojure
(meta (first message-log))
```

```clojure
(portal.workflows/source `p/submit)
```

- Same data models live in db, server and client

---

# Snapshot Workflow

Reify behavior of system as an edn snapshot

```clojure
(require '[clojure.data :refer [diff]])
(require '[model.entities :as e])
(require '[portal.api :as p])
(require '[portal.viewer :as v])
(let [[a b] (diff (e/dump-db "2d143ad6") (e/dump-db "f8b741f9"))]
  (p/inspect (v/diff [a b])))
```

Pros:

- Trivial to assert if snapshots are `=` 
- Trivial to update snapshot as you evolve your system
- Can grow over time to cover more aspects of your system
- Can be as simple as serializing a database instance
- Snapshot should be proportional to code change

Cons:

- Can be difficult to deterministically generate snapshot
  - random-uuid
  - java.util.Date
- Can be difficult to understand snapshot changes

---

# Benchmarking Workflow

Visualize performance differences

```clojure
(require '[portal.api :as p])
(p/open)
(add-tap p/submit)
```

```clojure
(require '[portal.runtime.bench-cson :as cson])

(def data (cson/run-benchmark))
(tap> :done)
```

```clojure
(tap> (cson/charts data))
(tap> (cson/table data))
(tap> data)
```

---

# Viewer Extensibility

```clojure
(require '[portal.api :as p])
(p/open {:main 'tetris.core/app})
```

- One of my first CLJS projects
- SCI (Small Clojure Interpreter)
  - Fast enough
  - Supports large subset of ClojureScript
  - `portal.api/repl` to start cljs REPL
  - Experimenting with bootstrap-cljs as another UI runtime options

[https://github.com/djblue/tetris](https://github.com/djblue/tetris)

---

# Documentation

```clojure
(require '[portal.api :as p])
(p/docs)
```

- Augments static docs
  - [https://cljdoc.org/d/djblue/portal/](https://cljdoc.org/d/djblue/portal/)
- Could become a generic facility for interactive docs
- Contributing guides is always appreciated!
- **Thanks Clojurists Together!**

---

# Current Issues

- Third-party viewers can be difficult to share
  - Who resolves npm dependencies?
  - Who loads code for viewers?
- No smart serialization around infinite values
- Some types in ClojureScript are wonky
  - Longs
  - Dates
- Lazy values can break internals

---

# Future Ideas

- Simplify extensibility
  - Ease packaging and distribution
- Explore / enable /document more workflow
- User defined keyboard shortcuts
  - Mostly done, needs polish and docs
- Embed Portal viewers into Cursive inline evaluation similar to Calva notebooks

---

# Conclusion

- Everyone works differently
- You know your data best
- Visual tools can be leveraged to fit into many workflows
  - Discoverability is an issue
  - We can learn from each other in the community
- Customization comes in many forms
- The more you datafy your workflow, the more your can get out of visual tools 

---

# Thanks!

<div style="display: flex; align-items: center; height: 400px">
<ul style="padding: 40px; font-size: 1.6em">
<li>
<a href="https://github.com/djblue/portal-workflows">https://github.com/djblue/portal-workflows</a>
</li>
<li>
<a href="https://github.com/djblue/portal">https://github.com/djblue/portal</a>
</li>
<li>
<a href="https://clojurians.slack.com/channels/portal">#portal</a> on clojurians slack
</li>
<ul>
</div>