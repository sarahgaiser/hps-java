package org.hps.recon.tracking;

import hep.physics.matrix.SymmetricMatrix;
import hep.physics.vec.BasicHep3Vector;
import hep.physics.vec.Hep3Matrix;
import hep.physics.vec.Hep3Vector;
import hep.physics.vec.SpacePoint;
import hep.physics.vec.VecOp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hps.recon.tracking.EventQuality.Quality;
import org.hps.recon.tracking.gbl.HelicalTrackStripGbl;
import org.lcsim.detector.ITransform3D;
import org.lcsim.detector.solids.Box;
import org.lcsim.detector.solids.Point3D;
import org.lcsim.detector.solids.Polygon3D;
import org.lcsim.detector.tracker.silicon.HpsSiSensor;
import org.lcsim.detector.tracker.silicon.SiSensor;
import org.lcsim.event.MCParticle;
import org.lcsim.event.RawTrackerHit;
import org.lcsim.event.Track;
import org.lcsim.event.TrackerHit;
import org.lcsim.fit.helicaltrack.HelicalTrackFit;
import org.lcsim.fit.helicaltrack.HelicalTrackHit;
import org.lcsim.fit.helicaltrack.HelicalTrackStrip;
import org.lcsim.fit.helicaltrack.HelixParamCalculator;
import org.lcsim.fit.helicaltrack.HelixUtils;
import org.lcsim.fit.helicaltrack.HitUtils;
import org.lcsim.fit.helicaltrack.MultipleScatter;
import org.lcsim.recon.tracking.seedtracker.SeedCandidate;
import org.lcsim.recon.tracking.seedtracker.SeedTrack;
import org.lcsim.util.swim.Helix;

/**
 * Assorted helper functions for the track and helix objects in lcsim. Re-use as much of HelixUtils
 * as possible.
 * 
 * @author Omar Moreno <omoreno1@ucsc.edu>
 */
// TODO: Switch to tracking/LCsim coordinates for the extrapolation output!
// FIXME: This class should probably be broken up into several different sets of utilities by type. --JM
public class TrackUtils {

    /**
     * Private constructor to make class static only
     */
    private TrackUtils() {
    }

    /**
     * Extrapolate track to a position along the x-axis. Turn the track into a helix object in
     * order to use HelixUtils.
     * @param track
     * @param x
     * @return
     */
    public static Hep3Vector extrapolateHelixToXPlane(Track track, double x) {
        return extrapolateHelixToXPlane(getHTF(track), x);
    }

    /**
     * Extrapolate helix to a position along the x-axis. Re-use HelixUtils.
     * @param track
     * @param x
     * @return
     */
    public static Hep3Vector extrapolateHelixToXPlane(HelicalTrackFit htf, double x) {
        double s = HelixUtils.PathToXPlane(htf, x, 0., 0).get(0);
        return HelixUtils.PointOnHelix(htf, s);
    }

    // ==========================================================================
    // Helper functions for track parameters and commonly used derived variables

    public static double getPhi(Track track, Hep3Vector position) {
        double x = Math.sin(getPhi0(track)) - (1 / getR(track)) * (position.x() - getX0(track));
        double y = Math.cos(getPhi0(track)) + (1 / getR(track)) * (position.y() - getY0(track));
        return Math.atan2(x, y);
    }

    public static double getX0(Track track) {
        return -1 * getDoca(track) * Math.sin(getPhi0(track));
    }

    public static double getR(Track track) {
        return 1.0 / track.getTrackStates().get(0).getOmega();
    }

    public static double getY0(Track track) {
        return getDoca(track) * Math.cos(getPhi0(track));
    }

    public static double getDoca(Track track) {
        return track.getTrackStates().get(0).getD0();
    }

    public static double getPhi0(Track track) {
        return track.getTrackStates().get(0).getPhi();
    }

    public static double getZ0(Track track) {
        return track.getTrackStates().get(0).getZ0();
    }

    public static double getTanLambda(Track track) {
        return track.getTrackStates().get(0).getTanLambda();
    }

    public static double getSinTheta(Track track) {
        return 1 / Math.sqrt(1 + Math.pow(getTanLambda(track), 2));
    }

    public static double getCosTheta(Track track) {
        return getTanLambda(track) / Math.sqrt(1 + Math.pow(getTanLambda(track), 2));
    }

    // ==========================================================================

