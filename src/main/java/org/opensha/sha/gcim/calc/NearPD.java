package org.opensha.sha.gcim.calc;

import java.util.StringTokenizer;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;
 
/**
 * <p>Title: nearPD </p>
 * <p>Description: This class finds the 'nearest' positive definite (PD) matrix of a given
 * input matrix.  Such a class is useful in the cases of an approximate correlation matrix 
 * which has individual elements that are obtained using different subsets of data
 * The class implements the method of Higham (2002) and the code has been adapted from the
 * version implemented in R.   
 * 
 *   Reference: Higham, N. J., 2002. Computing the nearest correlation matrix—a problem 
 *              from finance, IMA Journal of Numerical Analysis,  22, 329-343.
 * 
 * The main method of this class calcNearPD has been tested against a matlab impl and also
 * The impl in R.
 * 
 * <p>Copyright: Copyright (c) 2010</p>
 * <p>Company: </p>
 * @author Brendon Bradley
 * @date Feb 1, 2011
 * @version 1.0 
 */


public class NearPD {
	
	private Matrix X; //The nearest PD matrix
	
	//Solution parameters
	private boolean keepDiag;    //Whether the diagonal values of the input matrix should be kept unchanged
	private boolean doDykstra;   //Whether Dykstra update is performed
	private double eigTol;		 //Relative tolerence used for determining if eigenvalues are negative
	private double convTol;		 //Tolerence used for convergence
	private int maxit;   		 //Max number of iterations to perform
	
	//Values from solution
	private double conv;		 //convergence value
	private double normF;		 //Frobenius norm between the input marix, x, and nearest PD matrix, X
	private int iter;    		 //Number of iterations required for solution
	private double[] eigVals; 	 //The eigenValues computed
	
	
	/**
	 * This no-arg constructor sets defaults
	 */
	public NearPD() {
		setDefaults();
	};
	
	public boolean calcNearPD(Matrix x) {
		
		int n = x.getRowDimension();
		Matrix X;
		//Init local variables
		double[] diagX0 = new double[n];
		double[] d = new double[n];
		Matrix D_S = new Matrix(n,n);
		Matrix R = new Matrix(n,n);
		Matrix Y = new Matrix(n,n);
		EigenvalueDecomposition eig;
		Matrix D_plus = new Matrix(n,n);
		Matrix Q = new Matrix(n,n);
		
		if (keepDiag) {
			for (int i=0; i<n; i++) {
				diagX0[i] = x.get(i,i);
			}
		}

		X = x.copy();
		
		//Set iteration, convergence criteria
		int iter = 0;
		boolean converged = false;
		double conv = Double.POSITIVE_INFINITY;
		//Loop
		while ((iter<maxit)&!converged) {
			Y = X.copy();
			
			//Dykstra correction
			if (doDykstra) {
				R = Y.minus(D_S);
			}
			
	        //project onto PSD matrices  X_k  =  P_S (R_k)
	        if (doDykstra) {
	        	eig = R.eig();
	        } else {
	        	eig = Y.eig();
	        }
	        d = eig.getRealEigenvalues();
	        Q = eig.getV();
	        //Get the maximum eigenvalue
	        double eigMax=Double.NEGATIVE_INFINITY;
	        for (int i=0; i<n; i++) {
	        	if (d[i]>eigMax)
	        		eigMax = d[i];
	        }
	        //compute the D_plus diagonal matricies
	        for (int i=0; i<n; i++) {
	        	double d_plus = Math.max(d[i],eigTol*eigMax);
	        	D_plus.set(i,i,d_plus);
			}
	        
	        X = (Q.times(D_plus)).times(Q.transpose());
	        
	        //Update Dykstra correction
	        if (doDykstra)
	            D_S = X.minus(R);

	        //project onto symmetric and possibly 'given diag' matrices:
	        if (keepDiag) {
	        	for (int i=0; i<n; i++) {
	        		X.set(i,i, diagX0[i]);
	        	}
			}

	        //update convergence and iteration values
			conv = (Y.minus(X)).normInf() / Y.normInf();
	        iter = iter + 1;

	        //check convergence criteria
	        if (conv <= convTol)
	        	converged=true;
		}
		
		
		//Set solution local variables as globals
		this.X = X;
		this.conv = conv;
		this.normF = (x.minus(X)).normF();
		this.iter = iter;
		this.eigVals = d;
		
		
		return converged;
		
	}
	
