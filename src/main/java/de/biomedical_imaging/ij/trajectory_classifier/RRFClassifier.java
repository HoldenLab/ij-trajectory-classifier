package de.biomedical_imaging.ij.trajectory_classifier;

import java.util.ArrayList;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import de.biomedical_imaging.ij.trajectory_classifier.r.StartRserve;
import de.biomedical_imaging.traJ.Trajectory;
import de.biomedical_imaging.traJ.features.Asymmetry2Feature;
import de.biomedical_imaging.traJ.features.AsymmetryFeature;
import de.biomedical_imaging.traJ.features.EfficiencyFeature;
import de.biomedical_imaging.traJ.features.ElongationFeature;
import de.biomedical_imaging.traJ.features.FractalDimensionFeature;
import de.biomedical_imaging.traJ.features.GaussianityFeauture;
import de.biomedical_imaging.traJ.features.KurtosisFeature;
import de.biomedical_imaging.traJ.features.MeanSquaredDisplacmentCurvature;
import de.biomedical_imaging.traJ.features.PowerLawFeature;
import de.biomedical_imaging.traJ.features.ShortTimeLongTimeDiffusioncoefficentRatio;
import de.biomedical_imaging.traJ.features.SkewnessFeature;
import de.biomedical_imaging.traJ.features.SplineCurveDynamicsFeature;
import de.biomedical_imaging.traJ.features.SplineCurveDynamicsMSDRatioFeature;
import de.biomedical_imaging.traJ.features.StandardDeviationDirectionFeature;
import de.biomedical_imaging.traJ.features.StraightnessFeature;
import de.biomedical_imaging.traJ.features.TrappedProbabilityFeature;

public class RRFClassifier extends AbstractClassifier {
	private double fps;
	public static boolean chatty= false;
	private RConnection c = null;
	int cores;
	public RRFClassifier(double fps) {
		this.fps = fps;
	}
	