    /**
     * Calculate the point of interception between the helix and a plane in space. Uses an iterative procedure.
     * This function makes assumptions on the sign and convecntion of the B-field. Be careful.
     * @param helfit - helix
     * @param unit_vec_normal_to_plane - unit vector normal to the plane
     * @param point_on_plane - point on the plane
     * @param bfield - magnetic field value
     * @return point at intercept
     */
    public static Hep3Vector getHelixPlaneIntercept(HelicalTrackFit helfit, Hep3Vector unit_vec_normal_to_plane, Hep3Vector point_on_plane, double bfield) {
        boolean debug = false;
        //Hep3Vector B = new BasicHep3Vector(0, 0, -1);
        //WTrack wtrack = new WTrack(helfit, -1.0*bfield); //
        Hep3Vector B = new BasicHep3Vector(0, 0, 1);
        WTrack wtrack = new WTrack(helfit, bfield); //
        if (debug)
            System.out.printf("getHelixPlaneIntercept:find intercept between plane defined by point on plane %s, unit vec %s, bfield %.3f, h=%s and WTrack \n%s \n", point_on_plane.toString(), unit_vec_normal_to_plane.toString(), bfield, B.toString(), wtrack.toString());
        Hep3Vector intercept_point = wtrack.getHelixAndPlaneIntercept(point_on_plane, unit_vec_normal_to_plane, B);
        if (debug)
            System.out.printf("getHelixPlaneIntercept: found intercept point at %s\n", intercept_point.toString());
        return intercept_point;
    }

    /**
     * Calculate the point of interception between the helix and a plane in space. Uses an
     * iterative procedure.
     * @param helfit - helix
     * @param strip - strip cluster that will define the plane
     * @param bfield - magnetic field value
     * @return point at intercept
     */
    public static Hep3Vector getHelixPlaneIntercept(HelicalTrackFit helfit, HelicalTrackStripGbl strip, double bfield) {
        Hep3Vector point_on_plane = strip.origin();
        Hep3Vector unit_vec_normal_to_plane = VecOp.cross(strip.u(), strip.v());// strip.w();
        Hep3Vector intercept_point = getHelixPlaneIntercept(helfit, unit_vec_normal_to_plane, point_on_plane, bfield);
        return intercept_point;
    }

    /*
     * Calculates the point on the helix in the x-y plane at the intercept with plane The normal of
     * the plane is in the same x-y plane as the circle.
     * @param helix
     * @param vector normal to plane
     * @param origin of plane
     * @return point in the x-y plane of the intercept
     */
    public Hep3Vector getHelixXPlaneIntercept(HelicalTrackFit helix, Hep3Vector w, Hep3Vector origin) {
        throw new RuntimeException("this function is not working properly; don't use it");

        // FInd the intercept point x_int,y_int, between the circle and sensor, which becomes a
        // line in the x-y plane in this case.
        // y_int = k*x_int + m
        // R^2 = (y_int-y_c)^2 + (x_int-x_c)^2
        // solve for x_int

    }

    /**
     * Get position of a track extrapolated to the HARP in the HPS test run 2012
     * @param track
     * @return position at HARP
     */
    public static Hep3Vector getTrackPositionAtHarp(Track track) {
        return extrapolateTrack(track, BeamlineConstants.HARP_POSITION_TESTRUN);
    }

    /**
     * Get position of a track extrapolated to the ECAL face in the HPS test run 2012
     * @param track
     * @return position at ECAL
     */
    public static Hep3Vector getTrackPositionAtEcal(Track track) {
        return extrapolateTrack(track, BeamlineConstants.ECAL_FACE);
    }

    /**
     * Extrapolate track to given position.
     * @param helix - to be extrapolated
     * @param track - position along the x-axis of the helix in lcsim coordiantes
     * @return
     */
    public static Hep3Vector extrapolateTrack(Track track, double z) {

        Hep3Vector trackPosition = null;
        double dz = 0;
        if (z >= BeamlineConstants.DIPOLE_EDGE_TESTRUN) {
            trackPosition = extrapolateHelixToXPlane(track, BeamlineConstants.DIPOLE_EDGE_TESTRUN);
            dz = z - BeamlineConstants.DIPOLE_EDGE_TESTRUN;
        } else if (z <= BeamlineConstants.DIPOLE_EDGELOW_TESTRUN) {
            trackPosition = extrapolateHelixToXPlane(track, BeamlineConstants.DIPOLE_EDGELOW_TESTRUN);
            dz = z - trackPosition.x();
        } else {
            Hep3Vector detVecTracking = extrapolateHelixToXPlane(track, z);
            // System.out.printf("detVec %s\n", detVecTracking.toString());
            return new BasicHep3Vector(detVecTracking.y(), detVecTracking.z(), detVecTracking.x());
        }

        // Get the track azimuthal angle
        double phi = getPhi(track, trackPosition);

        // Find the distance to the point of interest
        double r = dz / (getSinTheta(track) * Math.cos(phi));
        double dx = r * getSinTheta(track) * Math.sin(phi);
        double dy = r * getCosTheta(track);

        // Find the track position at the point of interest
        double x = trackPosition.y() + dx;
        double y = trackPosition.z() + dy;

        return new BasicHep3Vector(x, y, z);
    }