	/**
	 * This method returns the nearest PD matrix X
	 */
	public Matrix getX() {
		return X;
	}
	
	/**
	 * This method returns the tolerence obtained in the solution
	 */
	public double getConvergedTolerence() {
		return conv;
	}
	
	/**
	 * This method returns the frobenius norm obtained in the solution
	 */
	public double getFrobNorm() {
		return normF;
	}
	
	/**
	 * This method returns the number of iterations required in the solution
	 */
	public double getIter() {
		return iter;
	}
	
	/**
	 * This method returns the eigenvalues of the matrix X (nearest PD)
	 */
	public double[] getEigVals() {
		return eigVals.clone();
	}

	/**
	 * This method gets the computed matrix X which is nearest to that input
	 * @return - the nearest PD matrix to that input
	 */
	public Matrix getNearPD() {
		return X.copy();
	}
	
	/**
	 * This method sets the default values of various parameters
	 */
	private void setDefaults() {
		keepDiag = false;
		doDykstra = true;
		eigTol = 1.e-6;
		convTol = 1.e-7;
		maxit = 100;
	}
	
	/**
	 * This method allows setting of keepDiag
	 */
	public void setKeepDiag(boolean e) {
		keepDiag = e;
	}
	/**
	 * This method allows setting of doDykstra
	 */
	public void setDoDykstra(boolean e) {
		doDykstra = e;
	}
	/**
	 * This method allows setting of convTol
	 */
	public void setConvTol(double val) {
		convTol = val;
	}
	/**
	 * This method allows setting of eigTol
	 */
	public void setEigTol(double val) {
		eigTol = val;
	}
	/**
	 * This method allows setting of maxit
	 */
	public void setMaxit(int val) {
		maxit = val;
	}
	
