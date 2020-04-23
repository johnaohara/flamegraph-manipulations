# Flamegraph Manipulations

Collection of scripts to manipulate flamegraphs and collapsed stacks

## Prerequsists

 1 [sdkman](https://sdkman.io/install) 

 2 jbang - ` $ sdk install jbang`

## Running script
e.g. 

` $ jbang ./scripts/LambdaCollapse {PATH_TO_FOLDED_STACKS} > {PATH_TO_OUTPUT_FILE}`

## Scripts

 - LambdaCollapse - Lambda Classes that wrap lambda are dynamically named at runtime. For flamegraphs captured with frameworks rebuilding classloaders, any lambda's that are invoked are named differently for each classloader. When plotted in a flamegraphs, each different Lambda name is plotted in the stack and the call graph does not correctly display the full call stack.  This script inspects the stack frames, and when it finds a dynamically named Lambda, it replaces it with a frame name common to that call stack    
