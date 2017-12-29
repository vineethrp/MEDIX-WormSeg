#!/bin/bash

rm -f WormSeg.jar
rm -f com/kylelmoy/WormSeg/*.class
javac com/kylelmoy/WormSeg/*.java
jar cvfm WormSeg.jar manifest.txt com/kylelmoy/WormSeg/*.class
