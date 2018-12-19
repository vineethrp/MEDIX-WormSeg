# MEDIX-WormSeg
Creates a distributed system for parallelizing feature extraction from C. elegans observational video

Computational Methods for Tracking, Quantitative Assessment, and Visualization of C. elegans Locomotory Behavior
Moy K, Li W, Tran HP, Simonis V, Story E, et al. (2015) Computational Methods for Tracking, Quantitative Assessment, and Visualization of C. elegans Locomotory Behavior. PLoS ONE 10(12): e0145870. doi: 10.1371/journal.pone.0145870
http://journals.plos.org/plosone/article?id=10.1371/journal.pone.0145870


## Build Instructions

Use the create_jar.sh in WormSegmenter/src/
Example:
```
vineeth@andvin:~/WS/IIT/fastworm/MEDIX-WormSeg/WormSegmenter/src$ ./create_jar.sh 
added manifest
adding: com/kylelmoy/WormSeg/FeatureExtractor.class(in = 7653) (out= 4364)(deflated 42%)
adding: com/kylelmoy/WormSeg/FeatureExtractor$ConnectedComponentAnalysis.class(in = 2346) (out= 1386)(deflated 40%)
adding: com/kylelmoy/WormSeg/FeatureExtractor$SegmentationFailureException.class(in = 387) (out= 254)(deflated 34%)
adding: com/kylelmoy/WormSeg/Networking.class(in = 12046) (out= 6473)(deflated 46%)
adding: com/kylelmoy/WormSeg/Networking$Node.class(in = 2419) (out= 1366)(deflated 43%)
adding: com/kylelmoy/WormSeg/Networking$NodeHandler$1.class(in = 804) (out= 453)(deflated 43%)
adding: com/kylelmoy/WormSeg/Networking$NodeHandler.class(in = 4429) (out= 2363)(deflated 46%)
```

The WormSeg.jar will be created in the WormSegmenter/src directory. This is
a self contained jar file and you can copy it to any folder to run it later.

## Running

You need two instances of the program to run. One is the worker instance which
waits for requests from the tasker and does the actual segmentation. One is the
tasker instance which the user interface. It collects all the required
parameters from the command line and then passes it over to the worker through
a socket connection.

Starting the Worker
```
$ java -Xmx8g -jar WormSeg.jar -mode worker -threads 12
```

The above options are self explanatory.

Starting the tasker:
```
$ java -Xmx8g -jar WormSeg.jar -mode tasker -input ./che2_HR_nf7/input/ -output output.txt \
      -frames 10 -width 1280 -height 960 -extension .jpeg -search_win_size 1200 \
      -area_min 1000 -area_max 1500
```

Again all the options are self explanatory:


There is another mode called terminate. That is just a mode to send a message
to the worker instance to shut down.
```
$ java -Xmx8g -jar WormSeg.jar -mode terminate
```

You wouldn't really need to do this to terminate the worker, you can just
Ctrl+C the worker instance to shut it down.

## Documentation
For detailed explanation of all the options for worker and tasker, you can
refer to this documentation:
https://github.com/vineethrp/MEDIX-WormSeg/blob/master/WormSegmenter/documentation/WormSeg%20Documentation.pdf

