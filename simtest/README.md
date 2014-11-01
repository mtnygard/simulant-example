# Example Simulant Test

This module has a complete test harness built with
[Simulant](https://github.com/Datomic/simulant). It simulates a
shopper interacting with an ecommerce system. The parts are designed
to be easy to learn.

You can interact with the simulation via a command-line interface or
a REPL.

This depends on Datomic Free. With a minor change to project.clj, it
will work with Datomic Pro as well.

# Introducing the Parts

## Basic schema

Some one-time setup is needed. `simtest.database` has a simple
migrations framework to install the schema. The schema definitions are
in `resources/simulant/schema.edn` and `resources/simtest.edn`

## The Model

The model is expressed in simtest.model. There are three parts to the
model itself:

1. State transitions, represented as `shopper-transitions`. This
   function returns a sparse Markov matrix that we use to create a
   random walk through the commerce system.
1. Control parameters that adjust various probabilities. These are
   read from a model entity in the database. They change the Markov
   transition probabilities. They also affect how heavily traffic will
   focus on "hot" items and categories.
1. Category and item data. In a full system, this would either be
   extracted from the target system or pushed into it at simulation
   start. We've got a data generator that spoofs up a pile of
   identifiers. Just load `repl.clj` in a REPL or via `lein run -m
   repl`.

## Generator

## Execution/Capture

## Validation


## Usage

FIXME

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.