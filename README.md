# Flamegraph Manipulations

Collection of scripts to manipulate flamegraphs and collapsed stacks

## Prerequsists

 1 [sdkman](https://sdkman.io/install) 

 2 jbang - ` $ sdk install jbang`


## Scripts

 - **LambdaNormalize** - Lambdas are wrapped by dynamically named classes at runtime. For stack profiles captured for code that creates new ClassLoaders, invoked lambdas are named differently for each ClassLoader. When plotted in a flamegraphs, each different Lambda name is plotted separately in the call stack and the flamegraph not correctly represent the actual stacktrace.  This script inspects the stack frames, and when it finds a dynamically named Lambda, it normalizes it to a frame name common to a particular call stack    

    Usage: 
    ``` 
    $ java --agentpath:/path/to/libasyncProfiler.so=start,file=/path/to/profile.folded -jar /path/to/jar
    $ jbang -p ./scripts/LambdaNormalize /path/to/profile.folded > /path/to/profile.folded.lambda
    $ /path/to/FlameGraph/flamegraph.pl /path/to/profile.folded.lambda > /path/to/profile.folded.svg
    ```
