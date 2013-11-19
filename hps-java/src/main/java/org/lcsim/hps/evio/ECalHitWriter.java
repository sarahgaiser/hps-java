package org.lcsim.hps.evio;

import org.lcsim.lcio.LCIOConstants;
import org.jlab.coda.jevio.BaseStructure;
import org.lcsim.event.RawTrackerHit;
import org.jlab.coda.jevio.CompositeData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.coda.jevio.DataType;
import org.jlab.coda.jevio.EventBuilder;
import org.jlab.coda.jevio.EvioBank;
import org.jlab.coda.jevio.EvioException;
import org.lcsim.event.EventHeader;
import org.lcsim.event.RawCalorimeterHit;
import org.lcsim.geometry.IDDecoder;
import org.lcsim.hps.recon.ecal.EcalConditions;

import static org.lcsim.hps.evio.EventConstants.ECAL_BANK_NUMBER;
import static org.lcsim.hps.evio.EventConstants.ECAL_BOTTOM_BANK_TAG;
import static org.lcsim.hps.evio.EventConstants.ECAL_PULSE_INTEGRAL_FORMAT;
import static org.lcsim.hps.evio.EventConstants.ECAL_PULSE_INTEGRAL_BANK_TAG;
import static org.lcsim.hps.evio.EventConstants.ECAL_TOP_BANK_TAG;

/**
 *
 * @author Sho Uemura <meeg@slac.stanford.edu>
 * @version $Id: ECalHitWriter.java,v 1.6 2013/04/18 20:59:16 meeg Exp $
 */
public class ECalHitWriter implements HitWriter {

    private String hitCollectionName = "EcalReadoutHits";
    private int mode = EventConstants.ECAL_PULSE_INTEGRAL_MODE;

    public ECalHitWriter() {
    }

    public void setHitCollectionName(String hitCollectionName) {
        this.hitCollectionName = hitCollectionName;
    }

    public void setMode(int mode) {
        this.mode = mode;
        if (mode != EventConstants.ECAL_WINDOW_MODE && mode != EventConstants.ECAL_PULSE_MODE && mode != EventConstants.ECAL_PULSE_INTEGRAL_MODE) {
            throw new IllegalArgumentException("invalid mode " + mode);
        }
    }

    @Override
    public boolean hasData(EventHeader event) {
        switch (mode) {
            case EventConstants.ECAL_WINDOW_MODE:
                return event.hasCollection(RawTrackerHit.class, hitCollectionName);
            case EventConstants.ECAL_PULSE_MODE:
                return event.hasCollection(RawTrackerHit.class, hitCollectionName);
            case EventConstants.ECAL_PULSE_INTEGRAL_MODE:
                return event.hasCollection(RawCalorimeterHit.class, hitCollectionName);
            default:
                return false;
        }
    }

    @Override
    public void writeData(EventHeader event, EventBuilder builder) {
        List<Object> hits = new ArrayList<Object>();
        switch (mode) {
            case EventConstants.ECAL_WINDOW_MODE:
                hits.addAll(event.get(RawTrackerHit.class, hitCollectionName));
                writeHits(hits, builder, mode);
                break;
            case EventConstants.ECAL_PULSE_MODE:
                hits.addAll(event.get(RawTrackerHit.class, hitCollectionName));
                writeHits(hits, builder, mode);
                break;
            case EventConstants.ECAL_PULSE_INTEGRAL_MODE:
                hits.addAll(event.get(RawCalorimeterHit.class, hitCollectionName));
                writeHits(hits, builder, mode);
                break;
            default:
                break;
        }
    }

