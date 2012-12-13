(defproject exclude "0.1.0-SNAPSHOT"
  :description "Simple project for testing build and exclude features of lein-cljsbuild"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :plugins [[lein-cljsbuild "0.2.9"]]
  :source-paths ["src/clj"]
  :cljsbuild {:builds
              {
               :exclude-nothing
               {:source-path "src/cljs"
                :compiler {:output-to "resources/public/js/exclude_nothing.js"
                           :optimizations :whitespace
                           :pretty-print true}}
               :exclude-file-only 
               {:source-path "src/cljs"
                :exclude "test_exclude/file1.cljs"
                :compiler {:output-to "resources/public/js/exclude_file_only.js"
                           :optimizations :whitespace
                           :pretty-print true}}
               :exclude-file-only-v
               {:source-path "src/cljs"            
                :exclude ["test_exclude/file1.cljs"]
                :compiler {:output-to "resources/public/js/exclude_file_only_v.js"
                           :optimizations :simple
                           :pretty-print true}}
               :exclude-dir-only
               {:source-path "src/cljs"            
                :exclude "test_exclude/dir1"
                :compiler {:output-to "resources/public/js/exclude_dir_only.js"
                           :optimizations :simple
                           :pretty-print true}}
               :exclude-dir-only-v
               {:source-path "src/cljs"            
                :exclude ["test_exclude/dir1"]
                :compiler {:output-to "resources/public/js/exclude_dir_only_v.js"
                           :optimizations :simple
                           :pretty-print true}}
               :exclude-sources
               {:source-path "src/cljs"            
                :exclude ["test_exclude/dir1" "test_exclude/file1.cljs"]
                :compiler {:output-to "resources/public/js/exclude_sources.js"
                           :optimizations :simple
                           :pretty-print true}}
               :exclude-all 
               ;;Gedankenexperiment, pretty unuseful in real world!
               {:source-path "src/cljs"            
                :exclude "."
                :compiler {:output-to "resources/public/js/exclude_all.js"
                           :optimizations :simple
                           :pretty-print true}}}})
