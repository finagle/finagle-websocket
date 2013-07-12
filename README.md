## Warning: This is still Beta

## finagle-irc

Support for the IRC protocol in Finagle

This is still very much a major work in progress.

A basic client/server will read and write IRC messages to the wire, the
user is tasked with managing the protocol.

A simple server is provided. Currently it will accept connections and
allow users to join rooms, chat, and update the room's topic. There are
no security features and modes are currently unsupported.

## Using IRC

A server can be started that will default to listening on
localhost:6060. It's based on TwitterServer and command line flags are
provided for altering the listening port (see s/c/t/f/irc/server/Main
for more).

    sbt run
