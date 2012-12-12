(ns cljsbuild.test.compiler
  (:use
   cljsbuild.compiler
   midje.sweet
   [midje.util :only [testable-privates]])
  (:require
    [cljs.closure :as cljs]
    [cljsbuild.util :as util]
    [clojure.java.io :as io]
    [fs.core :as fs]))

(def cljs-path "src-cljs")
(def crossover-path "crossovers")
(def crossover-macro-absolute "/a/b/crossovers/macros.clj")
(def crossover-macro-classpath "crossovers/macros.clj")
(def crossover-macro-paths [{:absolute crossover-macro-absolute
                             :classpath crossover-macro-classpath}])
(def output-to "output-to")
(def compiler-options
  {:output-to output-to
   :output-dir "output-dir"
   :optimizations :advanced
   :pretty-print false})
(def exclude-options "")
(def notify-command {:shell ["a" "b"] :test "c"})
(def assert? false)
(def incremental? true)
(def mtime 1234)
(testable-privates cljsbuild.compiler normalize-exclude-options)
(testable-privates cljsbuild.compiler to-be-excluded?)

(facts "Testing to-be-excluded? private function."
  (to-be-excluded? anything nil) => nil
  (to-be-excluded? nil anything) => nil
  (to-be-excluded? ["a/b.cljs"] "b.cljs") => false
  (to-be-excluded? ["a"] "a/b.cljs") => true
  (to-be-excluded? ["a"] "a/b/c.cljs") => true
  (to-be-excluded? ["a" "b/c"] "b/c/d.cljs") => true
  (to-be-excluded? ["."] "a/b/c.cljs") => true
  (to-be-excluded? [".."] "../a/b/c.cljs") => true
  (to-be-excluded? [".."] "a/b/c.cljs") => false
  (to-be-excluded? ["a"] "a/b/c") => true
  (to-be-excluded? ["a"] "b/c/d.cljs") => false
  )

(facts "Testing normalize-exclude-options private function."
  (normalize-exclude-options cljs-path nil) => nil
  (normalize-exclude-options cljs-path "") => nil
  (normalize-exclude-options cljs-path []) => nil
  (normalize-exclude-options cljs-path [""]) => ()
  (normalize-exclude-options cljs-path ["" " "]) => (throws Exception)
  (normalize-exclude-options cljs-path "file1") => (throws Exception)
  )

(fact "run-compiler calls cljs/build correctly"
  (run-compiler
    cljs-path
    crossover-path
    crossover-macro-paths
    compiler-options
    exclude-options
    notify-command
    incremental?
    assert?
    {}) => (just {"src-cljs/a.cljs" mtime,
                  "crossovers/b.cljs" mtime,
                  crossover-macro-absolute mtime})
  (provided
    (fs/exists? output-to) => false :times 1
    (util/find-files cljs-path #{"clj"}) => [] :times 1
    (util/find-files cljs-path #{"cljs"}) => ["src-cljs/a.cljs"] :times 1
    (util/find-files crossover-path #{"cljs"}) => ["crossovers/b.cljs"] :times 1
    (util/sh anything) => nil :times 1
    (fs/mod-time "src-cljs/a.cljs") => mtime :times 1
    (fs/mod-time "crossovers/b.cljs") => mtime :times 1
    (fs/mod-time crossover-macro-absolute) => mtime :times 1
    (fs/mkdirs anything) => nil
    (reload-clojure [crossover-macro-classpath] compiler-options notify-command) => nil :times 1))