    /**
     * Extrapolate helix to given position
     * @param helix - to be extrapolated
     * @param z - position along the x-axis of the helix in lcsim coordiantes
     * @return
     */
    public static Hep3Vector extrapolateTrack(HelicalTrackFit helix, double z) {
        SeedTrack trk = new SeedTrack();
        // bfield = Math.abs((detector.getFieldMap().getField(new BasicHep3Vector(0, 0, 0)).y()));
        double bfield = 0.;
        // Here we aren't really using anything related to momentum so B-field is not important
        trk.setTrackParameters(helix.parameters(), bfield); // Sets first TrackState.
        trk.setCovarianceMatrix(helix.covariance()); // Modifies first TrackState.
        trk.setChisq(helix.chisqtot());
        trk.setNDF(helix.ndf()[0] + helix.ndf()[1]);
        return TrackUtils.extrapolateTrack(trk, z);
    }

    /**
     * @param helix input helix object
     * @param origin of the plane to intercept
     * @param normal of the plane to intercept
     * @param eps criteria on the distance to the plane before stopping iteration
     * @return position in space at the intercept of the plane
     */
    public static Hep3Vector getHelixPlanePositionIter(HelicalTrackFit helix, Hep3Vector origin, Hep3Vector normal, double eps) {
        boolean debug = false;
        if (debug) {
            System.out.printf("--- getHelixPlanePositionIter ---\n");
            System.out.printf("Target origin [%.10f %.10f %.10f] normal [%.10f %.10f %.10f]\n", origin.x(), origin.y(), origin.z(), normal.x(), normal.y(), normal.z());
            System.out.printf("%.10f %.10f %.10f %.10f %.10f\n", helix.dca(), helix.z0(), helix.phi0(), helix.slope(), helix.R());
        }
        double x = origin.x();
        double d = 9999.9;
        double dx = 0.0;
        int nIter = 0;
        Hep3Vector pos = null;
        while (Math.abs(d) > eps && nIter < 50) {
            // Calculate position on helix at x
            pos = getHelixPosAtX(helix, x + dx);
            // Check if we are on the plane
            d = VecOp.dot(VecOp.sub(pos, origin), normal);
            dx += -1.0 * d / 2.0;
            if (debug)
                System.out.printf("%d d %.10f pos [%.10f %.10f %.10f] dx %.10f\n", nIter, d, pos.x(), pos.y(), pos.z(), dx);
            nIter += 1;
        }
        return pos;
    }

    /*
     * Calculates the point on the helix at a given point along the x-axis The normal of the plane
     * is in the same x-y plane as the circle.
     * @param helix
     * @param x point along x-axis
     * @return point on helix at x-coordinate
     */
    private static Hep3Vector getHelixPosAtX(HelicalTrackFit helix, double x) {
        // double C = (double)Math.round(helix.curvature()*1000000)/1000000;
        // double R = 1.0/C;
        double R = helix.R();
        double dca = helix.dca();
        double z0 = helix.z0();
        double phi0 = helix.phi0();
        double slope = helix.slope();
        // System.out.printf("%.10f %.10f %.10f %.10f %.10f\n",dca,z0,phi0,slope,R);

        double xc = (R - dca) * Math.sin(phi0);
        double sinPhi = (xc - x) / R;
        double phi_at_x = Math.asin(sinPhi);
        double dphi_at_x = phi_at_x - phi0;
        if (dphi_at_x > Math.PI)
            dphi_at_x -= 2.0 * Math.PI;
        if (dphi_at_x < -Math.PI)
            dphi_at_x += 2.0 * Math.PI;
        double s_at_x = -1.0 * dphi_at_x * R;
        double y = dca * Math.cos(phi0) - R * Math.cos(phi0) + R * Math.cos(phi_at_x);
        double z = z0 + s_at_x * slope;
        BasicHep3Vector pos = new BasicHep3Vector(x, y, z);
        // System.out.printf("pos %s xc %f phi_at_x %f dphi_at_x %f s_at_x %f\n",
        // pos.toString(),xc,phi_at_x,dphi_at_x,s_at_x);
        Hep3Vector posXCheck = TrackUtils.extrapolateHelixToXPlane(helix, x);
        if (VecOp.sub(pos, posXCheck).magnitude() > 0.0000001) {
            throw new RuntimeException(String.format("ERROR the helix propagation equations do not agree? (%f,%f,%f) vs (%f,%f,%f) in HelixUtils", pos.x(), pos.y(), pos.z(), posXCheck.x(), posXCheck.y(), posXCheck.z()));
        }
        return pos;
    }

    /**
        *
        */
    public static double findTriangleArea(double x0, double y0, double x1, double y1, double x2, double y2) {
        return .5 * (x1 * y2 - y1 * x2 - x0 * y2 + y0 * x2 + x0 * y1 - y0 * x1);
    }

