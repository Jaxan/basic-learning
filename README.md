# Basic Learner Setup

This repo contains a basic learner setup. It makes use of learlib and automatalib ( http://learnlib.de/ ). A working, compiled version has been added to libs for convenience. 

Main.java is the entry point, and the first part of the file contains all (hard-coded) settings needed to do a basic learning experiment which can be adjusted. From OO-programming perspective, the set-up isn't the best, but it should be simple to use and understand. 

It contains two SUL-interfaces: an example in Java-code, and a socket-wrapper which you can connect to your own SUL. Furthermore, it contains a simple observation tree used to check consistency of observations within an experiment (and give an error upon non-determinism).


## LearnLib version

This repo contains precompiled jars of the LearnLib library, which are more
recent than the ones in Maven (and include important bug fixes). The jars are
compiled with java version 1.8.

* Learnlib version: [18 sept 2015](https://github.com/LearnLib/learnlib/commit/883c04307deb93b1ab975b455027c76cc3d712a7)
* Automatalib version: [18 sept 2015](https://github.com/misberner/automatalib/commit/601e6fe105c3366b0706f8f2984873a67bf13e69)
