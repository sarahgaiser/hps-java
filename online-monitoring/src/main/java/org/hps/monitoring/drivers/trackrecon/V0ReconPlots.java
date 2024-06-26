package org.hps.monitoring.drivers.trackrecon;

import static org.hps.monitoring.drivers.trackrecon.PlotAndFitUtilities.plot;
import hep.aida.IAnalysisFactory;
import hep.aida.IFitFactory;
import hep.aida.IFunctionFactory;
import hep.aida.IHistogram1D;
import hep.aida.IHistogram2D;
import hep.aida.IPlotter;
import hep.aida.IPlotterFactory;
import hep.aida.ITree;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hps.record.triggerbank.AbstractIntData;
import org.hps.record.triggerbank.TSData2019;
import org.lcsim.event.EventHeader;
import org.lcsim.event.GenericObject;
import org.lcsim.event.ReconstructedParticle;
import org.lcsim.event.Track;
import org.lcsim.event.Vertex;
import org.lcsim.geometry.Detector;
import org.lcsim.util.Driver;
import org.lcsim.util.aida.AIDA;

public class V0ReconPlots extends Driver {

    private AIDA aida = AIDA.defaultInstance();
    private static ITree tree = null;
    String finalStateParticlesColName = "FinalStateParticles";
    String unconstrainedV0CandidatesColName = "UnconstrainedV0Candidates";
    String beamConV0CandidatesColName = "BeamspotConstrainedV0Candidates";
    String targetConV0CandidatesColName = "TargetConstrainedV0Candidates";
    // some counters
    int nRecoEvents = 0;
    boolean debug = false;

    IPlotter plotterUncon;
    IPlotter plotter2d;
    String outputPlots;

    IPlotterFactory plotterFactory;
    IFunctionFactory functionFactory;
    IFitFactory fitFactory;

    IHistogram1D nV0;
    IHistogram1D unconMass;
    IHistogram1D unconVx;
    IHistogram1D unconVy;
    IHistogram1D unconVz;
    IHistogram1D unconChi2;

    IHistogram2D pEleVspPos;
    IHistogram2D pyEleVspyPos;
    IHistogram2D pxEleVspxPos;
    IHistogram2D massVsVtxZ;
    private boolean removeRandomEvents = true;

    public void setRemoveRandomEvents(boolean doit) {
        this.removeRandomEvents = doit;
    }

    public void setFinalStateParticlesColName(String name) {
        this.finalStateParticlesColName = name;
    }

    public void setUnconstrainedV0CandidatesColName(String name) {
        this.unconstrainedV0CandidatesColName = name;
    }

    public void setBeamConV0CandidatesColName(String name) {
        this.beamConV0CandidatesColName = name;
    }

    public void setTargetConV0CandidatesColName(String name) {
        this.targetConV0CandidatesColName = name;
    }

    @Override
    protected void detectorChanged(Detector detector) {
        // System.out.println("V0Monitoring::detectorChanged  Setting up the plotter");

        IAnalysisFactory fac = aida.analysisFactory();
        IPlotterFactory pfac = fac.createPlotterFactory("V0 Recon");
        functionFactory = aida.analysisFactory().createFunctionFactory(null);
        fitFactory = aida.analysisFactory().createFitFactory();
        tree = AIDA.defaultInstance().tree();
        tree.cd("/");
        boolean dirExists = false;
        String dirName = "/V0Recon";
        for (String st : tree.listObjectNames()) {
            System.out.println(st);
            if (st.contains(dirName)) {
                dirExists = true;
            }
        }
        tree.setOverwrite(true);
        if (!dirExists) {
            tree.mkdir(dirName);
        }
        tree.cd(dirName);

        // resetOccupancyMap(); // this is for calculatin
        plotterUncon = pfac.create("4a Unconstrained V0");

        plotterUncon.createRegions(2, 3);

        /* V0 Quantities */
 /* Mass, vertex, chi^2 of fit */
 /* beamspot constrained */
        nV0 = aida.histogram1D("Number of V0 per event", 5, 0, 5);
        unconMass = aida.histogram1D("Unconstrained Mass (GeV)", 100, 0, 0.200);
        unconVx = aida.histogram1D("Unconstrained Vx (mm)", 50, -1, 1);
        unconVy = aida.histogram1D("Unconstrained Vy (mm)", 50, -0.6, 0.6);
        unconVz = aida.histogram1D("Unconstrained Vz (mm)", 50, -10, 10);
        unconChi2 = aida.histogram1D("Unconstrained Chi2", 25, 0, 25);
        plot(plotterUncon, nV0, null, 0);
        plot(plotterUncon, unconMass, null, 1);
        plot(plotterUncon, unconChi2, null, 2);
        plot(plotterUncon, unconVx, null, 3);
        plot(plotterUncon, unconVy, null, 4);
        plot(plotterUncon, unconVz, null, 5);

        plotter2d = pfac.create("4b Unconstrained 2d plots");
        plotter2d.createRegions(2, 2);

        pEleVspPos = aida.histogram2D("P(e) vs P(p)", 50, 0, 2.5, 50, 0, 2.5);
        pyEleVspyPos = aida.histogram2D("Py(e) vs Py(p)", 50, -0.1, 0.1, 50, -0.1, 0.1);
        pxEleVspxPos = aida.histogram2D("Px(e) vs Px(p)", 50, -0.1, 0.1, 50, -0.1, 0.1);
        massVsVtxZ = aida.histogram2D("Mass vs Vz", 50, 0, 0.15, 50, -10, 10);
        plot(plotter2d, pEleVspPos, null, 0);
        plot(plotter2d, pxEleVspxPos, null, 1);
        plot(plotter2d, massVsVtxZ, null, 2);
        plot(plotter2d, pyEleVspyPos, null, 3);
        plotterUncon.show();
        plotter2d.show();
    }