    /**
        *
        */
    public static boolean sensorContainsTrack(Hep3Vector trackPosition, SiSensor sensor) {
        boolean debug = false;
        ITransform3D localToGlobal = sensor.getGeometry().getLocalToGlobal();

        Box sensorSolid = (Box) sensor.getGeometry().getLogicalVolume().getSolid();
        Polygon3D sensorFace = sensorSolid.getFacesNormalTo(new BasicHep3Vector(0, 0, 1)).get(0);
        if (debug) {
            System.out.println("sensorContainsTrack:  Track Position: " + trackPosition.toString());
        }

        List<Point3D> vertices = new ArrayList<Point3D>();
        for (int index = 0; index < 4; index++) {
            vertices.add(new Point3D());
        }
        for (Point3D vertex : sensorFace.getVertices()) {
            if (vertex.y() < 0 && vertex.x() > 0) {
                localToGlobal.transform(vertex);
                // vertices.set(0, new Point3D(vertex.y() + sensorPos.x(), vertex.x() +
                // sensorPos.y(), vertex.z() + sensorPos.z()));
                vertices.set(0, new Point3D(vertex.x(), vertex.y(), vertex.z()));
                if (debug) {
                    System.out.println("sensorContainsTrack:  Vertex 1 Position: " + vertices.get(0).toString());
                    // System.out.println("sensorContainsTrack:  Transformed Vertex 1 Position: " +
                    // localToGlobal.transformed(vertex).toString());
                }
            } else if (vertex.y() > 0 && vertex.x() > 0) {
                localToGlobal.transform(vertex);
                // vertices.set(1, new Point3D(vertex.y() + sensorPos.x(), vertex.x() +
                // sensorPos.y(), vertex.z() + sensorPos.z()));
                vertices.set(1, new Point3D(vertex.x(), vertex.y(), vertex.z()));
                if (debug) {
                    System.out.println("sensorContainsTrack:  Vertex 2 Position: " + vertices.get(1).toString());
                    // System.out.println("sensorContainsTrack:  Transformed Vertex 2 Position: " +
                    // localToGlobal.transformed(vertex).toString());
                }
            } else if (vertex.y() > 0 && vertex.x() < 0) {
                localToGlobal.transform(vertex);
                // vertices.set(2, new Point3D(vertex.y() + sensorPos.x(), vertex.x() +
                // sensorPos.y(), vertex.z() + sensorPos.z()));
                vertices.set(2, new Point3D(vertex.x(), vertex.y(), vertex.z()));
                if (debug) {
                    System.out.println("sensorContainsTrack:  Vertex 3 Position: " + vertices.get(2).toString());
                    // System.out.println("sensorContainsTrack:  Transformed Vertex 3 Position: " +
                    // localToGlobal.transformed(vertex).toString());
                }
            } else if (vertex.y() < 0 && vertex.x() < 0) {
                localToGlobal.transform(vertex);
                // vertices.set(3, new Point3D(vertex.y() + sensorPos.x(), vertex.x() +
                // sensorPos.y(), vertex.z() + sensorPos.z()));
                vertices.set(3, new Point3D(vertex.x(), vertex.y(), vertex.z()));
                if (debug) {
                    System.out.println("sensorContainsTrack:  Vertex 4 Position: " + vertices.get(3).toString());
                    // System.out.println("sensorContainsTrack:  Transformed Vertex 4 Position: " +
                    // localToGlobal.transformed(vertex).toString());
                }
            }
        }

        double area1 = TrackUtils.findTriangleArea(vertices.get(0).x(), vertices.get(0).y(), vertices.get(1).x(), vertices.get(1).y(), trackPosition.y(), trackPosition.z());
        double area2 = TrackUtils.findTriangleArea(vertices.get(1).x(), vertices.get(1).y(), vertices.get(2).x(), vertices.get(2).y(), trackPosition.y(), trackPosition.z());
        double area3 = TrackUtils.findTriangleArea(vertices.get(2).x(), vertices.get(2).y(), vertices.get(3).x(), vertices.get(3).y(), trackPosition.y(), trackPosition.z());
        double area4 = TrackUtils.findTriangleArea(vertices.get(3).x(), vertices.get(3).y(), vertices.get(0).x(), vertices.get(0).y(), trackPosition.y(), trackPosition.z());

        if ((area1 > 0 && area2 > 0 && area3 > 0 && area4 > 0) || (area1 < 0 && area2 < 0 && area3 < 0 && area4 < 0))
            return true;

        return false;
    }

