package org.hps.conditions.ecal;

import static org.hps.conditions.ConditionsTableConstants.ECAL_BAD_CHANNELS;
import static org.hps.conditions.ConditionsTableConstants.ECAL_CALIBRATIONS;
import static org.hps.conditions.ConditionsTableConstants.ECAL_CHANNELS;
import static org.hps.conditions.ConditionsTableConstants.ECAL_GAINS;

import java.util.Map.Entry;

import org.hps.conditions.ConditionsObjectFactory;
import org.hps.conditions.DatabaseConditionsConverter;
import org.lcsim.conditions.ConditionsManager;

/**
 * This class loads all ecal conditions into an {@link EcalConditions} object
 * from the database, based on the current run number known by the conditions manager.
 * @author Jeremy McCormick <jeremym@slac.stanford.edu>
 */
public class EcalConditionsConverter extends DatabaseConditionsConverter<EcalConditions> {
    
    public EcalConditionsConverter(ConditionsObjectFactory objectFactory) {
        super(objectFactory);
    }
    
    /**
     * Create ECAL conditions object containing all data for the current run.
     */
    public EcalConditions getData(ConditionsManager manager, String name) {
        
        // Create new, empty conditions object to fill with data.
        EcalConditions conditions = new EcalConditions();
                               
        // Get the channel information from the database.                
        EcalChannelMap channelMap = manager.getCachedConditions(EcalChannelMap.class, ECAL_CHANNELS).getCachedData();
        
        // Set the channel map.
        conditions.setChannelMap(channelMap);
                                       
        // Add gains.
        EcalGainCollection gains = manager.getCachedConditions(EcalGainCollection.class, ECAL_GAINS).getCachedData();        
        for (EcalGain gain : gains.getObjects()) {
            EcalChannel channel = channelMap.get(gain.getChannelId());
            conditions.getChannelConstants(channel).setGain(gain);
        }
        
        // Add bad channels.
        EcalBadChannelCollection badChannels = manager.getCachedConditions(
                EcalBadChannelCollection.class, ECAL_BAD_CHANNELS).getCachedData();
        for (EcalBadChannel badChannel : badChannels.getObjects()) {
            EcalChannel channel = channelMap.get(badChannel.getChannelId());
            conditions.getChannelConstants(channel).setBadChannel(true);
        }
        
        // Add calibrations including pedestal and noise values.
        EcalCalibrationCollection calibrations = 
                manager.getCachedConditions(EcalCalibrationCollection.class, ECAL_CALIBRATIONS).getCachedData();
        for (EcalCalibration calibration : calibrations.getObjects()) {
            EcalChannel channel = channelMap.get(calibration.getChannelId());
            conditions.getChannelConstants(channel).setCalibration(calibration);
        }       
        
        // Return the conditions object to caller.
        return conditions;
    }

    /**
     * Get the type handled by this converter.
     * @return The type handled by this converter.
     */
    public Class<EcalConditions> getType() {
        return EcalConditions.class;
    }
    
    
}
