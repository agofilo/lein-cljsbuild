(defproject lein-cljsbuild "0.2.9"
  :description "ClojureScript Autobuilder Plugin"
  :url "http://github.com/emezeske/lein-cljsbuild"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo}
  :dependencies [[fs "1.1.2"]]
;;; For leiningen2 uncomment this part, comment the :dev-dependencies part and add lein-midje to your .lein/profiles
;;; :profiles {:dev {:dependencies [[midje "1.4.0"]]}}
  :dev-dependencies [[midje "1.4.0"]
                     ; NOTE: lein-midje requires different versions to be
                     ; installed for lein1 vs lein2 compatibility :(.
                     [lein-midje "1.0.10"]]
  :eval-in-leiningen true)