    public static Map<String, Double> calculateTrackHitResidual(HelicalTrackHit hth, HelicalTrackFit track, boolean includeMS) {

        boolean debug = false;
        Map<String, Double> residuals = new HashMap<String, Double>();

        Map<HelicalTrackHit, MultipleScatter> msmap = track.ScatterMap();
        double msdrphi = 0;
        double msdz = 0;

        if (includeMS) {
            msdrphi = msmap.get(hth).drphi();
            msdz = msmap.get(hth).dz();
        }

        // Calculate the residuals that are being used in the track fit

        // Start with the bendplane y
        double drphi_res = hth.drphi();
        double wrphi = Math.sqrt(drphi_res * drphi_res + msdrphi * msdrphi);
        // This is the normal way to get s
        double s_wrong = track.PathMap().get(hth);
        // This is how I do it with HelicalTrackFits
        double s = HelixUtils.PathToXPlane(track, hth.x(), 0, 0).get(0);
        // System.out.printf("x %f s %f smap %f\n",hth.x(),s,s_wrong);
        if (Double.isNaN(s)) {
            double xc = track.xc();
            double RC = track.R();
            System.out.printf("calculateTrackHitResidual: s is NaN. p=%.3f RC=%.3f, x=%.3f, xc=%.3f\n", track.p(-0.491), RC, hth.x(), xc);
            return residuals;
        }

        Hep3Vector posOnHelix = HelixUtils.PointOnHelix(track, s);
        double resy = hth.y() - posOnHelix.y();
        double erry = includeMS ? wrphi : drphi_res;

        // Now the residual for the "measurement" direction z
        double resz = hth.z() - posOnHelix.z();
        double dz_res = HitUtils.zres(hth, msmap, track);
        double dz_res2 = hth.getCorrectedCovMatrix().diagonal(2);

        if (Double.isNaN(resy)) {
            System.out.printf("calculateTrackHitResidual: resy is NaN. hit at %s posOnHelix=%s path=%.3f wrong_path=%.3f helix:\n%s\n", hth.getCorrectedPosition().toString(), posOnHelix.toString(), s, s_wrong, track.toString());
            return residuals;
        }

        residuals.put("resy", resy);
        residuals.put("erry", erry);
        residuals.put("drphi", drphi_res);
        residuals.put("msdrphi", msdrphi);

        residuals.put("resz", resz);
        residuals.put("errz", dz_res);
        residuals.put("dz_res", Math.sqrt(dz_res2));
        residuals.put("msdz", msdz);

        if (debug) {
            System.out.printf("calculateTrackHitResidual: HTH hit at (%f,%f,%f)\n", hth.x(), hth.y(), hth.z());
            System.out.printf("calculateTrackHitResidual: helix params d0=%f phi0=%f R=%f z0=%f slope=%f chi2=%f/%f chi2tot=%f\n", track.dca(), track.phi0(), track.R(), track.z0(), track.slope(), track.chisq()[0], track.chisq()[1], track.chisqtot());
            System.out.printf("calculateTrackHitResidual: => resz=%f resy=%f at s=%f\n", resz, resy, s);
            // System.out.printf("calculateTrackHitResidual: resy=%f eresy=%f drphi=%f msdrphi=%f \n",resy,erry,drphi_res,msdrphi);
            // System.out.printf("calculateTrackHitResidual: resz=%f eresz=%f dz_res=%f msdz=%f \n",resz,dz_res,Math.sqrt(dz_res2),msdz);
        }

        return residuals;
    }

    public static Map<String, Double> calculateLocalTrackHitResiduals(Track track, HelicalTrackHit hth, HelicalTrackStripGbl strip, double bFieldInZ) {

        SeedTrack st = (SeedTrack) track;
        SeedCandidate seed = st.getSeedCandidate();
        HelicalTrackFit _trk = seed.getHelix();
        Map<HelicalTrackHit, MultipleScatter> msmap = seed.getMSMap();
        double msdrdphi = msmap.get(hth).drphi();
        double msdz = msmap.get(hth).dz();
        return calculateLocalTrackHitResiduals(_trk, strip, msdrdphi, msdz, bFieldInZ);
    }

    public static Map<String, Double> calculateLocalTrackHitResiduals(HelicalTrackFit _trk, HelicalTrackStripGbl strip, double msdrdphi, double msdz, double bFieldInZ) {

        boolean debug = false;
        boolean includeMS = true;

        Hep3Vector u = strip.u();
        Hep3Vector corigin = strip.origin();

        // Find interception with plane that the strips belongs to
        Hep3Vector trkpos = TrackUtils.getHelixPlaneIntercept(_trk, strip, bFieldInZ);

        if (debug) {
            System.out.printf("calculateLocalTrackHitResiduals: found interception point at %s \n", trkpos.toString());
        }

        if (Double.isNaN(trkpos.x()) || Double.isNaN(trkpos.y()) || Double.isNaN(trkpos.z())) {
            System.out.printf("calculateLocalTrackHitResiduals: failed to get interception point (%s) \n", trkpos.toString());
            System.out.printf("calculateLocalTrackHitResiduals: track params\n%s\n", _trk.toString());
            System.out.printf("calculateLocalTrackHitResiduals: track pT=%.3f chi2=[%.3f][%.3f] \n", _trk.pT(bFieldInZ), _trk.chisq()[0], _trk.chisq()[1]);
            trkpos = TrackUtils.getHelixPlaneIntercept(_trk, strip, bFieldInZ);
            System.exit(1);
        }

        double xint = trkpos.x();
        double phi0 = _trk.phi0();
        double R = _trk.R();
        double s = HelixUtils.PathToXPlane(_trk, xint, 0, 0).get(0);
        double phi = -s / R + phi0;

        Hep3Vector mserr = new BasicHep3Vector(msdrdphi * Math.sin(phi), msdrdphi * Math.sin(phi), msdz);
        double msuError = VecOp.dot(mserr, u);

        Hep3Vector vdiffTrk = VecOp.sub(trkpos, corigin);
        TrackerHitUtils thu = new TrackerHitUtils(debug);
        Hep3Matrix trkToStrip = thu.getTrackToStripRotation(strip.getStrip());
        Hep3Vector vdiff = VecOp.mult(trkToStrip, vdiffTrk);

        double umc = vdiff.x();
        double vmc = vdiff.y();
        double wmc = vdiff.z();
        double umeas = strip.umeas();
        double uError = strip.du();
        double vmeas = 0;
        double vError = (strip.vmax() - strip.vmin()) / Math.sqrt(12);
        double wmeas = 0;
        double wError = 10.0 / Math.sqrt(12); // 0.001;

        Map<String, Double> res = new HashMap<String, Double>();
        res.put("ures", umeas - umc);
        res.put("ureserr", includeMS ? Math.sqrt(uError * uError + msuError * msuError) : uError);
        res.put("vres", vmeas - vmc);
        res.put("vreserr", vError);
        res.put("wres", wmeas - wmc);
        res.put("wreserr", wError);

        res.put("vdiffTrky", vdiffTrk.y());

        return res;
    }