	@Override
	public void start() {
	
		if(!StartRserve.isRserveRunning()){
			StartRserve.launchRserve("R");
			c = StartRserve.c;
		}else{
			try {
				c = new RConnection();
			} catch (RserveException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			c.voidEval("library(\"missForest\")");
			c.voidEval("try(library(\"randomForest\"))");
			c.voidEval("try(library(\"doSNOW\"))");
			c.voidEval("try(library(\"foreach\"))");
			c.voidEval("library(\"doParallel\")");
			cores = Runtime.getRuntime().availableProcessors();
			c.voidEval("registerDoParallel(cores="+cores+")");
			c.voidEval("load(\"/home/thorsten/randomForestModel.RData\")");
			
			c.voidEval("cl<-makeCluster("+cores+")");
			c.voidEval("registerDoSNOW(cl)");
		} catch (RserveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		try {
			c.voidEval("stopCluster(cl)");
		} catch (RserveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		c.close();
	}
	
	@Override
	public String classify(Trajectory t) {
		ArrayList<Trajectory> tracks = new ArrayList<Trajectory>();
		tracks.add(t);
		String[] result = classify(tracks);
		return result[0];
	}
	
	
	@Override
	public String[] classify(ArrayList<Trajectory> tracks) {
		
		/*
		 * Generate Dataset
		 */
		
		int N = tracks.size();
		double[] elong = new double[N];
		double[] fd = new double[N];
		double[] msdcurvature = new double[N];
		double[] power = new double[N];
		double[] sdDir = new double[N]; 
		double[] dratio = new double[N]; 
		double[] ltStRatio = new double[N]; 
		double[] asym1 = new double[N]; 
		double[] asym2 = new double[N];
		double[] efficiency = new double[N];
		double[] kurtosis = new double[N];
		double[] skewness = new double[N];
		double[] msdratio = new double[N];
		double[] straightness = new double[N];
		double[] trappedness = new double[N];
		double[] gaussianity = new double[N];
		
		int numberOfSegmentsSplineFit = 7;
		int numberOfPointsForShortTimeLongTimeRatio = 3;
		
		/**
		 * TO DO: Parallele Datenberechnung
		 */
		System.out.println("Calc features");

		for(int i = 0; i < tracks.size(); i++){
			Trajectory t = tracks.get(i);
			int timelagForDirectionDeviationLong = t.size()/20; 
			
			ElongationFeature elongF = new ElongationFeature(t);
			elong[i] = elongF.evaluate()[0];
			if(chatty)System.out.println("Elong evaluated");
			
			FractalDimensionFeature fdF = new FractalDimensionFeature(t);
			fd[i] = fdF.evaluate()[0];
			if(chatty)System.out.println("FD evaluated");
			
			MeanSquaredDisplacmentCurvature msdCurv = new MeanSquaredDisplacmentCurvature(t);
			msdcurvature[i] = msdCurv.evaluate()[0];
			if(chatty)System.out.println("MSDCURV evaluated");
			
			PowerLawFeature pwf = new PowerLawFeature(t, 1, t.size()-1);
			power[i] = pwf.evaluate()[0];
			if(chatty)System.out.println("POWER evaluated");

			StandardDeviationDirectionFeature sdf = new StandardDeviationDirectionFeature(t, timelagForDirectionDeviationLong);
			sdDir[i] = sdf.evaluate()[0];
			if(chatty)System.out.println("SDDIR evaluated");

			SplineCurveDynamicsFeature scdf = new SplineCurveDynamicsFeature(t, numberOfSegmentsSplineFit, 1);
			double[] res = scdf.evaluate();
			dratio[i] = res[1]/res[2];
			if(chatty)System.out.println("SCDF evaluated");

			ShortTimeLongTimeDiffusioncoefficentRatio stltdf = new ShortTimeLongTimeDiffusioncoefficentRatio(t, numberOfPointsForShortTimeLongTimeRatio);
			ltStRatio[i] = stltdf.evaluate()[0];
			if(chatty)System.out.println("STLTDF evaluated");
			
			AsymmetryFeature asymf1 = new AsymmetryFeature(t);
			asym1[i] = asymf1.evaluate()[0];
			if(chatty)System.out.println("ASYM1 evaluated");
			
			Asymmetry2Feature asymf2 = new Asymmetry2Feature(t);
			asym2[i] = asymf2.evaluate()[0];
			if(chatty)System.out.println("ASYMf2 evaluated");
			
			EfficiencyFeature eff = new EfficiencyFeature(t);
			efficiency[i] = eff.evaluate()[0];
			if(chatty)System.out.println("EFF evaluated");
			
			KurtosisFeature kurtf = new KurtosisFeature(t);
			kurtosis[i] = kurtf.evaluate()[0];
			if(chatty)System.out.println("KURT evaluated");
			
			SkewnessFeature skew = new SkewnessFeature(t);
			skewness[i] = skew.evaluate()[0];
			if(chatty)System.out.println("SKEW evaluated");

			SplineCurveDynamicsMSDRatioFeature msdr = new SplineCurveDynamicsMSDRatioFeature(t, 1);
			msdratio[i] = msdr.evaluate()[0];
			if(chatty)System.out.println("MSDR evaluated");
			
			StraightnessFeature straight = new StraightnessFeature(t);
			straightness[i] = straight.evaluate()[0];
			if(chatty)System.out.println("STRAIGHT evaluated");
			
			TrappedProbabilityFeature trappf = new TrappedProbabilityFeature(t, 1/fps);
			trappedness[i] = trappf.evaluate()[0];
			if(chatty)System.out.println("TRAPP evaluated");

			GaussianityFeauture gaussf = new GaussianityFeauture(t, 1);
			gaussianity[i] = gaussf.evaluate()[0];
			if(chatty)System.out.println("GAUSS evaluated");
		}
		
		System.out.println("Calc features finished");

		/*
		 * Classify
		 */

		String[] res =null;
	//	RConnection c = StartRserve.c; 
		
		try {
			
			c.assign("elong", elong);
			c.assign("fd",fd);
			c.assign("msdcurvature",msdcurvature);
			c.assign("power", power);
			c.assign("sdDir", sdDir);
			c.assign("D.ratio", dratio);
			c.assign("LtStRatio", ltStRatio);
			c.assign("asymmetry1", asym1);
			c.assign("asymmetry2", asym2);
			c.assign("efficiency", efficiency);
			c.assign("kurtosis",kurtosis);
			c.assign("skewness", skewness);
			c.assign("msdratio", msdratio);
			c.assign("straightness", straightness);
			c.assign("trappedness", trappedness);
			c.assign("gaussianity", gaussianity);
			c.voidEval("data<-data.frame(ELONG=elong,FD=fd,MSD.C=msdcurvature,"
					+ "POWER=power,SD.DIR=sdDir,SPLINE.RATIO=D.ratio,LTST.RATIO=LtStRatio,"
					+ "ASYM1=asymmetry1,ASYM2=asymmetry2,EFFICENCY=efficiency, KURT=kurtosis,"
					+ "SKEW=skewness,MSD.R=msdratio,STRAIGHTNESS=straightness, "
					+ "TRAPPED=trappedness,GAUSS=gaussianity)");
			
		   
			c.voidEval("hlp<-missForest(data,parallelize = \"variables\")");
			
			c.voidEval("data<-hlp$ximp");
		
			
			
			
			c.voidEval("split_testing<-sort(rank(1:nrow(data))%%"+cores+") ");
			c.voidEval("pred<-foreach(i=unique(split_testing),"
					+ ".combine=c,.packages=c(\"randomForest\")) %dopar% {"
							+ "as.numeric(predict(model,newdata=data[split_testing==i,]))}");
			c.voidEval("lvl<-levels(model$y)");
			//c.voidEval("features.predict <- predict(model,data)");
			res = c.eval("lvl[pred]").asStrings();
		} catch (RserveException e) {
			System.out.println("Message: " + e.getMessage());
			e.printStackTrace();
			try {
				c.voidEval("save(data,file=\"/home/thorsten/baddata.Rdata\")");
				System.out.println("Bad data is saved...");
			} catch (RserveException e1) {
				System.out.println("NOT");
				e1.printStackTrace();
			}
			
			
			return null;
		} catch (REngineException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (REXPMismatchException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return res;
	}
	
	public static void printArr(double[] arr, String title){
		System.out.print(title+":");
		System.out.print(arr[0]);
		for(int i = 1; i < arr.length; i++){
			System.out.print(","+arr[i]);
		}
		System.out.println();
		
	}

	
	
	

}
