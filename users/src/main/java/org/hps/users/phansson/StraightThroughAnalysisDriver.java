/**
 * 
 */
package org.hps.users.phansson;

import hep.aida.IAnalysisFactory;
import hep.aida.IFitResult;
import hep.aida.IHistogram1D;
import hep.aida.IHistogram2D;
import hep.aida.IHistogramFactory;
import hep.aida.IPlotter;
import hep.aida.IPlotterFactory;
import hep.aida.IPlotterStyle;
import hep.aida.ref.rootwriter.RootFileStore;
import hep.physics.vec.BasicHep3Vector;
import hep.physics.vec.Hep3Vector;
import hep.physics.vec.VecOp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hps.monitoring.drivers.svt.SvtPlotUtils;
import org.hps.users.phansson.STUtils.STStereoTrack;
import org.lcsim.detector.converter.compact.subdetector.HpsTracker2;
import org.lcsim.detector.converter.compact.subdetector.SvtStereoLayer;
import org.lcsim.detector.tracker.silicon.HpsSiSensor;
import org.lcsim.event.EventHeader;
import org.lcsim.geometry.Detector;
import org.lcsim.geometry.compact.converter.HPSTrackerBuilder;
import org.lcsim.recon.tracking.digitization.sisim.SiTrackerHitStrip1D;
import org.lcsim.recon.tracking.digitization.sisim.TrackerHitType.CoordinateSystem;
import org.lcsim.util.Driver;
import org.lcsim.util.aida.AIDA;

/**
 * @author Per Hansson Adrian <phansson@slac.stanford.edu>
 *
 */
public class StraightThroughAnalysisDriver extends Driver {

    final static Logger logger = Logger.getLogger(StraightThroughAnalysisDriver.class.getSimpleName());
    private String stripClusterCollectionName = "StripClusterer_SiTrackerHitStrip1D";
    private List<HpsSiSensor> sensors = null;
    private IHistogram1D hitCount;
    private IHistogram1D topHitCount;
    private IHistogram1D botHitCount;
    private Map<String,IHistogram2D> stereoHitPositionsXY = new HashMap<String, IHistogram2D>();
    private Map<String,IHistogram1D> sensorHitResGlobal = new HashMap<String, IHistogram1D>();
    private Map<String,IHistogram1D>  sensorStereoHitYZResGlobal = new HashMap<String, IHistogram1D>();
    private Map<String,IHistogram1D>  sensorStereoHitXZResGlobal = new HashMap<String, IHistogram1D>();
    private Map<String,IHistogram1D> sensorHitPositions = new HashMap<String, IHistogram1D>();
    private Map<String,IHistogram1D> sensorHitCounts = new HashMap<String, IHistogram1D>();
    private Map<String,IHistogram1D> sensorHitTimes = new HashMap<String, IHistogram1D>();
    private Map<String,int[]> sensorHitCountMap = new HashMap<String,int[]>();
    private static AIDA aida = AIDA.defaultInstance();
    static final Hep3Vector origo = new BasicHep3Vector(0, 0, 0);
    private static final Hep3Vector origoStraightThroughs = new BasicHep3Vector(0, 0, 0);

    private final IAnalysisFactory af = aida.analysisFactory();
    private final IHistogramFactory hf = af.createHistogramFactory(aida.tree());
    private final IPlotterFactory pf = af.createPlotterFactory("Factory");
    private Map<String,IPlotter> plotters = new HashMap<String, IPlotter>();
    private String rootFileName = "";
    private int runNumber = -1;
    private IHistogram1D trackAxialHitCount[];
    private IHistogram1D trackAxialSlope[];
    private IHistogram1D trackAxialIntercept[];
    private IHistogram1D trackAxialCount[];
    private IHistogram2D trackAxialExtraPolation[];
    private IHistogram2D trackExtraPolationY[];
    private IHistogram2D trackExtraPolationX[];
    private IHistogram1D stereoHitCount[];
    private IHistogram1D trackHitCount[];
    private IHistogram1D trackSlope[][];
    private IHistogram1D trackIntercept[][];
    private IHistogram1D trackCount[];
    private IHistogram2D[] fitUpdateIteration;


    private boolean selectTime = true;
    private double timeMax = 8.0;
    private double timeMin = -8.0;
    private int minHitsAxialTrack = 6;
    private int minHitsStereoTrack = 6;
    private STUtils.STTrackFitter regressionFitter  = new STUtils.RegressionFit();
    //private STTrackFitter regressionFitter  = new LineFit();
    private final double startPointZ = 1500.0;
    private final double endPointZ = -3000.0;
    private final int nPointsZ = 100;
    private final double[] wirePosition = {-67.23, 0.0, -2337.1};
    private String subdetectorName = "Tracker";
    private List<SvtStereoLayer> stereoLayers;

