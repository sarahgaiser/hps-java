package org.hps.online.recon.eventbus;

import org.hps.job.JobManager;
import org.hps.record.LCSimEventBuilder;
import org.jlab.coda.jevio.EvioEvent;
import org.lcsim.event.EventHeader;

import com.google.common.eventbus.Subscribe;

/**
 * Receive EVIO events, build raw events,
 * and do recon processing.
 */
public class EvioListener {

    private OnlineEventBus eventbus;
    private LCSimEventBuilder builder;
    private JobManager mgr;

    EvioListener(OnlineEventBus eventbus) {
        this.eventbus = eventbus;
        this.mgr = eventbus.getStation().getJobManager();
        this.builder = eventbus.getStation().getEventBuilder();
    }

    /**
     * Receive an EVIO event, convert it to raw LCIO using the
     * event builder, and then process it using the job manager
     * to perform reconstruction. The LCIO event is then posted
     * to the event bus.
     *
     * @param evioEvent The input EVIO event
     */
    @Subscribe
    public void receiveEvioAndPostLcio(EvioEvent evioEvent) {
        try {
            /*eventbus.getLogger().info("Station " + eventbus.getStation().getStationName()
                    + " processing EVIO event: "
                    + EvioEventUtilities.getEventIdData(evioEvent)[0]);*/
            builder.readEvioEvent(evioEvent);
            EventHeader lcioEvent = builder.makeLCSimEvent(evioEvent);
            //eventbus.getLogger().info("Built LCIO event: " + lcioEvent.getEventNumber());
            mgr.processEvent(lcioEvent);
            eventbus.getLogger().info("Processed LCIO event: " + lcioEvent.getEventNumber());
            eventbus.post(lcioEvent);
        } catch (Exception e) {
            eventbus.post(new EventProcessingError(e, false));
        }
    }
}