    public static int[] getHitsInTopBottom(Track track) {
        int n[] = { 0, 0 };
        List<TrackerHit> hitsOnTrack = track.getTrackerHits();
        for (TrackerHit hit : hitsOnTrack) {
            HelicalTrackHit hth = (HelicalTrackHit) hit;
            //===> if (SvtUtils.getInstance().isTopLayer((SiSensor) ((RawTrackerHit) hth.getRawHits().get(0)).getDetectorElement())) {
            HpsSiSensor sensor = ((HpsSiSensor) ((RawTrackerHit) hth.getRawHits().get(0)).getDetectorElement());
            if(sensor.isTopLayer()){
                n[0] = n[0] + 1;
            } else {
                n[1] = n[1] + 1;
            }
        }
        return n;
    }

    public static boolean isTopTrack(Track track, int minhits) {
        return isTopOrBottomTrack(track, minhits) == 1 ? true : false;
    }

    public static boolean isBottomTrack(Track track, int minhits) {
        return isTopOrBottomTrack(track, minhits) == 0 ? true : false;
    }

    public static int isTopOrBottomTrack(Track track, int minhits) {
        int nhits[] = getHitsInTopBottom(track);
        if (nhits[0] >= minhits && nhits[1] == 0) {
            return 1;
        } else if (nhits[1] >= minhits && nhits[0] == 0) {
            return 0;
        } else {
            return -1;
        }
    }

    public static boolean hasTopBotHit(Track track) {
        int nhits[] = getHitsInTopBottom(track);
        if (nhits[0] > 0 && nhits[1] > 0)
            return true;
        else
            return false;
    }

    public static boolean isSharedHit(TrackerHit hit, List<Track> othertracks) {
        HelicalTrackHit hth = (HelicalTrackHit) hit;
        for (Track track : othertracks) {
            List<TrackerHit> hitsOnTrack = track.getTrackerHits();
            for (TrackerHit loop_hit : hitsOnTrack) {
                HelicalTrackHit loop_hth = (HelicalTrackHit) loop_hit;
                if (hth.equals(loop_hth)) {
                    // System.out.printf("share hit at layer %d at %s (%s) with track w/ chi2=%f\n",hth.Layer(),hth.getCorrectedPosition().toString(),loop_hth.getCorrectedPosition().toString(),track.getChi2());
                    return true;
                }
            }
        }
        return false;
    }

    public static int numberOfSharedHits(Track track, List<Track> tracklist) {
        List<Track> tracks = new ArrayList<Track>();
        // System.out.printf("%d tracks in event\n",tracklist.size());
        // System.out.printf("look for another track with chi2=%f and px=%f \n",track.getChi2(),track.getTrackStates().get(0).getMomentum()[0]);
        for (Track t : tracklist) {
            // System.out.printf("add track with chi2=%f and px=%f ?\n",t.getChi2(),t.getTrackStates().get(0).getMomentum()[0]);
            if (t.equals(track)) {
                // System.out.printf("NOPE\n");
                continue;
            }
            // System.out.printf("YEPP\n");
            tracks.add(t);
        }
        List<TrackerHit> hitsOnTrack = track.getTrackerHits();
        int n_shared = 0;
        for (TrackerHit hit : hitsOnTrack) {
            if (isSharedHit(hit, tracks)) {
                ++n_shared;
            }
        }
        return n_shared;
    }

    public static boolean hasSharedHits(Track track, List<Track> tracklist) {
        return numberOfSharedHits(track, tracklist) == 0 ? false : true;
    }

