(ns cljsbuild.compiler
  (:use
   [clj-stacktrace.repl :only [pst+]]
   [clojure.java.io :only [resource]])
  (:require
   [cljs.closure :as closure]
   [cljs.compiler :as compiler]
   [cljsbuild.util :as util]
   [cljs.analyzer :as analyzer]
   [clojure.string :as string]
   [fs.core :as fs]))

(def reset-color "\u001b[0m")
(def foreground-red "\u001b[31m")
(def foreground-green "\u001b[32m")

(defn- colorizer [c]
  (fn [& args]
    (str c (apply str args) reset-color)))

(def red (colorizer foreground-red))
(def green (colorizer foreground-green))

(defn- elapsed [started-at]
  (let [elapsed-us (- (. System (nanoTime)) started-at)]
    (with-precision 2
      (str (/ (double elapsed-us) 1000000000) " seconds"))))

(defn- notify-cljs [command message colorizer]
  (when (seq (:shell command))
    (try
      (util/sh (update-in command [:shell] (fn [old] (concat old [message]))))
      (catch Throwable e
        (println (red "Error running :notify-command:"))
        (pst+ e))))
  (println (colorizer message)))

(defn- normalize-exclude-options
  ; Normalize the exclude options to a sequence of file pathnames. It throws an exception if not existent
  ; files or directories are found in the exclude option.
  [cljs-path exclude-src]
  (when (and (not-empty exclude-src) (not-empty cljs-path))
    (let [cljs-canonical-path (str (fs/normalized-path cljs-path) java.io.File/separator)
          normalized-exclude-src (flatten (vector exclude-src))
          filtered-exclude-src (filter #(not-empty %) normalized-exclude-src)]
      (flatten (map (fn [s]
                      (when (not (fs/exists? (str cljs-canonical-path s)))
                        (throw (Exception. (str "Trying to exclude not existing file or directory: \"" s "\""))))
                      (str cljs-canonical-path s))
                    filtered-exclude-src)))))

(defn- to-be-excluded?
  ; Assert wether the specified by file-name file is contained in
  ; scr-coll, which is the collection of dirs and files that must be excluded.
  ; "scr-coll" must be either a sequence of strings or nil.
  [src-coll file-path]
  (when (and src-coll file-path)
    (let [regex (map #(re-pattern (str "^"  % "|^"  % "/.*")) src-coll)]
      (reduce #(or %1 %2) (map #(string? (re-matches % file-path)) regex)))))

(defn- build
  ; Given a source which can be compiled, produce runnable JavaScript. 
  ; Straightforward adaptation of cljs.closure/build
  [cljs-path options exclude-options]
  (analyzer/reset-namespaces!)
  (let [compilables (compiler/cljs-files-in (fs/file cljs-path))
        normalized-exclude-options (normalize-exclude-options cljs-path exclude-options)
        to-be-compiled (filter #(not (to-be-excluded? normalized-exclude-options (fs/absolute-path %))) compilables)
        options (if (= :nodejs (:target options))
               (merge {:optimizations :simple} options)
               options)
        ups-deps (closure/get-upstream-deps)
        all-options (assoc options
                      :ups-libs (:libs ups-deps)
                      :ups-foreign-libs (:foreign-libs ups-deps)
                      :ups-externs (:externs ups-deps))]
    (binding [analyzer/*cljs-static-fns*
              (or (and (= (options :optimizations) :advanced))
                  (:static-fns options)
                  analyzer/*cljs-static-fns*)
              analyzer/*cljs-warn-on-undeclared*
              (true? (options :warnings))]
      (let [compiled (map #(closure/-compile % all-options) to-be-compiled)
            js-sources (concat
                        (apply closure/add-dependencies all-options
                               (concat (if (coll? compiled) compiled [compiled])
                                       (when (= :nodejs (:target all-options))
                                         [(closure/-compile (resource "cljs/nodejs.cljs") all-options)])))
                        (when (= :nodejs (:target all-options))
                          [(closure/-compile (resource "cljs/nodejscli.cljs") all-options)]))
            optim (:optimizations all-options)]
        (if (and optim (not= optim :none))
          (->> js-sources
               (apply closure/optimize all-options)
               (closure/add-header all-options)
               (closure/add-wrapper all-options)
               (closure/output-one-file all-options))
          (apply closure/output-unoptimized all-options js-sources))))))

(defn- compile-cljs [cljs-path compiler-options exclude notify-command incremental? assert?]
  (let [output-file (:output-to compiler-options)
        output-file-dir (fs/parent output-file)]
    (println (str "Compiling \"" output-file "\" from \"" cljs-path "\"..."))
    (flush)
    (when (not incremental?)
      (fs/delete-dir (:output-dir compiler-options)))
    (when output-file-dir
      (fs/mkdirs output-file-dir))
    (let [started-at (System/nanoTime)]
      (try
        (binding [*assert* assert?]
          (build cljs-path compiler-options exclude))
        (notify-cljs
          notify-command
          (str "Successfully compiled \"" output-file "\" in " (elapsed started-at) ".") green)
        (catch Throwable e
          (notify-cljs
            notify-command
            (str "Compiling \"" output-file "\" failed.") red)
          (pst+ e))))))

(defn- get-mtimes [paths]
  (into {}
    (map (fn [path] [path (fs/mod-time path)]) paths)))

(defn- list-modified [output-mtime dependency-mtimes]
  (reduce (fn [modified [path mtime]]
            (if (< output-mtime mtime)
              (conj modified path)
              modified))
          []
          dependency-mtimes))

(defn- drop-extension [path]
  (let [i (.lastIndexOf path ".")]
    (if (pos? i)
      (subs path 0 i)
      path)))

(defn- relativize [parent path]
  (let [path (fs/absolute-path path)
        parent (fs/absolute-path parent)]
    (if (.startsWith path parent)
      (subs path (count parent))
      path)))

(defn reload-clojure [paths compiler-options notify-command]
  ; Incremental builds will use cached JS output unless one of the cljs input files
  ; has been modified.  Since reloading a clj file *might* affect the build, but does
  ; not affect any cljs file mtimes, we have to clear the cache here to force everything
  ; to be rebuilt.
  (fs/delete-dir (:output-dir compiler-options))
  (doseq [path paths]
    (try
      (load (drop-extension path))
      (catch Throwable e
        (notify-cljs
          notify-command
          (str "Reloading Clojure file \"" path "\" failed.") red)
        (pst+ e)))))

(defn run-compiler [cljs-path crossover-path crossover-macro-paths
                    compiler-options exclude notify-command incremental?
                    assert? last-dependency-mtimes]
  (let [output-file (:output-to compiler-options)
        output-mtime (if (fs/exists? output-file) (fs/mod-time output-file) 0)
        macro-files (map :absolute crossover-macro-paths)
        macro-classpath-files (into {} (map vector macro-files (map :classpath crossover-macro-paths)))
        clj-files (util/find-files cljs-path #{"clj"})
        cljs-files (mapcat #(util/find-files % #{"cljs"}) [cljs-path crossover-path])
        macro-mtimes (get-mtimes macro-files)
        clj-mtimes (get-mtimes clj-files)
        cljs-mtimes (get-mtimes cljs-files)
        dependency-mtimes (merge macro-mtimes clj-mtimes cljs-mtimes)]
    (when (not= last-dependency-mtimes dependency-mtimes)
      (let [macro-modified (list-modified output-mtime macro-mtimes)
            clj-modified (list-modified output-mtime clj-mtimes)
            cljs-modified (list-modified output-mtime cljs-mtimes)]
        (when (seq macro-modified)
          (reload-clojure (map macro-classpath-files macro-modified) compiler-options notify-command))
        (when (seq clj-modified)
          (reload-clojure (map (partial relativize cljs-path) clj-files) compiler-options notify-command))
        (when (or (seq macro-modified) (seq clj-modified) (seq cljs-modified))
          (compile-cljs cljs-path compiler-options exclude notify-command incremental? assert?))))
    dependency-mtimes))
