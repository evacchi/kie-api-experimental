# Kie API Prototype

A "next-gen" Kie API, revolving around a unified unit-based interface.
See test cases and read the comments for implementation details and example usages.

This is a follow up to previous work on units. Consult the GDoc for details.

## Design

- extremely light-weight session creation
- highly-separated concerns (no "huge" classes or interface)
- require only what you need and nothing else
- type-safe API 

## User-experience

- unit-centric API: uniform instantiation of a "knowledge unit" (rules, processes, etc.), 
  and uniform run
