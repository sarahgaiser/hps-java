package org.hps.recon.tracking.gbl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.hps.recon.tracking.MaterialSupervisor;
import org.hps.recon.tracking.MultipleScattering;
import org.hps.recon.tracking.TrackUtils;
import org.hps.record.StandardCuts;
import org.lcsim.detector.DetectorElementStore;
import org.lcsim.detector.IDetectorElement;
import org.lcsim.detector.identifier.IExpandedIdentifier;
import org.lcsim.detector.identifier.IIdentifier;
import org.lcsim.detector.identifier.IIdentifierDictionary;
import org.lcsim.detector.tracker.silicon.SiSensor;
import org.lcsim.event.EventHeader;
import org.lcsim.event.LCRelation;
import org.lcsim.event.RawTrackerHit;
import org.lcsim.event.RelationalTable;
import org.lcsim.event.Track;
import org.lcsim.event.base.BaseLCRelation;
import org.lcsim.geometry.Detector;
import org.lcsim.lcio.LCIOConstants;
import org.lcsim.util.Driver;

/**
 * A Driver which refits tracks using GBL. Does not require GBL collections to
 * be present in the event.
 */
public class GBLRefitterDriver extends Driver {

    private String inputCollectionName = "MatchedTracks";
    private String outputCollectionName = "GBLTracks";
    private String trackRelationCollectionName = "MatchedToGBLTrackRelations";
    private String helicalTrackHitRelationsCollectionName = "HelicalTrackHitRelations";
    private String rotatedHelicalTrackHitRelationsCollectionName = "RotatedHelicalTrackHitRelations";
    private String rawHitCollectionName = "SVTRawTrackerHits";
    

    private double bfield;
    private final MultipleScattering _scattering = new MultipleScattering(new MaterialSupervisor());
    private boolean storeTrackStates = false;
    private StandardCuts cuts = null;

    public void setStoreTrackStates(boolean input) {
        storeTrackStates = input;
    }

    public boolean getStoreTrackStates() {
        return storeTrackStates;
    }

    public void setInputCollectionName(String inputCollectionName) {
        this.inputCollectionName = inputCollectionName;
    }

    public void setOutputCollectionName(String outputCollectionName) {
        this.outputCollectionName = outputCollectionName;
    }
    
    public void setTrackRelationCollectionName(String trackRelationCollectionName) {
        this.trackRelationCollectionName = trackRelationCollectionName;
    }
    
    public void setHelicalTrackHitRelationsCollectionName(String helicalTrackHitRelationsCollectionName) {
        this.helicalTrackHitRelationsCollectionName = helicalTrackHitRelationsCollectionName;
    }

    public void setRotatedHelicalTrackHitRelationsCollectionName(String rotatedHelicalTrackHitRelationsCollectionName) {
        this.rotatedHelicalTrackHitRelationsCollectionName = rotatedHelicalTrackHitRelationsCollectionName;
    }
    
    public void setRawHitCollectionName(String rawHitCollectionName) {
        this.rawHitCollectionName = rawHitCollectionName;
    }
    
    public void setMaxTrackChisq(int nhits, double input) {
        if (cuts == null)
            cuts = new StandardCuts();
        cuts.setMaxTrackChisq(nhits, input);
    }

    public void setMaxTrackChisq(double input) {
        if (cuts == null)
            cuts = new StandardCuts();
        cuts.changeChisqTrackProb(input);
    }

    @Override
    protected void detectorChanged(Detector detector) {
        bfield = Math.abs(TrackUtils.getBField(detector).magnitude());
        _scattering.getMaterialManager().buildModel(detector);
        _scattering.setBField(bfield); // only absolute of B is needed as it's used for momentum calculation only

        if (cuts == null) {
            cuts = new StandardCuts();
            //System.out.printf("in constructor 5 %f 6 %f \n", cuts.getMaxTrackChisq(5), cuts.getMaxTrackChisq(6));
        }
    }