    private String outputFilename = "";
    private PrintWriter gblPrintWriter = null;
    

    
    /**
     * 
     */
    public StraightThroughAnalysisDriver() {
        logger.setLevel(Level.INFO);
        STUtils.logger.setLevel(logger.getLevel());
    }

  
    private static double getTheta0(double beta,double p,double X0) {
        return 13.6/beta/p*Math.sqrt(X0)*(1 + 0.038*Math.log(X0));
    }
    protected void detectorChanged(Detector detector) {

        if(!outputFilename.isEmpty()) {
            try {
                gblPrintWriter = new PrintWriter( new BufferedWriter(new FileWriter(outputFilename)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("Created a GBL filewriter for to file \"" + outputFilename + "\"");
        }

        // find the stereo layers for this detector
        stereoLayers = ((HpsTracker2) detector.getSubdetector(subdetectorName).getDetectorElement()).getStereoPairs();
        StringBuffer sb = new StringBuffer("Found " + stereoLayers.size() + " stereo layers:");
        for(SvtStereoLayer sl : stereoLayers) sb.append(sl.getLayerNumber() + ": " + sl.getAxialSensor().getName()+ " - " + sl.getStereoSensor().getName() + "\n");
        logger.info(sb.toString());

        //
        double beta = 1.0;
        double p = 1.05;
        double X0 = 0.007; // per stereo pair
        double theta0 = getTheta0(beta, p, X0);
        double msErrY = 100.0*theta0;
        regressionFitter.setErrY(msErrY);
        
        
        // Get the HpsSiSensor objects from the geometry
        sensors = detector.getSubdetector("Tracker").getDetectorElement().findDescendants(HpsSiSensor.class);
        aida.tree().cd("/");
        plotters.put("Sensor hit position", af.createPlotterFactory().create("Sensor hit positions"));
        plotters.get("Sensor hit position").setStyle(this.getDefaultPlotterStyle("Hit y (mm)","Entries"));
        plotters.get("Sensor hit position").createRegions(6, 6);
        plotters.put("Sensor hit res", af.createPlotterFactory().create("Sensor hit res"));
        //plotters.get("Sensor hit res").setStyle(this.getDefaultPlotterStyle("Hit y res global (mm)","Entries"));
        plotters.get("Sensor hit res").createRegions(3, 6);
        plotters.put("Sensor stereo YZ hit res", af.createPlotterFactory().create("Sensor stereo YZ hit res"));
        plotters.get("Sensor stereo YZ hit res").setStyle(this.getDefaultPlotterStyle("Stereo hit y res global (mm)","Entries"));
        plotters.get("Sensor stereo YZ hit res").createRegions(3, 6);
        plotters.put("Sensor stereo XZ hit res", af.createPlotterFactory().create("Sensor stereo XZ hit res"));
        plotters.get("Sensor stereo XZ hit res").setStyle(this.getDefaultPlotterStyle("Stereo hit x res global (mm)","Entries"));
        plotters.get("Sensor stereo XZ hit res").createRegions(3, 6);
        plotters.put("Sensor hit times", af.createPlotterFactory().create("Sensor hit times"));
        plotters.get("Sensor hit times").setStyle(this.getDefaultPlotterStyle("Hit time (ns)","Entries"));
        plotters.get("Sensor hit times").createRegions(6, 6);
        plotters.put("Sensor cluster counts", af.createPlotterFactory().create("Sensor cluster counts"));
        plotters.get("Sensor cluster counts").setStyle(this.getDefaultPlotterStyle("Cluster multiplicity","Entries"));
        plotters.get("Sensor cluster counts").createRegions(6, 6);
        plotters.put("Cluster counts", af.createPlotterFactory().create("Cluster counts"));
        plotters.get("Cluster counts").setStyle(this.getDefaultPlotterStyle("Cluster multiplicity","Entries"));
        plotters.get("Cluster counts").createRegions(2,2);
        plotters.put("Track axial extrapolation", af.createPlotterFactory().create("Track axial extrapolation"));
        plotters.get("Track axial extrapolation").setStyle(this.getDefaultPlotterStyle("Z (mm)","Y (mm)"));
        plotters.get("Track axial extrapolation").createRegions(2,3);
        plotters.put("Track extrapolation Y", af.createPlotterFactory().create("Track extrapolation Y"));
        plotters.get("Track extrapolation Y").setStyle(this.getDefaultPlotterStyle("Z (mm)","Y (mm)"));
        plotters.get("Track extrapolation Y").createRegions(2,3);
        plotters.put("Track extrapolation X", af.createPlotterFactory().create("Track extrapolation X"));
        plotters.get("Track extrapolation X").setStyle(this.getDefaultPlotterStyle("Z (mm)","X (mm)"));
        plotters.get("Track extrapolation X").createRegions(2,3);
        plotters.put("Stereo hit position", af.createPlotterFactory().create("Stereo hit positions"));
        plotters.get("Stereo hit position").setStyle(this.getDefaultPlotterStyle("Hit x (mm)","Hit y (mm)"));
        plotters.get("Stereo hit position").createRegions(3, 6);
        plotters.put("Stereo hit count", af.createPlotterFactory().create("Stereo hit count"));
        plotters.get("Stereo hit count").createRegions(1, 2);
        plotters.put("Fit update iterations", af.createPlotterFactory().create("Fit update iterations"));
        plotters.get("Fit update iterations").setStyle(this.getDefaultPlotterStyle("Iterations","Average hit update magnitude (mm)"));
        plotters.get("Fit update iterations").createRegions(1, 2);

        topHitCount = hf.createHistogram1D("Top hit count", 21,-0.5, 20.5);
        botHitCount = hf.createHistogram1D("Bottom hit count", 21,-0.5, 20.5);
        hitCount = hf.createHistogram1D("Hit count", 21, -0.5, 20.5);
        trackAxialHitCount = new IHistogram1D[2];
        trackAxialCount = new IHistogram1D[2];
        trackAxialSlope = new IHistogram1D[2];
        trackAxialIntercept = new IHistogram1D[2];
        trackAxialExtraPolation = new IHistogram2D[5];
        trackExtraPolationY = new IHistogram2D[5];
        trackExtraPolationX = new IHistogram2D[5];
        stereoHitCount = new IHistogram1D[2];
        trackHitCount = new IHistogram1D[2];
        trackCount = new IHistogram1D[2];
        trackSlope = new IHistogram1D[2][];
        trackIntercept = new IHistogram1D[2][];
        fitUpdateIteration = new IHistogram2D[2];
        

        for(int i=0;i<2;++i) {
            String half = i==0 ? "top" : "bottom";
            
            trackHitCount[i]= hf.createHistogram1D("Track " + half + " hit multiplicity", 11, -0.5, 10.5);
            trackCount[i] = hf.createHistogram1D("Track " + half + " multiplicity", 11, -0.5, 10.5);
            trackSlope[i] = new IHistogram1D[2];
            trackIntercept[i] = new IHistogram1D[2];
            trackSlope[i][STUtils.STStereoTrack.VIEW.YZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.YZ.name()  + " slope", 50, -0.05, 0.05);
            trackSlope[i][STUtils.STStereoTrack.VIEW.XZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.XZ.name()  + " slope", 50, -0.1, 0.1);
            if(i==0) {
                trackIntercept[i][STUtils.STStereoTrack.VIEW.YZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.YZ.name()  + " intecept", 50, 0, 50);
                trackIntercept[i][STUtils.STStereoTrack.VIEW.XZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.XZ.name()  + " intecept", 50, -80, 0);
                trackAxialIntercept[i] = hf.createHistogram1D("Track axial " + half +" intercept", 50, 0, 50);
            } else {
                trackIntercept[i][STUtils.STStereoTrack.VIEW.YZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.YZ.name()  + " intecept", 50, -50, 0);
                trackIntercept[i][STUtils.STStereoTrack.VIEW.XZ.ordinal()] = hf.createHistogram1D("Track " + half +" " + STUtils.STStereoTrack.VIEW.XZ.name()  + " intecept", 50, -50, 50);
                trackAxialIntercept[i] = hf.createHistogram1D("Track axial " + half +" intercept", 50, -50, 0);
            }
            trackAxialHitCount[i] = hf.createHistogram1D("Track axial " + half +" hit multiplicity", 11, -0.5, 10.5);
            trackAxialCount[i] = hf.createHistogram1D("Track axial " + half +" multiplicity", 2, -0.5, 1.5);
            trackAxialSlope[i] = hf.createHistogram1D("Track axial " + half +" slope", 50, -0.05, 0.05);
            trackAxialExtraPolation[i] = hf.createHistogram2D("Track axial " + half +" extrapolation", nPointsZ, endPointZ, startPointZ,50,-80,80);
            trackExtraPolationY[i] = hf.createHistogram2D("Track " + half +" extrapolation Y", nPointsZ, endPointZ, startPointZ,50,-80,80);
            trackExtraPolationX[i] = hf.createHistogram2D("Track " + half +" extrapolation X", nPointsZ, endPointZ, startPointZ,50,-100,60);
            stereoHitCount[i] = hf.createHistogram1D("Stereo hit count " + half, 11, -0.5, 10.5);
            
            fitUpdateIteration[i] = hf.createHistogram2D("Fit update" + half, 6, -0.5, 6.5,50,0,20);
            
            plotters.get("Fit update iterations").region(i).plot(fitUpdateIteration[i]);
            
            plotters.put("Track " + half +" axial", af.createPlotterFactory().create("Track " + half +" axial"));
            //plotters.get("Track " + half +" axial").setStyle(this.getDefaultPlotterStyle("","Entries",true));
            plotters.get("Track " + half +" axial").createRegions(2,2);

            plotters.get("Track " + half +" axial").region(0).plot(trackAxialHitCount[i]);
            plotters.get("Track " + half +" axial").region(1).plot(trackAxialCount[i]);
            plotters.get("Track " + half +" axial").region(2).plot(trackAxialSlope[i]);
            plotters.get("Track " + half +" axial").region(3).plot(trackAxialIntercept[i]);



            plotters.put("Track " + half, af.createPlotterFactory().create("Track " + half));
            //plotters.get("Track " + half +" axial").setStyle(this.getDefaultPlotterStyle("","Entries",true));
            plotters.get("Track " + half).createRegions(3,2);
            plotters.get("Track " + half).region(0).plot(trackHitCount[i]);
            plotters.get("Track " + half).region(1).plot(trackCount[i]);
            plotters.get("Track " + half).region(2).plot(trackSlope[i][STUtils.STStereoTrack.VIEW.YZ.ordinal()]);
            plotters.get("Track " + half).region(3).plot(trackIntercept[i][STUtils.STStereoTrack.VIEW.YZ.ordinal()]);
            plotters.get("Track " + half).region(4).plot(trackSlope[i][STUtils.STStereoTrack.VIEW.XZ.ordinal()]);
            plotters.get("Track " + half).region(5).plot(trackIntercept[i][STUtils.STStereoTrack.VIEW.XZ.ordinal()]);
            
            plotters.get("Stereo hit count").region(i).plot(stereoHitCount[i]);
            
            plotters.get("Track axial extrapolation").region(i).plot(trackAxialExtraPolation[i]);
            plotters.get("Track extrapolation Y").region(i).plot(trackExtraPolationY[i]);
            plotters.get("Track extrapolation X").region(i).plot(trackExtraPolationX[i]);
            
        }
        
        trackAxialExtraPolation[2] = hf.createHistogram2D("Track axial extrapolation", nPointsZ, endPointZ, startPointZ,50,-80,80);
        plotters.get("Track axial extrapolation").region(2).plot(trackAxialExtraPolation[2]);
        trackAxialExtraPolation[3] = hf.createHistogram2D("Track axial extrapolation wires", nPointsZ, wirePosition[2]-350, wirePosition[2]+350,50,-20,20);
        plotters.get("Track axial extrapolation").region(3).plot(trackAxialExtraPolation[3]);
        trackAxialExtraPolation[4] = hf.createHistogram2D("Track axial extrapolation rndm", nPointsZ, -200.0, 200.0,50,-60,60);
        plotters.get("Track axial extrapolation").region(4).plot(trackAxialExtraPolation[4]);

        trackExtraPolationY[2] = hf.createHistogram2D("Track extrapolation Y", nPointsZ, endPointZ, startPointZ,50,-80,80);
        plotters.get("Track extrapolation Y").region(2).plot(trackExtraPolationY[2]);
        trackExtraPolationY[3] = hf.createHistogram2D("Track extrapolation Y wires", nPointsZ, wirePosition[2]-350, wirePosition[2]+350,50,-20,20);
        plotters.get("Track extrapolation Y").region(3).plot(trackExtraPolationY[3]);
        trackExtraPolationY[4] = hf.createHistogram2D("Track extrapolation Y rndm", nPointsZ, -200.0, 200.0,50,-60,60);
        plotters.get("Track extrapolation Y").region(4).plot(trackExtraPolationY[4]);

        trackExtraPolationX[2] = hf.createHistogram2D("Track extrapolation X", nPointsZ, endPointZ, startPointZ,50,-100,60);
        plotters.get("Track extrapolation X").region(2).plot(trackExtraPolationX[2]);
        trackExtraPolationX[3] = hf.createHistogram2D("Track extrapolation X wires", nPointsZ, wirePosition[2]-350, wirePosition[2]+350,50,-80,-20);
        plotters.get("Track extrapolation X").region(3).plot(trackExtraPolationX[3]);
        trackExtraPolationX[4] = hf.createHistogram2D("Track extrapolation X rndm", nPointsZ, -200.0, 200.0,50,-60,0);
        plotters.get("Track extrapolation X").region(4).plot(trackExtraPolationX[4]);

        plotters.get("Cluster counts").region(0).plot(hitCount);
        plotters.get("Cluster counts").region(1).plot(topHitCount);
        plotters.get("Cluster counts").region(2).plot(botHitCount);
        
        
        for(HpsSiSensor sensor : sensors) {
            
            if(sensor.isBottomLayer()) {
                sensorHitPositions.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hitpos1D", 50, -60, 0));
                if(sensor.isAxial()) {
                    stereoHitPositionsXY.put(sensor.getName(), hf.createHistogram2D(sensor.getName() + "_stereohitpos2D", 50, -100, 1000,50,-60,0));
                    plotters.get("Stereo hit position").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(stereoHitPositionsXY.get(sensor.getName()));
                }
            }
            else{
                sensorHitPositions.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hitpos1D", 50, 0, 60));
                if(sensor.isAxial()) {
                    stereoHitPositionsXY.put(sensor.getName(), hf.createHistogram2D(sensor.getName() + "_stereohitpos2D", 50, -100, 100,50,0,60));
                    plotters.get("Stereo hit position").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(stereoHitPositionsXY.get(sensor.getName()));
                }
            }
            sensorHitCounts.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hitcount", 11, -0.5, 10.5));
            sensorHitTimes.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hittime", 50, -100, 100));
            sensorHitCountMap.put(sensor.getName(), new int[1]);
            sensorHitCountMap.get(sensor.getName())[0] = 0;

            if(sensor.isAxial()) {
                sensorHitResGlobal.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hitresglobal", 50, -0.7, 0.7));
                plotters.get("Sensor hit res").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(sensorHitResGlobal.get(sensor.getName()));
                plotters.get("Sensor hit res").region(0).style().dataStyle().showInStatisticsBox(true);
                sensorStereoHitYZResGlobal.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_stereohityzresglobal", 50, -1, 1));
                plotters.get("Sensor stereo YZ hit res").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(sensorStereoHitYZResGlobal.get(sensor.getName()));
                plotters.get("Sensor stereo YZ hit res").region(0).style().dataStyle().showInStatisticsBox(true);
                sensorStereoHitXZResGlobal.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_stereohitxzresglobal", 50, -1, 1));
                plotters.get("Sensor stereo XZ hit res").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(sensorStereoHitXZResGlobal.get(sensor.getName()));
                plotters.get("Sensor stereo XZ hit res").region(0).style().dataStyle().showInStatisticsBox(true);

            
            }
            
            plotters.get("Sensor hit position").region(SvtPlotUtils.computePlotterRegion(sensor)).plot(sensorHitPositions.get(sensor.getName()));
            plotters.get("Sensor hit times").region(SvtPlotUtils.computePlotterRegion(sensor)).plot(sensorHitTimes.get(sensor.getName()));
            plotters.get("Sensor cluster counts").region(SvtPlotUtils.computePlotterRegion(sensor)).plot(sensorHitCounts.get(sensor.getName()));
            
        }

        for(IPlotter plotter : plotters.values())
            plotter.show();
    }
    
    
    protected void resetCounts() {
        //reset hit counts
        for(HpsSiSensor sensor : sensors) {
            sensorHitCountMap.get(sensor.getName())[0] = 0;
        }
    }
    protected void process(EventHeader event) {
    
        logger.fine("Process event");
        
        if (runNumber  == -1) {
            runNumber = event.getRunNumber();
        }
        
        //Find all strip clusters in the events
        List<SiTrackerHitStrip1D> stripClusters = new ArrayList<SiTrackerHitStrip1D>();
        if(event.hasCollection(SiTrackerHitStrip1D.class, stripClusterCollectionName)) {
            stripClusters = event.get(SiTrackerHitStrip1D.class, stripClusterCollectionName);
        }
        
        logger.fine("Found " + stripClusters.size() + " strip clusters in the event");
    
        resetCounts();
        
        List<SiTrackerHitStrip1D> topHits= new ArrayList<SiTrackerHitStrip1D>(); 
        List<SiTrackerHitStrip1D> botHits= new ArrayList<SiTrackerHitStrip1D>(); 
        
        // Create a map of axial hits
        List<Map<Integer, List<SiTrackerHitStrip1D>>> axialHitsPerLayer = new ArrayList<Map<Integer, List<SiTrackerHitStrip1D>>>();
        axialHitsPerLayer.add(new HashMap<Integer, List<SiTrackerHitStrip1D>>());
        axialHitsPerLayer.add(new HashMap<Integer, List<SiTrackerHitStrip1D>>());
        
        // Create a map of stereo hits
        List<Map<Integer, List<SiTrackerHitStrip1D>>> stereoHitsPerLayer = new ArrayList<Map<Integer, List<SiTrackerHitStrip1D>>>();
        stereoHitsPerLayer.add(new HashMap<Integer, List<SiTrackerHitStrip1D>>());
        stereoHitsPerLayer.add(new HashMap<Integer, List<SiTrackerHitStrip1D>>());
        
        
        for(SiTrackerHitStrip1D cluster : stripClusters) {
            SiTrackerHitStrip1D cluster_global = cluster.getTransformedHit(CoordinateSystem.GLOBAL);
            HpsSiSensor sensor = (HpsSiSensor) cluster.getRawHits().get(0).getDetectorElement();
            int layer = HPSTrackerBuilder.getLayerFromVolumeName(sensor.getName());
            sensorHitPositions.get(sensor.getName()).fill(cluster_global.getPositionAsVector().y());
            sensorHitTimes.get(sensor.getName()).fill(cluster.getTime());
            logger.fine("cluster position " + cluster_global.getPositionAsVector().toString());
            sensorHitCountMap.get(sensor.getName())[0]++;

            if(sensor.isTopLayer()) {
                topHits.add(cluster);
            } else {
                botHits.add(cluster);
            }
            //hit time selection
            if(!selectTime || (cluster.getTime() < timeMax && cluster.getTime()>timeMin)) { 

                // look at axials and stereo separately
                if(sensor.isAxial()) {

                    Map<Integer, List<SiTrackerHitStrip1D>> hitsPerLayer;
                    if(sensor.isTopLayer()) {
                        hitsPerLayer = axialHitsPerLayer.get(0);
                    } else {
                        hitsPerLayer = axialHitsPerLayer.get(1);
                    }

                    List<SiTrackerHitStrip1D> hits;
                    hits = hitsPerLayer.get(layer);
                    if(hits == null) {
                        hits = new ArrayList<SiTrackerHitStrip1D>();
                        hitsPerLayer.put(layer, hits);
                    }
                    hits.add(cluster);

                } else {
                    
                    // look at stereos

                    Map<Integer, List<SiTrackerHitStrip1D>> hitsPerLayer;
                    if(sensor.isTopLayer()) {
                        hitsPerLayer = stereoHitsPerLayer.get(0);
                    } else {
                        hitsPerLayer = stereoHitsPerLayer.get(1);
                    }

                    List<SiTrackerHitStrip1D> hits;
                    hits = hitsPerLayer.get(layer);
                    if(hits == null) {
                        hits = new ArrayList<SiTrackerHitStrip1D>();
                        hitsPerLayer.put(layer, hits);
                    }
                    hits.add(cluster);

                }

            }
            
        }
        
        //count hits
        hitCount.fill(stripClusters.size());
        topHitCount.fill(topHits.size());
        botHitCount.fill(botHits.size());
  
        // fill hit positions
        for(HpsSiSensor sensor : sensors) 
            sensorHitCounts.get(sensor.getName()).fill(sensorHitCountMap.get(sensor.getName())[0] );

        
        // Pattern recognition for axial hits - yeah!
        List< List<SiTrackerHitStrip1D>> axialSeedHits = new ArrayList<List<SiTrackerHitStrip1D>>();
        for(int ihalf=0; ihalf<2; ++ihalf) {
            List<SiTrackerHitStrip1D> seedHits = new ArrayList<SiTrackerHitStrip1D>();
            for(int layer : axialHitsPerLayer.get(ihalf).keySet()) {
                // single hit on the sensor
                if( axialHitsPerLayer.get(ihalf).get(layer).size() == 1 )
                    seedHits.add(axialHitsPerLayer.get(ihalf).get(layer).get(0));
            }
            axialSeedHits.add(seedHits);
        }
        
        // Pattern recognition for stereo hits - yeah!
        List< List<SiTrackerHitStrip1D>> stereoSeedHits = new ArrayList<List<SiTrackerHitStrip1D>>();
        for(int ihalf=0; ihalf<2; ++ihalf) {
            List<SiTrackerHitStrip1D> seedHits = new ArrayList<SiTrackerHitStrip1D>();
            for(int layer : stereoHitsPerLayer.get(ihalf).keySet()) {
                // single hit on the sensor
                if( stereoHitsPerLayer.get(ihalf).get(layer).size() == 1 )
                    seedHits.add(stereoHitsPerLayer.get(ihalf).get(layer).get(0));
            }
            stereoSeedHits.add(seedHits);
        }
        

        // try to make stereo hits
        
        
        List< List<STUtils.StereoPair> > stereoPairs  = new ArrayList< List<STUtils.StereoPair>>();
        
        
        
        for(int ihalf=0; ihalf<2; ++ihalf) {
            List<STUtils.StereoPair> stereoPairCandidates = new ArrayList< STUtils.StereoPair>();
            List<SiTrackerHitStrip1D> aSeedHits = axialSeedHits.get(ihalf);
            for(SiTrackerHitStrip1D axialSeedHit : aSeedHits) {

                // find the stereo sensor and its hit from the pre-compiled stereo pair list
                HpsSiSensor stereoSensor = null;
                SiTrackerHitStrip1D stereoSeedHit = null;
                
                HpsSiSensor axialSensor = (HpsSiSensor) axialSeedHit.getRawHits().get(0).getDetectorElement();
                logger.fine("Look for stereo sensor to \"" + axialSensor.getName() + "\"");
                
                for(SvtStereoLayer stereoLayer : stereoLayers) {
                    if(stereoLayer.getAxialSensor().getName().equals(axialSensor.getName())) {
                        stereoSensor = stereoLayer.getStereoSensor();
                        break;
                    }
                }
                
                // make sure it was found
                if(stereoSensor == null) throw new RuntimeException("Couldn't find stereo sensor to \"" + axialSensor.getName() + "\"");
                
                logger.fine("Found stereo sensor \"" + stereoSensor.getName() + "\"");
                
                // find the hit
                // this only works for single hits per sensor
                for(List<SiTrackerHitStrip1D> sSeedHits : stereoSeedHits) {
                    for(SiTrackerHitStrip1D stereoSeedHitCandidate : sSeedHits) {
                        HpsSiSensor sensor = (HpsSiSensor) stereoSeedHitCandidate.getRawHits().get(0).getDetectorElement();
                        if(sensor.getName().equals(stereoSensor.getName())) {
                            logger.fine("Found stereo hit at " + stereoSeedHitCandidate.getPositionAsVector().toString() + " at sensor \"" + stereoSensor.getName() + "\"");
                            stereoSeedHit = stereoSeedHitCandidate;
                            break; // ok, this only works for single hits per sensor
                        }
                    }
                }
                
                // Check if we found a candidate pair
                if(stereoSeedHit != null) {
                    STUtils.StereoPair pair = new STUtils.StereoPair(axialSeedHit,stereoSeedHit,origoStraightThroughs);
                    if(STUtils.StereoPair.passCuts(pair))
                        stereoPairCandidates.add( pair );
                }
            }
            stereoPairs.add(stereoPairCandidates);
        }

        
       
        // loop over the stereo pairs
        for(int ihalf=0; ihalf<2; ++ihalf) {
            List< STUtils.StereoPair > pairs = stereoPairs.get(ihalf);
            
            logger.fine("Found " + pairs.size() + " stereo candidates for " + (ihalf == 0 ? "top" : "bottom"));
            
            // Fill count
            stereoHitCount[ihalf].fill(pairs.size());

            // Plot the position of the hits
            for(STUtils.StereoPair pair : pairs) {
                Hep3Vector p = pair.getPosition();
                stereoHitPositionsXY.get(pair.getAxial().getRawHits().get(0).getDetectorElement().getName()).fill(p.x(),p.y());
                logger.fine(p.toString() + " from " + pair.getAxial().getPositionAsVector().toString() + "  " + pair.getStereo().getPositionAsVector().toString() 
                        + " ("+pair.getAxial().getRawHits().get(0).getDetectorElement().getName() +" and "+pair.getStereo().getRawHits().get(0).getDetectorElement().getName()+")");
            }
        }
        
        
        // add hits to axial tracks and fit them
        List<STUtils.STTrack> axialTracks = new ArrayList<STUtils.STTrack>();
        for(List<SiTrackerHitStrip1D> seedHits : axialSeedHits) {
            
            if(seedHits.size() < minHitsAxialTrack) 
                continue;

            STUtils.STTrack track = new STUtils.STTrack();
            track.setHits(seedHits);
            
            logger.fine("Fit axial track");
            regressionFitter.fit(track);
            track.addFit(regressionFitter.getFit());
            
            axialTracks.add(track);
        }

        // add stereo hits to tracks and fit them with simple regression in 1D
        List<STUtils.STStereoTrack> stereoTracks = new ArrayList<STUtils.STStereoTrack>();
        for(List<STUtils.StereoPair> seedHits : stereoPairs) {
            
            if(seedHits.size() < minHitsStereoTrack) 
                continue;

            STUtils.STStereoTrack track = new STUtils.STStereoTrack();
            track.setHits(seedHits);
            
            STUtils.fit(regressionFitter, track);

            //            logger.fine("Fit stereo track in YZ");
//            regressionFitter.fit(track.getPointList(STStereoTrack.VIEW.YZ,null));
//            track.setFit(regressionFitter.getFit(), STStereoTrack.VIEW.YZ);
//            logger.fine("Fit stereo track in XZ");
//            regressionFitter.fit(track.getPointList(STStereoTrack.VIEW.XZ,STStereoTrack.VIEW.YZ));
//            track.setFit(regressionFitter.getFit(), STStereoTrack.VIEW.XZ);
            
            stereoTracks.add(track);
        }
        
        
        
        // Loop over the axial tracks
        int nTracksAxial[] = {0,0};
        for(STUtils.STTrack track : axialTracks) {
            
            for(SiTrackerHitStrip1D hit : track.getHits()) {
                double yhit = hit.getPositionAsVector().y();
                double zHit = hit.getPositionAsVector().z();
                double yPred = track.predict(zHit);
                double resGlobal = yhit - yPred;
                sensorHitResGlobal.get(hit.getRawHits().get(0).getDetectorElement().getName()).fill(resGlobal);
            }
            
            logger.fine(track.toString());

            if(track.isTop()) {
                nTracksAxial[0]++;
                trackAxialHitCount[0].fill(track.getHits().size());
                trackAxialSlope[0].fill(track.getSlope());
                trackAxialIntercept[0].fill(track.getIntercept());
            }
            else {
                nTracksAxial[1]++;
                trackAxialHitCount[1].fill(track.getHits().size());
                trackAxialSlope[1].fill(track.getSlope());
                trackAxialIntercept[1].fill(track.getIntercept());
            }
         }   
        trackAxialCount[0].fill(nTracksAxial[0]);
        trackAxialCount[1].fill(nTracksAxial[1]);
        
        
        
        // Loop over the stereo tracks
        int nTracks[] = {0,0};
        for(STUtils.STStereoTrack track : stereoTracks) {

            int half = track.isTop() ? 0 : 1;
            
            // update the hit positions with the fit
            double delta = 9999.9;
            int idelta = 0;
            while(delta>0.05) {
                logger.fine(idelta + ": delta " + delta);
                delta = 0.0;
                for(STUtils.StereoPair pair : track.getHits()) {
                    Hep3Vector p = new BasicHep3Vector(pair.getPosition().v());
                    Hep3Vector trackDirection = track.getDirection();
                    logger.fine("updatePosition " + p.toString() + " with track dir " + trackDirection.toString());
                    pair.updatePosition(trackDirection);
                    Hep3Vector pnew = pair.getPosition();
                    logger.fine("new position " + pnew.toString());
                    delta += VecOp.sub(p, pnew).magnitude();
                }
                delta = delta / track.getHits().size();
                idelta++;

                fitUpdateIteration[half].fill(idelta,delta);

                // Re-fit the track after the update
                STUtils.fit(regressionFitter,track);
//                track.clearFit();
//                logger.fine("Fit stereo track in YZ");
//                regressionFitter.fit(track.getPointList(STStereoTrack.VIEW.YZ,null));
//                track.setFit(regressionFitter.getFit(), STStereoTrack.VIEW.YZ);
//                logger.fine("Fit stereo track in XZ");
//                regressionFitter.fit(track.getPointList(STStereoTrack.VIEW.XZ,STStereoTrack.VIEW.YZ));
//                track.setFit(regressionFitter.getFit(), STStereoTrack.VIEW.XZ);
                
            }
            logger.fine("finished update position after " + idelta + "iterations with delta " + delta);
            
            
            for(STUtils.StereoPair hit : track.getHits()) {
                double yhit = hit.getPosition().y();
                double xhit = hit.getPosition().x();
                double zHit = hit.getPosition().z();
                double xyPred[] = track.predict(zHit);
                double resGlobalY = yhit - xyPred[STStereoTrack.VIEW.YZ.ordinal()];
                double resGlobalX = xhit - xyPred[STStereoTrack.VIEW.XZ.ordinal()];;
                sensorStereoHitYZResGlobal.get(hit.getAxial().getRawHits().get(0).getDetectorElement().getName()).fill(resGlobalY);
                sensorStereoHitXZResGlobal.get(hit.getAxial().getRawHits().get(0).getDetectorElement().getName()).fill(resGlobalX);
            }

            logger.fine(track.toString());

            nTracks[half]++;
            trackHitCount[half].fill(track.getHits().size());
            for(int v=0;v<2;++v) {
                STUtils.STStereoTrack.VIEW view = STUtils.STStereoTrack.VIEW.values()[v];
                trackSlope[half][v].fill(track.getSlope()[view.ordinal()]);
                trackIntercept[half][v].fill(track.getIntercept()[view.ordinal()]);
            }
        }   
        trackCount[0].fill(nTracks[0]);
        trackCount[1].fill(nTracks[1]);


        
        
        //predict where these axial tracks go upstream in Y
        double zIter,yIter,start,end;
        for(STUtils.STTrack track : axialTracks) {
            int half = track.isTop() ? 0 : 1;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = startPointZ - i*(startPointZ - endPointZ)/(double)nPointsZ; 
                yIter = track.predict(zIter);
                trackAxialExtraPolation[half].fill(zIter,yIter);
                trackAxialExtraPolation[2].fill(zIter,yIter);
            }
            start = wirePosition[2] + 350.0;
            end = wirePosition[2] - 350.0;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = start - i*(start - end)/(double)nPointsZ; 
                yIter = track.predict(zIter);
                trackAxialExtraPolation[3].fill(zIter,yIter);
            }
            start = 200.0;
            end = -200.0;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = start - i*(start - end)/(double)nPointsZ; 
                yIter = track.predict(zIter);
                trackAxialExtraPolation[4].fill(zIter,yIter);
            }
        }

        
      //predict where the stereo tracks go upstream in X-Y
        double xyIter[];
        for(STStereoTrack track : stereoTracks) {
            int half = track.isTop() ? 0 : 1;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = startPointZ - i*(startPointZ - endPointZ)/(double)nPointsZ; 
                xyIter = track.predict(zIter);
                trackExtraPolationY[half].fill(zIter,xyIter[STStereoTrack.VIEW.YZ.ordinal()]);
                trackExtraPolationY[2].fill(zIter,xyIter[STStereoTrack.VIEW.YZ.ordinal()]);
                trackExtraPolationX[half].fill(zIter,xyIter[STStereoTrack.VIEW.XZ.ordinal()]);
                trackExtraPolationX[2].fill(zIter,xyIter[STStereoTrack.VIEW.XZ.ordinal()]);
            }
            start = wirePosition[2] + 350.0;
            end = wirePosition[2] - 350.0;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = start - i*(start - end)/(double)nPointsZ; 
                xyIter = track.predict(zIter);
                trackExtraPolationY[3].fill(zIter,xyIter[STStereoTrack.VIEW.YZ.ordinal()]);
                trackExtraPolationX[3].fill(zIter,xyIter[STStereoTrack.VIEW.XZ.ordinal()]);
            }
            start = 200.0;
            end = -200.0;
            for(int i = 0; i < (nPointsZ+1); ++i) {
                zIter = start - i*(start - end)/(double)nPointsZ; 
                xyIter = track.predict(zIter);
                trackExtraPolationY[4].fill(zIter,xyIter[STStereoTrack.VIEW.YZ.ordinal()]);
                trackExtraPolationX[4].fill(zIter,xyIter[STStereoTrack.VIEW.XZ.ordinal()]);
            }
        }


        // GBL interface
        if(gblPrintWriter != null) {
            STUtils.printGBL(gblPrintWriter, event, stereoTracks);
        }


    }
    
    
    protected void endOfData() {
        
        rootFileName = outputFilename + "_hitrecon.root";
        RootFileStore rootFileStore = new RootFileStore(rootFileName);
        try {
            rootFileStore.open();
            rootFileStore.add(aida.tree());
            rootFileStore.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        if(gblPrintWriter != null) gblPrintWriter.close();
        
    }

    
    private void updateFits() {
        for(HpsSiSensor sensor : sensors) {
            IHistogram1D h = sensorHitResGlobal.get(sensor.getName());
            if(h != null) {
                if( h.entries() > 20) {
                    IFitResult f = SvtPlotUtils.performGaussianFit(h);
                    if(f != null) {
                        SvtPlotUtils.plot(plotters.get("Sensor hit res"), f.fittedFunction(), null, SvtPlotUtils.computePlotterRegionAxialOnly(sensor));
                    }
                }
            }
//            if(sensor.isAxial()) {
//                sensorHitResGlobal.put(sensor.getName(), hf.createHistogram1D(sensor.getName() + "_hitresglobal", 50, -1.5, 1.5));
//                plotters.get("Sensor hit res").region(SvtPlotUtils.computePlotterRegionAxialOnly(sensor)).plot(sensorHitResGlobal.get(sensor.getName()));
//                plotters.get("Sensor hit res").region(0).style().dataStyle().showInStatisticsBox(true);
//            }
            
        }
        
    }

    protected IPlotterStyle getMinPlotterStyle(String xAxisTitle, String yAxisTitle) {
        // Create a default style
        IPlotterStyle style = this.pf.createPlotterStyle();

        // Set the style of the X axis
        if(!xAxisTitle.isEmpty()) style.xAxisStyle().setLabel(xAxisTitle);
        style.xAxisStyle().labelStyle().setFontSize(14);
        style.xAxisStyle().setVisible(true);

        // Set the style of the Y axis
        if(!yAxisTitle.isEmpty()) style.yAxisStyle().setLabel(yAxisTitle);
        style.yAxisStyle().labelStyle().setFontSize(14);
        style.yAxisStyle().setVisible(true);

        // Turn off the histogram grid 
        style.gridStyle().setVisible(false);
        
        // Turn off legend
        style.legendBoxStyle().setVisible(false);
        
        style.setParameter("hist2DStyle", "colorMap");
        style.dataStyle().fillStyle().setParameter("colorMapScheme", "rainbow");
        style.dataStyle().fillStyle().setColor("yellow");
        style.dataStyle().errorBarStyle().setVisible(false);
        
        return style;
    }
    
    protected IPlotterStyle getDefaultPlotterStyle(String xAxisTitle, String yAxisTitle) {
        // Create a default style
        IPlotterStyle style = this.pf.createPlotterStyle();

        style.dataBoxStyle().setVisible(true);

        // Set the style of the X axis
        if(!xAxisTitle.isEmpty()) style.xAxisStyle().setLabel(xAxisTitle);
        style.xAxisStyle().labelStyle().setFontSize(14);
        style.xAxisStyle().setVisible(true);

        // Set the style of the Y axis
        if(!yAxisTitle.isEmpty()) style.yAxisStyle().setLabel(yAxisTitle);
        style.yAxisStyle().labelStyle().setFontSize(14);
        style.yAxisStyle().setVisible(true);

        // Turn off the histogram grid 
        style.gridStyle().setVisible(false);
        
        style.setParameter("hist2DStyle", "colorMap");
        style.dataStyle().fillStyle().setParameter("colorMapScheme", "rainbow");
        style.dataStyle().fillStyle().setColor("yellow");
        style.dataStyle().errorBarStyle().setVisible(false);
        
        return style;
    }



    public void setOutputFilename(String gblOutputFilename) {
        this.outputFilename = gblOutputFilename;
    }
    
   
    
    
}