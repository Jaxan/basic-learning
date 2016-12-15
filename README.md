# Basic Learner Setup

This repo contains a basic learner setup. It makes use of learlib and automatalib ( http://learnlib.de/ ). A working, compiled version has been added to libs for convenience. 

BasicLearner is the most important class for external use. It contains some utility methods to quickly start learning. It also contains some learning and testing (equivalence checking) methods, including one which lets the user search for counterexamples. These can be used in the utility methods as parameters. It also contains some settings as simple static attributes; use them by simply changing their settings before starting a learning experiment. ExampleExperiment.java contains a main method demonstrating its use, and learns the included example SUL.

The project contains two SUL-interfaces: an example in Java-code, and a socket-wrapper which you can connect to your own SUL. Furthermore, it contains a simple observation tree used to check consistency of observations within an experiment (and give an error upon non-determinism).


## LearnLib version

This repo contains precompiled jars of the LearnLib library, which are more
recent than the ones in Maven (and include important bug fixes). Both LearnLib
and AutomataLib are compiled into a single jar. Compiled with Java 1.8.

* Learnlib version: [22 aug 2016](https://github.com/LearnLib/learnlib/commit/78417b85f771d0a2cf4498567a9190f03a5978d0)
* Automatalib version: [18 sept 2015](https://github.com/misberner/automatalib/commit/601e6fe105c3366b0706f8f2984873a67bf13e69)