	/**
	 * This method tests the NearPD class for a approx correlation matrix.  This test has been implemented in Matlab
	 * for verification
	 * @param args
	 */
	public static void main(String[] args) {
		
//		int n=11;
		int n=10;
//		int n=17;
		double[][] rho = new double[n][n];
		
//		String rhoString=  
//	      "1.0000      0.9459      0.8018      0.6580      0.5526      0.4665      0.3909      0.3200      0.2486      0.1682     -0.0946     -0.1157     -0.1355      0.9652      0.2520      0.8299      0.5164 " +
//	      "0.9459      1.0000      0.7642      0.6108      0.5027      0.4173      0.3446      0.2785      0.2139      0.1432     -0.1256     -0.1358     -0.1252      0.9446      0.3705      0.8809      0.5569 " +
//	      "0.8018      0.7642      1.0000      0.8139      0.6787      0.5695      0.4745      0.3865      0.2989      0.2014     -0.1337     -0.1508     -0.1513      0.8722      0.2323      0.9336      0.4744 " +
//	      "0.6580      0.6108      0.8139      1.0000      0.8398      0.7086      0.5931      0.4850      0.3764      0.2543     -0.1279     -0.1470     -0.1526      0.7762      0.2969      0.9358      0.4703 " +
//	      "0.5526      0.5027      0.6787      0.8398      1.0000      0.8465      0.7105      0.5823      0.4529      0.3066     -0.1182     -0.1372     -0.1452      0.6840      0.4004      0.8400      0.4664 " +
//	      "0.4665      0.4173      0.5695      0.7086      0.8465      1.0000      0.8409      0.6903      0.5376      0.3643     -0.1069     -0.1249     -0.1337      0.5948      0.4537      0.7401      0.4421 " +
//	      "0.3909      0.3446      0.4745      0.5931      0.7105      0.8409      1.0000      0.8219      0.6407      0.4346     -0.0944     -0.1109     -0.1197      0.5070      0.4544      0.6354      0.4050 " +
//	      "0.3200      0.2785      0.3865      0.4850      0.5823      0.6903      0.8219      1.0000      0.7802      0.5296     -0.0807     -0.0951     -0.1033      0.4188      0.4166      0.5261      0.3618 " +
//	      "0.2486      0.2139      0.2989      0.3764      0.4529      0.5376      0.6407      0.7802      1.0000      0.6792     -0.0650     -0.0768     -0.0839      0.3265      0.3494      0.4097      0.2997 " +
//	      "0.1682      0.1432      0.2014      0.2543      0.3066      0.3643      0.4346      0.5296      0.6792      1.0000     -0.0453     -0.0537     -0.0589      0.2207      0.2495      0.2760      0.2086 " +
//	     "-0.0946     -0.1256     -0.1337     -0.1279     -0.1182     -0.1069     -0.0944     -0.0807     -0.0650     -0.0453      1.0000      0.7538      0.5695     -0.0437      0.7922     -0.0830      0.4212 " +
//	     "-0.1157     -0.1358     -0.1508     -0.1470     -0.1372     -0.1249     -0.1109     -0.0951     -0.0768     -0.0537      0.7538      1.0000      0.7648     -0.0076      0.8310     -0.0258      0.5696 " +
//	     "-0.1355     -0.1252     -0.1513     -0.1526     -0.1452     -0.1337     -0.1197     -0.1033     -0.0839     -0.0589      0.5695      0.7648      1.0000      0.0581      0.8930      0.0665      0.6871 " +
//	      "0.9652      0.9446      0.8722      0.7762      0.6840      0.5948      0.5070      0.4188      0.3265      0.2207     -0.0437     -0.0076      0.0581      1.0000      0.2931      0.8956      0.5861 " +
//	      "0.2520      0.3705      0.2323      0.2969      0.4004      0.4537      0.4544      0.4166      0.3494      0.2495      0.7922      0.8310      0.8930      0.2931      1.0000      0.3191      0.6704 " +
//	      "0.8299      0.8809      0.9336      0.9358      0.8400      0.7401      0.6354      0.5261      0.4097      0.2760     -0.0830     -0.0258      0.0665      0.8956      0.3191      1.0000      0.5372 " +
//	      "0.5164      0.5569      0.4744      0.4703      0.4664      0.4421      0.4050      0.3618      0.2997      0.2086      0.4212      0.5696      0.6871      0.5861      0.6704      0.5372      1.0000";
	    String rhoString=
	    		"1.0000      0.8646      0.5412     -0.0070     -0.4291     -0.2727      0.5448      0.4981     -0.4579     -0.4481 " +
	    		"0.8646      1.0000      0.5343     -0.0141     -0.4832     -0.4705      0.4392      0.4716     -0.3771     -0.3660 " +
	    		"0.5412      0.5343      1.0000      0.0427     -0.7999     -0.8240      0.2005      0.1926     -0.2985     -0.2684 " +
	    	   "-0.0070     -0.0141      0.0427      1.0000     -0.4071     -0.8087     -0.1396     -0.1775     -0.0304     -0.0340 " +
	    	   "-0.4291     -0.4832     -0.7999     -0.4071      1.0000      0.0594     -0.1627     -0.2604      0.2474      0.2541 " +
	    	   "-0.2727     -0.4705     -0.8240     -0.8087      0.0594      1.0000      0.2672      0.0173      0.3205      0.3749 " +
	    	    "0.5448      0.4392      0.2005     -0.1396     -0.1627      0.2672      1.0000      0.2581     -0.3109     -0.3051 " +
	    	    "0.4981      0.4716      0.1926     -0.1775     -0.2604      0.0173      0.2581      1.0000      0.2289      0.2408 " +
	    	   "-0.4579     -0.3771     -0.2985     -0.0304      0.2474      0.3205     -0.3109      0.2289      1.0000      0.8425 " +
	    	   "-0.4481     -0.3660     -0.2684     -0.0340      0.2541      0.3749     -0.3051      0.2408      0.8425      1.0000";
	    		
	    		
//		String rhoString=" 1.000 0.399 0.426 0.484 0.585 0.713 0.822 0.887 0.918 0.929 0.897 " +
//				      "0.399 1.000 0.911 0.791 0.675 0.563 0.458 0.360 0.272 0.195 0.129 " +
//				      "0.426 0.911 1.000 0.878 0.759 0.643 0.532 0.428 0.332 0.247 0.173 " +
//				      "0.484 0.791 0.878 1.000 0.878 0.759 0.643 0.532 0.428 0.332 0.247 " +
//				      "0.585 0.675 0.759 0.878 1.000 0.878 0.759 0.643 0.532 0.428 0.332 " +
//				      "0.713 0.563 0.643 0.759 0.878 1.000 0.878 0.759 0.643 0.532 0.428 " +
//				      "0.822 0.458 0.532 0.643 0.759 0.878 1.000 0.878 0.759 0.643 0.532 " +
//				      "0.887 0.360 0.428 0.532 0.643 0.759 0.878 1.000 0.878 0.759 0.643 " +
//				      "0.918 0.272 0.332 0.428 0.532 0.643 0.759 0.878 1.000 0.878 0.759 " +
//				      "0.929 0.195 0.247 0.332 0.428 0.532 0.643 0.759 0.878 1.000 0.878 " +
//				      "0.897 0.129 0.173 0.247 0.332 0.428 0.532 0.643 0.759 0.878 1.000";
		//Create string tokenizer
		StringTokenizer st = new StringTokenizer(rhoString);
		for (int i=0; i<n; i++) {
			for (int j=0; j<n; j++) {
				rho[i][j] = Double.parseDouble(st.nextToken().trim());
			}
		}
		
		Matrix rho_matrix = new Matrix(rho);
//		CholeskyDecomposition chol = rho_matrix.chol();
		CholeskyDecomposition chol = new CholeskyDecomposition(rho_matrix);
		if (!chol.isSPD())
			System.out.println("The rho matrix is not SPD");
		
		//Get the eigen values for this matrix
		EigenvalueDecomposition eig = rho_matrix.eig();
		String eigValsString = "";
		double[] eigVals = eig.getRealEigenvalues();
		for (int i=0; i<n; i++) 
			eigValsString = eigValsString + " " + Math.round(eigVals[i]*1000)/1000.;
		System.out.println("The eigVals are: " + eigValsString);
		
		NearPD nearPd = new NearPD();
		nearPd.setKeepDiag(true);
		nearPd.calcNearPD(rho_matrix);
		Matrix rho_PDmatrix = nearPd.getX();
		
		String rhoPDString_Matlab="1.0000    0.3944    0.4262    0.4865    0.5863    0.7108    0.8179    0.8836    0.9155    0.9246    0.8882 " +
	    "0.3944    1.0000    0.9110    0.7905    0.6747    0.5635    0.4589    0.3607    0.2725    0.1959    0.1308 " +
	    "0.4262    0.9110    1.0000    0.8780    0.7590    0.6430    0.5320    0.4280    0.3320    0.2470    0.1729 " +
	    "0.4865    0.7905    0.8780    1.0000    0.8782    0.7588    0.6425    0.5316    0.4277    0.3315    0.2460 " +
	    "0.5863    0.6747    0.7590    0.8782    1.0000    0.8779    0.7587    0.6428    0.5318    0.4277    0.3315 " +
	    "0.7108    0.5635    0.6430    0.7588    0.8779    1.0000    0.8784    0.7593    0.6432    0.5324    0.4289 " +
	    "0.8179    0.4589    0.5320    0.6425    0.7587    0.8784    1.0000    0.8787    0.7595    0.6438    0.5337 " +
	    "0.8836    0.3607    0.4280    0.5316    0.6428    0.7593    0.8787    1.0000    0.8784    0.7597    0.6444 " +
	    "0.9155    0.2725    0.3320    0.4277    0.5318    0.6432    0.7595    0.8784    1.0000    0.8785    0.7600 " +
	    "0.9246    0.1959    0.2470    0.3315    0.4277    0.5324    0.6438    0.7597    0.8785    1.0000    0.8798 " +
	    "0.8882    0.1308    0.1729    0.2460    0.3315    0.4289    0.5337    0.6444    0.7600    0.8798    1.0000";
		
		//Output the PD matrix and the convergence parameters
		System.out.println("Testing NearPD: nearest PD matrix \n " +
						   "Rho_PD = \n");
		rho_PDmatrix.print(12,8);
		CholeskyDecomposition cholDecompPD = new CholeskyDecomposition(rho_PDmatrix);
		if (!cholDecompPD.isSPD()) {
			throw new RuntimeException("Error: Even after NearPD the matrix is not PD");
		}
	}

}
