# Flamegraph Manipulations

Collection of scripts to manipulate flamegraphs and collapsed stacks

## Prerequsists

 1 [sdkman](https://sdkman.io/install) 

 2 jbang - ` $ sdk install jbang`


## Scripts

 - LambdaCollapse - Lambda Classes that wrap lambdas are dynamically named at runtime. For stacks captured with frameworks creting new classloaders while being profiled, any lambda's that are invoked are named differently for each classloader. When plotted in a flamegraphs, each different Lambda name is plotted in the stack and the call graph does not correctly display the full call stack.  This script inspects the stack frames, and when it finds a dynamically named Lambda, it replaces it with a frame name common to that call stack    

    Usage: 
    ``` 
    $ java --agentpath:/path/to/libasyncProfiler.so=start,file=/path/to/profile.folded -jar /path/to/jar
    $ jbang ./scripts/LambdaCollapse /path/to/profile.folded > /path/to/profile.folded.lambda
    $ /path/to/FlameGraph/flamegraph.pl /path/to/profile.folded.lambda > /path/to/profile.folded.svg
    ```