    private void writeHits(List<Object> rawCalorimeterHits, EventBuilder builder, int mode) {
        System.out.println("Writing " + rawCalorimeterHits.size() + " ECal hits in integral format");

        // Make two lists containing the hits from top and bottom sections, which go into separate EVIO data banks.
        List<Object> topHits = new ArrayList<Object>();
        List<Object> bottomHits = new ArrayList<Object>();
        for (Object hit : rawCalorimeterHits) {
            Long daqID = EcalConditions.physicalToDaqID(getCellID(hit));
            int crate = EcalConditions.getCrate(daqID);
            if (crate == ECAL_BOTTOM_BANK_TAG) {
                bottomHits.add(hit);
            } else {
                topHits.add(hit);
            }
        }

        // Make a new bank for this crate.
        BaseStructure topBank = null;
        BaseStructure botBank = null;
        // Do these banks already exist?
        for (BaseStructure bank : builder.getEvent().getChildren()) {
            switch (bank.getHeader().getTag()) {
                case ECAL_TOP_BANK_TAG:
                    topBank = bank;
                    break;
                case ECAL_BOTTOM_BANK_TAG:
                    botBank = bank;
                    break;
            }
        }
        // If they don't exist, make them.
        if (topBank == null) {
            topBank = new EvioBank(ECAL_TOP_BANK_TAG, DataType.BANK, ECAL_BANK_NUMBER);
            try {
                builder.addChild(builder.getEvent(), topBank);
            } catch (EvioException e) {
                throw new RuntimeException(e);
            }
        }
        if (botBank == null) {
            botBank = new EvioBank(ECAL_BOTTOM_BANK_TAG, DataType.BANK, ECAL_BANK_NUMBER);
            try {
                builder.addChild(builder.getEvent(), botBank);
            } catch (EvioException e) {
                throw new RuntimeException(e);
            }
        }

        switch (mode) {
            case EventConstants.ECAL_WINDOW_MODE:
                // Write the two collections for top and bottom hits to separate EVIO banks.
                writeWindowHitCollection(topHits, topBank, builder);
                writeWindowHitCollection(bottomHits, botBank, builder);
                break;
            case EventConstants.ECAL_PULSE_MODE:
                // Write the two collections for top and bottom hits to separate EVIO banks.
                writePulseHitCollection(topHits, topBank, builder);
                writePulseHitCollection(bottomHits, botBank, builder);
                break;
            case EventConstants.ECAL_PULSE_INTEGRAL_MODE:
                // Write the two collections for top and bottom hits to separate EVIO banks.
                writeIntegralHitCollection(topHits, topBank, builder);
                writeIntegralHitCollection(bottomHits, botBank, builder);
                break;
            default:
                break;
        }
    }

    private long getCellID(Object hit) {
        if (RawCalorimeterHit.class.isInstance(hit)) {
            return ((RawCalorimeterHit) hit).getCellID();
        } else if (RawTrackerHit.class.isInstance(hit)) {
            return ((RawTrackerHit) hit).getCellID();
        }
        return 0;
    }

    private void writeIntegralHitCollection(List<Object> hits, BaseStructure crateBank, EventBuilder builder) {
        if (hits.isEmpty()) {
            return;
        }

        // Get the ID decoder.
        IDDecoder dec = EcalConditions.getSubdetector().getIDDecoder();

        // Make a hit map; allow for multiple hits in a crystal.
        Map<Long, List<RawCalorimeterHit>> hitMap = new HashMap<Long, List<RawCalorimeterHit>>();
        for (Object thing : hits) {
            RawCalorimeterHit hit = (RawCalorimeterHit) thing;
            if (hitMap.get(hit.getCellID()) == null) {
                hitMap.put(hit.getCellID(), new ArrayList<RawCalorimeterHit>());
            }
            List<RawCalorimeterHit> channelHits = hitMap.get(hit.getCellID());
            channelHits.add(hit);
        }

        // Make map of slot number to hit IDs.
        Map<Integer, List<Long>> slotMap = new HashMap<Integer, List<Long>>();
        for (Long id : hitMap.keySet()) {
            dec.setID(id);
//			System.out.println(dec.getIDDescription());
//			System.out.printf("ix = %d, iy = %d\n", dec.getValue("ix"), dec.getValue("iy"));
            Long daqID = EcalConditions.physicalToDaqID(id);
//			System.out.printf("physicalID %d, daqID %d\n", id, daqID);
            int slot = EcalConditions.getSlot(daqID);
            if (slotMap.get(slot) == null) {
                slotMap.put(slot, new ArrayList<Long>());
            }
            List<Long> slots = slotMap.get(slot);
            slots.add(id);
        }

        // Create composite data for this slot and its channels.
        CompositeData.Data data = new CompositeData.Data();

        // Loop over the slots in the map.
        for (int slot : slotMap.keySet()) {
            data.addUchar((byte) slot); // slot #
            data.addUint(0); // trigger #
            data.addUlong(0); // timestamp    		
            List<Long> hitIDs = slotMap.get(slot);
            int nhits = hitIDs.size();
            data.addN(nhits); // number of channels
            for (Long id : hitIDs) {
                dec.setID(id);
                int channel = EcalConditions.getChannel(EcalConditions.physicalToDaqID(id));
                data.addUchar((byte) channel); // channel #
                List<RawCalorimeterHit> channelHits = hitMap.get(id);
                data.addN(channelHits.size()); // number of pulses
                for (RawCalorimeterHit hit : channelHits) {
                    data.addUshort((short) hit.getTimeStamp()); // pulse time
                    data.addUint((int) hit.getAmplitude()); // pulse integral
                }
            }
        }
        // New bank for this slot.
        EvioBank cdataBank = new EvioBank(EventConstants.ECAL_PULSE_BANK_TAG, DataType.COMPOSITE, 0);
        List<CompositeData> cdataList = new ArrayList<CompositeData>();

        // Add CompositeData to bank.
        try {
            CompositeData cdata = new CompositeData(ECAL_PULSE_INTEGRAL_FORMAT, 1, data, ECAL_PULSE_INTEGRAL_BANK_TAG, 0);
            cdataList.add(cdata);
            cdataBank.appendCompositeData(cdataList.toArray(new CompositeData[cdataList.size()]));
            builder.addChild(crateBank, cdataBank);
        } catch (EvioException e) {
            throw new RuntimeException(e);
        }
    }