    public static void cut(int cuts[], EventQuality.Cut bit) {
        cuts[0] = cuts[0] | (1 << bit.getValue());
    }

    public static boolean isGoodTrack(Track track, List<Track> tracklist, EventQuality.Quality trk_quality) {
        int cuts = passTrackSelections(track, tracklist, trk_quality);
        return cuts == 0 ? true : false;
    }

    public static int passTrackSelections(Track track, List<Track> tracklist, EventQuality.Quality trk_quality) {
        int cuts[] = { 0 };
        if(trk_quality.compareTo(Quality.NONE) != 0) {
            if (track.getTrackStates().get(0).getMomentum()[0] < EventQuality.instance().getCutValue(EventQuality.Cut.PZ, trk_quality))
                cut(cuts, EventQuality.Cut.PZ);
            if (track.getChi2() >= EventQuality.instance().getCutValue(EventQuality.Cut.CHI2, trk_quality))
                cut(cuts, EventQuality.Cut.CHI2);
            if (numberOfSharedHits(track, tracklist) > ((int) Math.round(EventQuality.instance().getCutValue(EventQuality.Cut.SHAREDHIT, trk_quality))))
                cut(cuts, EventQuality.Cut.SHAREDHIT);
            if (hasTopBotHit(track))
                cut(cuts, EventQuality.Cut.TOPBOTHIT);
            if (track.getTrackerHits().size() < ((int) Math.round(EventQuality.instance().getCutValue(EventQuality.Cut.NHITS, trk_quality))))
                cut(cuts, EventQuality.Cut.NHITS);
        }
        return cuts[0];
    }

    public static boolean isTopTrack(HelicalTrackFit htf) {
        return htf.slope() > 0;
    }

    public static boolean isBottomTrack(HelicalTrackFit htf) {
        return !isTopTrack(htf);
    }

    /**
     * Transform MCParticle into a Helix object. Note that it produces the helix parameters at
     * nominal x=0 and assumes that there is no field at x<0
     * 
     * @param mcp MC particle to be transformed
     * @return helix object based on the MC particle
     */
    public static HelicalTrackFit getHTF(MCParticle mcp, double Bz) {
        boolean debug = true;
        if(debug) {
            System.out.printf("getHTF\n");
            System.out.printf("mcp org %s mc p %s\n",mcp.getOrigin().toString(),mcp.getMomentum().toString());
        }
        Hep3Vector org = CoordinateTransformations.transformVectorToTracking(mcp.getOrigin());
        Hep3Vector p = CoordinateTransformations.transformVectorToTracking(mcp.getMomentum());

        if(debug) {
            System.out.printf("mcp org %s mc p %s (trans)\n",org.toString(),p.toString());
        }

        // Move to x=0 if needed
        double targetX = BeamlineConstants.DIPOLE_EDGELOW_TESTRUN;
        if (org.x() < targetX) {
            double dydx = p.y() / p.x();
            double dzdx = p.z() / p.x();
            double delta_x = targetX - org.x();
            double y = delta_x * dydx + org.y();
            double z = delta_x * dzdx + org.z();
            double x = org.x() + delta_x;
            if (Math.abs(x - targetX) > 1e-8)
                throw new RuntimeException("Error: origin is not zero!");
            org = new BasicHep3Vector(x, y, z);
            // System.out.printf("org %s p %s -> org %s\n",
            // old.toString(),p.toString(),org.toString());
        }

        if(debug) {
            System.out.printf("mcp org %s mc p %s (trans2)\n",org.toString(),p.toString());
        }

        HelixParamCalculator helixParamCalculator = new HelixParamCalculator(p, org, -1 * ((int) mcp.getCharge()), Bz);
        double par[] = new double[5];
        par[HelicalTrackFit.dcaIndex] = helixParamCalculator.getDCA();
        par[HelicalTrackFit.slopeIndex] = helixParamCalculator.getSlopeSZPlane();
        par[HelicalTrackFit.phi0Index] = helixParamCalculator.getPhi0();
        par[HelicalTrackFit.curvatureIndex] = 1.0 / helixParamCalculator.getRadius();
        par[HelicalTrackFit.z0Index] = helixParamCalculator.getZ0();
        HelicalTrackFit htf = getHTF(par);
         System.out.printf("d0 %f z0 %f R %f phi %f lambda %s\n",
         htf.dca(),htf.z0(),htf.R(),htf.phi0(),htf.slope() );
        return htf;
    }

    public static HelicalTrackFit getHTF(Track track) {
        if (track.getClass().isInstance(SeedTrack.class)) {
            return ((SeedTrack) track).getSeedCandidate().getHelix();
        } else {
            return getHTF(track.getTrackStates().get(0).getParameters());
        }
    }

