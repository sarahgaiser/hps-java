package org.hps.online.recon.commands;

import org.hps.online.recon.Command;

/**
 * Start a list of existing stations by their IDs or
 * attempt to start all stations if no IDs are provided.
 */
public class StartCommand extends Command {

    public StartCommand() {
        super("start", "Start a station that is inactive", "[IDs]",
                "Provide a list of IDs or none to start all inactive stations");
    }

    protected void process() {
        readStationIDs(cl);
    }
}