    @Override
    public void process(EventHeader event) {
        if (removeRandomEvents && event.hasCollection(GenericObject.class, "TSBank")) {
            List<GenericObject> triggerList = event.get(GenericObject.class, "TSBank");
            for (GenericObject data : triggerList) {
                if (AbstractIntData.getTag(data) == TSData2019.BANK_TAG) {
                    TSData2019 triggerData = new TSData2019(data);
                    if (triggerData.isPulserTrigger() || triggerData.isFaradayCupTrigger()) {
                        return;
                    }
                }
            }
        }
        /* make sure everything is there */
        if (!event.hasCollection(ReconstructedParticle.class, finalStateParticlesColName)) {
            return;
        }
        if (!event.hasCollection(ReconstructedParticle.class, unconstrainedV0CandidatesColName)) {
            return;
        }
        if (!event.hasCollection(ReconstructedParticle.class, beamConV0CandidatesColName)) {
            return;
        }
        if (!event.hasCollection(ReconstructedParticle.class, targetConV0CandidatesColName)) {
            return;
        }
        nRecoEvents++;

        List<ReconstructedParticle> unConstrainedV0List = event.get(ReconstructedParticle.class,
                unconstrainedV0CandidatesColName);
        nV0.fill(unConstrainedV0List.size());
        for (ReconstructedParticle uncV0 : unConstrainedV0List) {
            Vertex uncVert = uncV0.getStartVertex();
            unconVx.fill(uncVert.getPosition().x());
            unconVy.fill(uncVert.getPosition().y());
            unconVz.fill(uncVert.getPosition().z());
            unconMass.fill(uncV0.getMass());
            unconChi2.fill(uncVert.getChi2());
            massVsVtxZ.fill(uncV0.getMass(), uncVert.getPosition().z());
            // this always has 2 tracks.
            List<ReconstructedParticle> trks = uncV0.getParticles();
            Track ele = trks.get(0).getTracks().get(0);
            Track pos = trks.get(1).getTracks().get(0);
            // if track #0 has charge>0 it's the electron! This seems mixed up, but remember the track
            // charge is assigned assuming a positive B-field, while ours is negative
            if (trks.get(0).getCharge() > 0) {
                pos = trks.get(0).getTracks().get(0);
                ele = trks.get(1).getTracks().get(0);
            }
            pEleVspPos.fill(getMomentum(ele), getMomentum(pos));
            pxEleVspxPos.fill(ele.getTrackStates().get(0).getMomentum()[1],
                    pos.getTrackStates().get(0).getMomentum()[1]);
            pyEleVspyPos.fill(ele.getTrackStates().get(0).getMomentum()[2],
                    pos.getTrackStates().get(0).getMomentum()[2]);
        }
    }

    public void setOutputPlots(String output) {
        this.outputPlots = output;
    }

    private double getMomentum(Track trk) {

        double px = trk.getTrackStates().get(0).getMomentum()[0];
        double py = trk.getTrackStates().get(0).getMomentum()[1];
        double pz = trk.getTrackStates().get(0).getMomentum()[2];
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    @Override
    public void endOfData() {
        if (outputPlots != null) {
            try {
                plotterUncon.writeToFile(outputPlots + "-Unconstrained.gif");
                plotter2d.writeToFile(outputPlots + "-2d.gif");
            } catch (IOException ex) {
                Logger.getLogger(TrackingReconPlots.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

}
