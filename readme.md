# Overview

In the spirit of `babashka.http-server` and Python's `http.server`, `file-server` is HTTP server for serving and uploading files.

## Objectives

- support workflows for downloading files from the host machine
- support workflows for uploading files to the host machine
- support transfers of large files
- support HTTP `range` requests

## History

`file-server` started as a clone of [http-server](https://github.com/lamesjinden/http-server) (a wrapper around `babashka.http-server`) with the goal of adding upload support.

During development, `file-server` failed to handle sending and receiving of larger files. Further investigation revealed that the underlying HTTP server, `http-kit` (included in `babashka`), [buffers all requests and responses in memory before processing](https://github.com/http-kit/http-kit/issues/90). This specific limitation could not reasonably be worked-around, so another HTTP server was required. `file-server` was converted to use `ring-jetty-adapter`, the 'standard' Clojure HTTP back-end. But, this comes with its own limitations. Specifically, `file-server` could no longer be executed via `Babashka`, thus sacrificing fast startup times.

As a pure Clojure/JVM project, `file-server` could take advantage of a wider array of supporting libraries. Instead of hand-rolling mulit-part parsing of HTTP requests, `file-server` uses `ring.middleware.multipart-params`. And, while it didn't work out, for a brief moment `ring-range-middleware` was used to handle Http Range requests. That is, the HTTP Range handling remains a copy of `babashka.http-server` as `ring-range-middleware` failed to support scrubbing of large video assets.

## Usage

### CLI Arguments

- _--help_: Displays CLI details
- _--host_: The hostname to listen on (defaults to 0.0.0.0)
- _--port_: The port to listen on (defaults to 8000)
- _--headers_: Map of headers to include in _all_ server responses, specified as `edn` (defaults to `{}`)
- _--directory_: The directory to serve (defaults to the current working directory)
- _--verbose_: Enable verbose logging; supports multiple instances for increased verbosity

### Execution

* _pre-req_: `java` is on the `$PATH` (tested on JDK 21)
* _pre-req_: access to the build artifact, `file-server.jar` (naming not exact; default builds currently generate `file-server-0.1.0-SNAPSHOT.jar`)

From a terminal, execute the following:

```bash
java -jar file-server.jar
```

Alternatively, to serve from a different host and port, adn for a different directory, and with verbose logging, execute the following:

```bash
java -jar --host localhost --port 8080 --directory /tmp -v -v -v
```

## Development

`file-server` is a Clojure `deps`-based project. Additionally, development scripts are specified via `bb.edn` tasks. To invoke the development scripts, `babashka` must be installed prior.

### Build

To build an artifact of `file-server`:

```
bb build
```

### Run (from source)

To run `file-server` directly from source:

```
bb run-file-server
```

### Start a REPL

To start a project REPL:

```
bb run-dev-file-server-repl
# once running, load the dev-server ns to start file-server with CORS enabled
```