    private void writePulseHitCollection(List<Object> hits, BaseStructure crateBank, EventBuilder builder) {
        if (hits.isEmpty()) {
            return;
        }

        // Get the ID decoder.
        IDDecoder dec = EcalConditions.getSubdetector().getIDDecoder();

        // Make a hit map; allow for multiple hits in a crystal.
        Map<Long, List<RawTrackerHit>> hitMap = new HashMap<Long, List<RawTrackerHit>>();
        for (Object thing : hits) {
            RawTrackerHit hit = (RawTrackerHit) thing;
            if (hitMap.get(hit.getCellID()) == null) {
                hitMap.put(hit.getCellID(), new ArrayList<RawTrackerHit>());
            }
            List<RawTrackerHit> channelHits = hitMap.get(hit.getCellID());
            channelHits.add(hit);
        }

        // Make map of slot number to hit IDs.
        Map<Integer, List<Long>> slotMap = new HashMap<Integer, List<Long>>();
        for (Long id : hitMap.keySet()) {
            dec.setID(id);
//			System.out.println(dec.getIDDescription());
//			System.out.printf("ix = %d, iy = %d\n", dec.getValue("ix"), dec.getValue("iy"));
            Long daqID = EcalConditions.physicalToDaqID(id);
//			System.out.printf("physicalID %d, daqID %d\n", id, daqID);
            int slot = EcalConditions.getSlot(daqID);
            if (slotMap.get(slot) == null) {
                slotMap.put(slot, new ArrayList<Long>());
            }
            List<Long> slots = slotMap.get(slot);
            slots.add(id);
        }

        // Create composite data for this slot and its channels.
        CompositeData.Data data = new CompositeData.Data();

        // Loop over the slots in the map.
        for (int slot : slotMap.keySet()) {
            data.addUchar((byte) slot); // slot #
            data.addUint(0); // trigger #
            data.addUlong(0); // timestamp    		
            List<Long> hitIDs = slotMap.get(slot);
            int nhits = hitIDs.size();
            data.addN(nhits); // number of channels
            for (Long id : hitIDs) {
                dec.setID(id);
                int channel = EcalConditions.getChannel(EcalConditions.physicalToDaqID(id));
                data.addUchar((byte) channel); // channel #
                List<RawTrackerHit> channelHits = hitMap.get(id);
                data.addN(channelHits.size()); // number of pulses
                for (RawTrackerHit hit : channelHits) {
                    data.addUchar((byte) channelHits.indexOf(hit)); // pulse number
                    data.addN(hit.getADCValues().length); // number of samples
                    for (short val : hit.getADCValues()) {
                        data.addUshort(val); // sample
                    }
                }
            }
        }

        // New bank for this slot.
        EvioBank cdataBank = new EvioBank(EventConstants.ECAL_PULSE_BANK_TAG, DataType.COMPOSITE, 0);
        List<CompositeData> cdataList = new ArrayList<CompositeData>();

        // Add CompositeData to bank.
        try {
            CompositeData cdata = new CompositeData(EventConstants.ECAL_PULSE_FORMAT, 1, data, EventConstants.ECAL_PULSE_BANK_TAG, 0);
            cdataList.add(cdata);
            cdataBank.appendCompositeData(cdataList.toArray(new CompositeData[cdataList.size()]));
            builder.addChild(crateBank, cdataBank);
        } catch (EvioException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeWindowHitCollection(List<Object> hits, BaseStructure crateBank, EventBuilder builder) {
        if (hits.isEmpty()) {
            return;
        }

        // Get the ID decoder.
        IDDecoder dec = EcalConditions.getSubdetector().getIDDecoder();

        // Make a hit map; allow for multiple hits in a crystal.
        Map<Long, RawTrackerHit> hitMap = new HashMap<Long, RawTrackerHit>();
        for (Object thing : hits) {
            RawTrackerHit hit = (RawTrackerHit) thing;
            hitMap.put(hit.getCellID(), hit);
        }

        // Make map of slot number to hit IDs.
        Map<Integer, List<Long>> slotMap = new HashMap<Integer, List<Long>>();
        for (Long id : hitMap.keySet()) {
            dec.setID(id);
//			System.out.println(dec.getIDDescription());
//			System.out.printf("ix = %d, iy = %d\n", dec.getValue("ix"), dec.getValue("iy"));
            Long daqID = EcalConditions.physicalToDaqID(id);
//			System.out.printf("physicalID %d, daqID %d\n", id, daqID);
            int slot = EcalConditions.getSlot(daqID);
            if (slotMap.get(slot) == null) {
                slotMap.put(slot, new ArrayList<Long>());
            }
            List<Long> slots = slotMap.get(slot);
            slots.add(id);
        }

        // Create composite data for this slot and its channels.
        CompositeData.Data data = new CompositeData.Data();

        // Loop over the slots in the map.
        for (int slot : slotMap.keySet()) {

            // New bank for this slot.
//            EvioBank slotBank = new EvioBank(EventConstants.ECAL_WINDOW_BANK_TAG, DataType.COMPOSITE, slot);

            data.addUchar((byte) slot); // slot #
            data.addUint(0); // trigger #
            data.addUlong(0); // timestamp    		
            List<Long> hitIDs = slotMap.get(slot);
            int nhits = hitIDs.size();
            data.addN(nhits); // number of channels
            for (Long id : hitIDs) {
                dec.setID(id);
                int channel = EcalConditions.getChannel(EcalConditions.physicalToDaqID(id));
                data.addUchar((byte) channel); // channel #
                RawTrackerHit hit = hitMap.get(id);
                data.addN(hit.getADCValues().length); // number of samples
                for (short val : hit.getADCValues()) {
                    data.addUshort(val); // sample
                }
            }
        }

        // New bank for this slot.
        EvioBank cdataBank = new EvioBank(EventConstants.ECAL_WINDOW_BANK_TAG, DataType.COMPOSITE, 0);
        List<CompositeData> cdataList = new ArrayList<CompositeData>();

        // Add CompositeData to bank.
        try {
            CompositeData cdata = new CompositeData(EventConstants.ECAL_WINDOW_FORMAT, 1, data, EventConstants.ECAL_WINDOW_BANK_TAG, 0);
            cdataList.add(cdata);
            cdataBank.appendCompositeData(cdataList.toArray(new CompositeData[cdataList.size()]));
            builder.addChild(crateBank, cdataBank);
        } catch (EvioException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeData(EventHeader event, EventHeader toEvent) {
        String readoutName = EcalConditions.getSubdetector().getReadout().getName();
        switch (mode) {
            case EventConstants.ECAL_WINDOW_MODE:
            case EventConstants.ECAL_PULSE_MODE:
                List<RawTrackerHit> rawTrackerHits = event.get(RawTrackerHit.class, hitCollectionName);
                System.out.println("Writing " + rawTrackerHits.size() + " ECal hits");
                toEvent.put(hitCollectionName, rawTrackerHits, RawTrackerHit.class, 0, readoutName);
                break;
            case EventConstants.ECAL_PULSE_INTEGRAL_MODE:
                List<RawCalorimeterHit> rawCalorimeterHits = event.get(RawCalorimeterHit.class, hitCollectionName);
                System.out.println("Writing " + rawCalorimeterHits.size() + " ECal hits in integral format");
                int flags = 0;
                flags += 1 << LCIOConstants.RCHBIT_TIME; //store cell ID
                toEvent.put(hitCollectionName, rawCalorimeterHits, RawCalorimeterHit.class, flags, readoutName);
                break;
            default:
                break;
        }
    }
}
