# YaCy Grid Component: Parser

The YaCy Grid is the second-generation implementation of YaCy, a peer-to-peer search engine.
A YaCy Grid installation consists of a set of micro-services which communicate with each other
using the MCP, see https://github.com/yacy/yacy_grid_mcp

## Purpose

The Parser is a microservices which can be deployed i.e. using Docker. When the Parser Component
is started, it searches for a MCP and connects to it. By default the local host is searched for a
MCP but you can configure one yourself.

## What it does

The Parser is able to read a WARC file and parses it's content. The content is analyzed,
the plain text, links, images and more entities are extracted. The result is stored in a JSON Object.
Calling the parser will generate a list of JSON Objects, each containing the analyzed content
of one internet resource.
The parser understands not only HTML but also a wide range of different document formats, including PDF,
all OpenOffice and MS Office document formats and much more.


## Installation: Download, Build, Run
At this time, yacy_grid_parser is not provided in compiled form, you easily build it yourself. It's not difficult and done in one minute! The source code is hosted at https://github.com/yacy/yacy_grid_parser, you can download it and run loklak with:

    > git clone --recursive https://github.com/yacy/yacy_grid_parser.git
    > cd yacy_grid_parser
    > gradle run
    
This repository uses git submodules to integrate yacy_grid_mcp into yacy_grid_parser. In case that you clones this repository without the `--recursive` do now:

    > git submodule update --init --recursive

The submodules require, that each subsequent

    > git pull origin master
    
requires also a pull for the submodules, in case anything has changed there. You can do that easily with:

    > git submodule foreach git pull origin master


## Example for Parsing a set of Documents

For this example, a hosted version of yacy_grid_parser is provided at http://yacygrid.com:8500.
The example shows, how a web site is crawled using wget, then parsed with yacy_grid_parser and finally indexed with yacy_search_server ('legacy' YaCy/1.x) using the surrogate dump reading method:

First, crawl a site (here:publicplan.de):

    > wget -r -l3 "https://www.publicplan.de/" --warc-file="publicplan.de"
    
This produces the file "publicplan.de.warc.gz". That file can then be send to the hosted yacy_grid_parser with:

    > curl -X POST -F "sourcebytes=@publicplan.de.warc.gz" -F "flatfile=true" -o "publicplan.de.flatjson" http://yacygrid.com:8500/yacy/grid/parser/parser.json

The result is a file "publicplan.de.flatjson" with 774 single JSON objects, each printed in it's own line in the flatjson file.
To index that file with legacy YaCy (YaCy/1.x) just copy it into the yacy_search_server/DATA/SURROGATES/in/ path.

## Contribute

This is a community project and your contribution is welcome!

1. Check for [open issues](https://github.com/yacy/yacy_grid_parser/issues)
   or open a fresh one to start a discussion around a feature idea or a bug.
2. Fork [the repository](https://github.com/yacy/yacy_grid_parser.git)
   on GitHub to start making your changes (branch off of the master branch).
3. Write a test that shows the bug was fixed or the feature works as expected.
4. Send a pull request and bug us on Gitter until it gets merged and published. :)


## What is the software license?
LGPL 2.1

Have fun!

@0rb1t3r
