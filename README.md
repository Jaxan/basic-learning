# Basic Learner Setup

This repo contains a basic learner setup. It makes use of learlib and automatalib ( http://learnlib.de/ ). A working, compiled version has been added to libs for convenience. 

Main.java is the entry point, and the first part of the file contains all (hard-coded) settings needed to do a basic learning experiment which can be adjusted. From OO-programming perspective, the set-up isn't the best, but it should be simple to use and understand. 

It contains two SUL-interfaces: an example in Java-code, and a socket-wrapper which you can connect to your own SUL. Furthermore, it contains a simple observation tree used to check consistency of observations within an experiment (and give an error upon non-determinism).
