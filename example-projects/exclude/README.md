# test-exclude

This is a simple project to provide a quick way to perform some tests on the exclude feature of cljsbuild. For more details
about leiningen project for ClojureScript and its features (and more indeed) see the online tutorial 
[modern-cljs] (https://github.com/magomimmo/modern-cljs.git)  

## Running the test

Install `lein-cljsbuild` and `cljsbuild` running from the `lein-cljsbuild` project home

```bash	
$ cd support
$ lein install	
```

and

```bash
$ cd plugin
$ lein install
```

then perform the test just executing the script in the home directory of the project `exclude`, that is from the `lein-cljsbuild` home folder

```bash
$ cd example-projects/exclude
$ lein cljsbuild once
```

The generated JavaScript files are imported in the html files, open them to check the result of the exclusion. 

Warning: after you have installed the new `lein-cljsbuild` and `cljsbuild`, reverting to previous can be made only 
deleting the respective folders in leiningen repository (tipically `~/.m2/repository`) and rerunning `lein cljsbuild`. 

## License

Copyright © 2012 

Distributed under the Eclipse Public License, the same as Clojure.
