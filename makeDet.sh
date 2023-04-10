DET=$1

java -cp distribution/target/hps-distribution-5.2-SNAPSHOT-bin.jar org.hps.detector.DetectorConverter -f lcdd -i detector-data/detectors/${DET}/compact.xml -o detector-data/detectors/${DET}/${DET}.lcdd