    public static HelicalTrackFit getHTF(double par[]) {
        // need to have matrix that makes sense? Really?
        SymmetricMatrix cov = new SymmetricMatrix(5);
        for (int i = 0; i < cov.getNRows(); ++i)
            cov.setElement(i, i, 1.);
        HelicalTrackFit htf = new HelicalTrackFit(par, cov, new double[2], new int[2], null, null);
        return htf;
    }

    public static StraightLineTrack findSLTAtZ(Track trk1, double zVal, boolean useFringe) {
        SeedTrack s1 = (SeedTrack) trk1;
        HelicalTrackFit htf1 = s1.getSeedCandidate().getHelix();
        HPSTrack hpstrk1 = new HPSTrack(htf1);
        Hep3Vector pos1;
        if (useFringe) {
            // broken because you need ot provide the Field Map to get this...
//            pos1 = hpstrk1.getPositionAtZMap(100.0, zVal, 5.0)[0];            
        } else {
            pos1 = TrackUtils.extrapolateTrack(trk1, zVal);
        }
        // System.out.printf("%s: Position1 at edge of fringe %s\n",this.getClass().getSimpleName(),pos1.toString());
        Helix traj = (Helix) hpstrk1.getTrajectory();
        if (traj == null) {
            SpacePoint r0 = new SpacePoint(HelixUtils.PointOnHelix(htf1, 0));
            traj = new Helix(r0, htf1.R(), htf1.phi0(), Math.atan(htf1.slope()));
        }
        HelixConverter converter = new HelixConverter(0.);
        StraightLineTrack slt1 = converter.Convert(traj);
        // System.out.printf("%s: straight line track: x0=%f,y0=%f,z0=%f dz/dx=%f dydx=%f targetY=%f targetZ=%f \n",this.getClass().getSimpleName(),slt1.x0(),slt1.y0(),slt1.z0(),slt1.dzdx(),slt1.dydx(),slt1.TargetYZ()[0],slt1.TargetYZ()[1]);
        return slt1;
    }
    
    
    
    public static MCParticle getMatchedTruthParticle(Track track) {
        boolean debug = false;
        
        Map<MCParticle,Integer> particlesOnTrack = new HashMap<MCParticle,Integer>();
        
        if(debug) System.out.printf("getMatchedTruthParticle: getmatched mc particle from %d tracker hits on the track \n",track.getTrackerHits().size());
        
        
        for(TrackerHit hit : track.getTrackerHits()) {
            List<MCParticle> mcps = ((HelicalTrackHit)hit).getMCParticles();
            if(mcps==null) {
                System.out.printf("getMatchedTruthParticle: warning, this hit (layer %d pos=%s) has no mc particles.\n",((HelicalTrackHit)hit).Layer(),((HelicalTrackHit)hit).getCorrectedPosition().toString());
            } 
            else {
                if( debug ) System.out.printf("getMatchedTruthParticle: this hit (layer %d pos=%s) has %d mc particles.\n",((HelicalTrackHit)hit).Layer(),((HelicalTrackHit)hit).getCorrectedPosition().toString(),mcps.size());
                for(MCParticle mcp : mcps) {
                    if( !particlesOnTrack.containsKey(mcp) ) {
                        particlesOnTrack.put(mcp, 0);
                    }
                    int c = particlesOnTrack.get(mcp);
                    particlesOnTrack.put(mcp, c+1);
                }
            }
        }
        if(debug) {
            System.out.printf("Track p=[ %f, %f, %f] \n",track.getTrackStates().get(0).getMomentum()[0],track.getTrackStates().get(0).getMomentum()[1],track.getTrackStates().get(0).getMomentum()[1]);
            System.out.printf("Found %d particles\n",particlesOnTrack.size());
            for(Map.Entry<MCParticle, Integer> entry : particlesOnTrack.entrySet()) {
                System.out.printf("%d hits assigned to %d p=%s \n",entry.getValue(),entry.getKey().getPDGID(),entry.getKey().getMomentum().toString());
            }
        }
        Map.Entry<MCParticle,Integer> maxEntry = null;
        for(Map.Entry<MCParticle,Integer> entry : particlesOnTrack.entrySet()) {
            if ( maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0 ) maxEntry = entry;
            //if ( maxEntry != null ) {
            //    if(entry.getValue().compareTo(maxEntry.getValue()) < 0) continue;
            //}
            //maxEntry = entry;
        }
        if(debug) {
            if (maxEntry != null ) {
                System.out.printf("Matched particle with pdgId=%d and mom %s to track with charge %d and momentum [%f %f %f]\n",
                        maxEntry.getKey().getPDGID(),maxEntry.getKey().getMomentum().toString(),
                        track.getCharge(),track.getTrackStates().get(0).getMomentum()[0],track.getTrackStates().get(0).getMomentum()[1],track.getTrackStates().get(0).getMomentum()[2]);
            } else {
                System.out.printf("No truth particle found on this track\n");
            }
        }
        return maxEntry == null ? null : maxEntry.getKey();
    }
    

}