    @Override
    protected void process(EventHeader event) {
        //System.out.println(outputCollectionName + " Pass1");
        if(outputCollectionName == "GBLTracks_truth")
            System.out.println("New GBL");
        if (!event.hasCollection(Track.class, inputCollectionName))
            return;
        if(outputCollectionName == "GBLTracks_truth")
            System.out.println("Has MatchedTracks_truth");
        //System.out.println(outputCollectionName + " Pass2");

        setupSensors(event);
        List<Track> tracks = event.get(Track.class, inputCollectionName);
        //       System.out.println("GBLRefitterDriver::process number of tracks = "+tracks.size());
        RelationalTable hitToStrips = TrackUtils.getHitToStripsTable(event,helicalTrackHitRelationsCollectionName);
        RelationalTable hitToRotated = TrackUtils.getHitToRotatedTable(event,rotatedHelicalTrackHitRelationsCollectionName);

        List<Track> refittedTracks = new ArrayList<Track>();
        List<LCRelation> trackRelations = new ArrayList<LCRelation>();

        List<GBLKinkData> kinkDataCollection = new ArrayList<GBLKinkData>();
        List<LCRelation> kinkDataRelations = new ArrayList<LCRelation>();

        Map<Track, Track> inputToRefitted = new HashMap<Track, Track>();
        for (Track track : tracks) {
            if(outputCollectionName == "GBLTracks_truth")
                System.out.println("GBLRefitterDriver::process  number of hits on track = "+track.getTrackerHits().size());
            //System.out.println(outputCollectionName + " Pass3");
            //System.out.println(outputCollectionName + track + " " + hitToStrips + " " + hitToRotated + " " + TrackUtils.getStripHits(track, hitToStrips, hitToRotated).size());
            /*List<TrackerHit> hits = new ArrayList<TrackerHit>();
            for (TrackerHit hit : track.getTrackerHits()) {
                hits.addAll(hitToStrips.allFrom(hitToRotated.from(hit)));
            }
            System.out.println(outputCollectionName + " " + hits);*/
            if (TrackUtils.getStripHits(track, hitToStrips, hitToRotated).size() == 0){
                if(outputCollectionName == "GBLTracks_truth")
                    System.out.println("GBLRefitterDriver::process  did not find any strip hits on this track???");
                continue;
            }
            //System.out.println(outputCollectionName + " Pass4");
            if(outputCollectionName == "GBLTracks_truth")
                System.out.println("Track " + track);
            Pair<Track, GBLKinkData> newTrack = MakeGblTracks.refitTrack(TrackUtils.getHTF(track), TrackUtils.getStripHits(track, hitToStrips, hitToRotated), track.getTrackerHits(), 5, track.getType(), _scattering, bfield, storeTrackStates);
            if(outputCollectionName == "GBLTracks_truth")
                System.out.println("NewTrack " + newTrack);
            if (newTrack == null)
                continue;
            Track gblTrk = newTrack.getFirst();
            //System.out.println(outputCollectionName + " Pass5");
            if(outputCollectionName == "GBLTracks_truth")
                System.out.printf("gblTrkNDF %d  gblTrkChi2 %f  getMaxTrackChisq5 %f getMaxTrackChisq6 %f \n", gblTrk.getNDF(), gblTrk.getChi2(), cuts.getMaxTrackChisq(5), cuts.getMaxTrackChisq(6));
            if (gblTrk.getChi2() > cuts.getMaxTrackChisq(gblTrk.getTrackerHits().size()))
                continue;
            //System.out.println(outputCollectionName + " Pass6");
            if(outputCollectionName == "GBLTracks_truth")
                System.out.println("gblTrk " + gblTrk);
            refittedTracks.add(gblTrk);
            trackRelations.add(new BaseLCRelation(track, gblTrk));
            inputToRefitted.put(track, gblTrk);

            kinkDataCollection.add(newTrack.getSecond());
            kinkDataRelations.add(new BaseLCRelation(newTrack.getSecond(), gblTrk));
        }

        // Put the tracks back into the event and exit
        int flag = 1 << LCIOConstants.TRBIT_HITS;
        //System.out.println(outputCollectionName + " Pass7");
        if(outputCollectionName == "GBLTracks_truth")
            System.out.println(outputCollectionName+" "+refittedTracks);
        event.put(outputCollectionName, refittedTracks, Track.class, flag);
        event.put(trackRelationCollectionName, trackRelations, LCRelation.class, 0);
        event.put(GBLKinkData.DATA_COLLECTION, kinkDataCollection, GBLKinkData.class, 0);
        event.put(GBLKinkData.DATA_RELATION_COLLECTION, kinkDataRelations, LCRelation.class, 0);
    }

    private void setupSensors(EventHeader event) {
        List<RawTrackerHit> rawTrackerHits = null;
        if (event.hasCollection(RawTrackerHit.class, rawHitCollectionName))
            rawTrackerHits = event.get(RawTrackerHit.class, rawHitCollectionName);
        if (event.hasCollection(RawTrackerHit.class, "RawTrackerHitMaker_RawTrackerHits"))
            rawTrackerHits = event.get(RawTrackerHit.class, "RawTrackerHitMaker_RawTrackerHits");

        EventHeader.LCMetaData meta = event.getMetaData(rawTrackerHits);
        // Get the ID dictionary and field information.
        IIdentifierDictionary dict = meta.getIDDecoder().getSubdetector().getDetectorElement().getIdentifierHelper().getIdentifierDictionary();
        int fieldIdx = dict.getFieldIndex("side");
        int sideIdx = dict.getFieldIndex("strip");
        for (RawTrackerHit hit : rawTrackerHits) {
            // if sensor already has a DetectorElement, skip it
            if (hit.getDetectorElement() != null)
                continue;

            // The "side" and "strip" fields needs to be stripped from the ID for sensor lookup.
            IExpandedIdentifier expId = dict.unpack(hit.getIdentifier());
            expId.setValue(fieldIdx, 0);
            expId.setValue(sideIdx, 0);
            IIdentifier strippedId = dict.pack(expId);
            // Find the sensor DetectorElement.
            List<IDetectorElement> des = DetectorElementStore.getInstance().find(strippedId);
            if (des == null || des.size() == 0)
                throw new RuntimeException("Failed to find any DetectorElements with stripped ID <0x" + Long.toHexString(strippedId.getValue()) + ">.");
            else if (des.size() == 1)
                hit.setDetectorElement((SiSensor) des.get(0));
            else
                // Use first sensor found, which should work unless there are sensors with duplicate IDs.
                for (IDetectorElement de : des)
                    if (de instanceof SiSensor) {
                        hit.setDetectorElement((SiSensor) de);
                        break;
                    }
            // No sensor was found.
            if (hit.getDetectorElement() == null)
                throw new RuntimeException("No sensor was found for hit with stripped ID <0x" + Long.toHexString(strippedId.getValue()) + ">.");
        }
    }
}
