OUTPUTNAME=$1
GEO=$2
Nevents=1

FILE_1=/sdf/group/hps/mc/4pt55GeV/fee/idealCond/fee_recon_20um120nA_1.slcio
RUN_N=14552

java -DdisableDesign -DdisableSvtAlignmentConstants -Djna.library.path="/Users/pbutti/sw/GeneralBrokenLines/cpp/build/lib/" -XX:+UseSerialGC -Xmx3000m -jar distribution/target/hps-distribution-5.2-SNAPSHOT-bin.jar \
geoPrint.lcsim \
-i ${FILE_1} \
-DoutputFile=$OUTPUTNAME.slcio -d $GEO -R $RUN_N -n $Nevents -e 500
