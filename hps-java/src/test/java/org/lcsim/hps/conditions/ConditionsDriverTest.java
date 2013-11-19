package org.lcsim.hps.conditions;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.lcsim.detector.tracker.silicon.HpsSiSensor;
import org.lcsim.event.EventHeader;
import org.lcsim.geometry.Detector;
import org.lcsim.util.Driver;
import org.lcsim.util.cache.FileCache;
import org.lcsim.util.loop.LCSimLoop;

/**
 * This class tests that {@link org.lcsim.hps.conditions.ConditionsDriver} works correctly.
 * @author Jeremy McCormick <jeremym@slac.stanford.edu>
 */
public class ConditionsDriverTest extends TestCase {
    
    /** This test file has a few events from the "good runs" of the Test Run. */
    private static final String TEST_FILE_URL = "http://www.lcsim.org/test/hps/conditions_test.slcio";
    
    /** Answer key for number of bad channels by run. */
    static Map<Integer,Integer> badChannelAnswerKey = new HashMap<Integer,Integer>();
    
    /** Setup the bad channel answer key by run. */
    static {
       badChannelAnswerKey.put(1351, 441);
       badChannelAnswerKey.put(1353, 473);
       badChannelAnswerKey.put(1354, 474);
       badChannelAnswerKey.put(1358, 344);
       badChannelAnswerKey.put(1359, 468);
       badChannelAnswerKey.put(1360, 468);
    }
    
    /** This is the number of bad channels in the QA set for all runs. */
    static int BAD_CHANNELS_QA_ANSWER = 50;

    /**
     * Run the test.
     * @throws Exception 
     */
    public void test() throws Exception {

        // Cache file locally from URL.
        FileCache cache = new FileCache();
        File testFile = cache.getCachedFile(new URL(TEST_FILE_URL));
        
        // Run the ConditionsDriver over test data containing multiple runs from the Test Run.
        LCSimLoop loop = new LCSimLoop();
        loop.setLCIORecordSource(testFile);
        loop.add(new ConditionsDriver());  
        loop.add(new SvtBadChannelChecker());
        loop.loop(-1, null);
    }
    
    /**
     * This Driver will check the number of bad channels for a run against the answer key.
     * @author Jeremy McCormick <jeremym@slac.stanford.edu>
     */
    class SvtBadChannelChecker extends Driver {
        
        int currentRun = Integer.MIN_VALUE;
        
        /**
         * This method will check the number of bad channels against the answer key
         * for the first event of a new run.
         */
        public void process(EventHeader event) {
            int run = event.getRunNumber();
            if (run != currentRun) {
                currentRun = run;
                Detector detector = event.getDetector();
                int badChannels = 0;
                List<HpsSiSensor> sensors = detector.getDetectorElement().findDescendants(HpsSiSensor.class);
                for (HpsSiSensor sensor : sensors) {
                    int nchannels = sensor.getNumberOfChannels();
                    for (int i=0; i<nchannels; i++) {
                        if (sensor.isBadChannel(i))
                            ++badChannels;
                    }
                }
                System.out.println("Run " + currentRun + " has " + badChannels + " SVT bad channels.");
                Integer badChannelAnswer = badChannelAnswerKey.get(run);
                if (badChannelAnswer != null) {
                    TestCase.assertEquals("Wrong number of bad channels found.", (int)badChannelAnswer, (int)badChannels);
                } else {
                    TestCase.assertEquals("Wrong number of bad channels found.", (int)BAD_CHANNELS_QA_ANSWER, (int)badChannels);
                }
            }
        }
    }
}