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
the plain text, links, images and more entities are extracted. The result is stored in a JSON Object
Calling the parser will generate a list of JSON Objects, each containing the analyzed content
of one internet resource.
The parser understands not only HTML but also a wide range of different document formats, including PDF,
all OpenOffice and MS Office document formats and much more.


## Installation: Download, Build, Run
At this time, yacy_grid_parser is not provided in compiled form, you easily build it yourself. It's not difficult and done in one minute! The source code is hosted at https://github.com/yacy/yacy_grid_parser, you can download it and run loklak with:

    > git clone https://github.com/yacy/yacy_grid_parser.git
    > git submodule foreach git pull origin master
    > cd yacy_grid_parser
    > gradle run

Please read also https://github.com/yacy/yacy_grid_mcp/README.md for further details.

## What is the software license?
LGPL 2.1

Have fun!

@0rb1t3r
