package org.opensha.sha.simulators.stiffness;

import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * This is nearly a line-for-line translation into Java of Okada [1992]'s Fortran code;
 * The translation was first done into C by Keith Richards-Dinger in 2007, and then into
 * Java by Kevin Milner in 2020.
 * 
 * @author kevin
 *
 */
public class OkadaDisplaceStrainCalc {

	/*
	C********************************************************************   04680005
	C*****                                                          *****   04690005
	C*****    DISPLACEMENT AND STRAIN AT DEPTH                      *****   04700005
	C*****    DUE TO BURIED FINITE FAULT IN A SEMIINFINITE MEDIUM   *****   04710005
	C*****              CODED BY  Y.OKADA ... SEP.1991              *****   04720005
	C*****              REVISED ... NOV.1991, APR.1992, MAY.1993,   *****   04730005
	C*****                          JUL.1993                        *****   04740005
	C********************************************************************   04750005
	C                                                                       04760005
	C***** INPUT                                                            04770005
	C*****   ALPHA : MEDIUM CONSTANT  (LAMBDA+MYU)/(LAMBDA+2*MYU)           04780005
	C*****   X,Y,Z : COORDINATE OF OBSERVING POINT                          04790005
	C*****   DEPTH : DEPTH OF REFERENCE POINT                               04800005
	C*****   DIP   : DIP-ANGLE (DEGREE)                                     04810005
	C*****   AL1,AL2   : FAULT LENGTH RANGE                                 04820005
	C*****   AW1,AW2   : FAULT WIDTH RANGE                                  04830005
	C*****   DISL1-DISL3 : STRIKE-, DIP-, TENSILE-DISLOCATIONS              04840005
	C                                                                       04850005
	C***** OUTPUT                                                           04860005
	C*****   UX, UY, UZ  : DISPLACEMENT ( UNIT=(UNIT OF DISL)               04870005
	C*****   UXX,UYX,UZX : X-DERIVATIVE ( UNIT=(UNIT OF DISL) /             04880005
	C*****   UXY,UYY,UZY : Y-DERIVATIVE        (UNIT OF X,Y,Z,DEPTH,AL,AW) )04890005
	C*****   UXZ,UYZ,UZZ : Z-DERIVATIVE                                     04900005
	C*****   IRET        : RETURN CODE  ( =0....NORMAL,   =1....SINGULAR )  04910005
	C   
	 */

	public static class Displacement {
		/**
		 * (x,y,z)
		 */
		final double[] u;
		/**
		 * [x/y/z][x/y/z]
		 */
		final double[][] du;

		private Displacement(double[] u) {
			this.u = Arrays.copyOfRange(u, 0, 3);
			this.du =  new double[3][3];
			du[0][0] = u[3];
			du[1][0] = u[4];
			du[2][0] = u[5];
			du[0][1] = u[6];
			du[1][1] = u[7];
			du[2][1] = u[8];
			du[0][2] = u[9];
			du[1][2] = u[10];
			du[2][2] = u[11];
		}

		public Displacement(double[] u, double[][] du) {
			super();
			Preconditions.checkState(u.length == 3);
			Preconditions.checkState(du.length == 3);
			for (double[] d : du)
				Preconditions.checkState(d.length == 3);
			this.u = u;
			this.du = du;
		}
	}

	private static class C0 {
		double alp1, alp2, alp3, alp4, alp5;
		double sd;
		double cd; 
		double sdsd, cdcd, sdcd, s2d, c2d;
	}

	private static class C1 {
		double p,q,s,t,xy,x2,y2,d2,r,r2,r3,r5,qr,qrx,a3,a5,b3,c3;  
		double uy,vy,wy,uz,vz,wz;
	}

	private static class C2 {
		double xi2,et2,q2,r,r2,r3,r5,y,d,tt,alx,ale,x11,y11,x32,y32,  
		ey,ez,fy,fz,gy,gz,hy,hz;
	}

	public static Displacement dc3d(double alpha, double x, double y, double z,
			double depth, double dip, double al1, double al2,
			double aw1, double aw2, double disl1, double disl2,
			double disl3) {
		double[] xi = new double[2];
		double[] et = new double[2];
		double[] kxi = new double[2];
		double[] ket = new double[2];
		double[] u = new double[12];
		double[] du = new double[12];
		//	  double[] dua = new double[12];
		//	  double[] dub = new double[12];
		//	  double[] duc = new double[12];
		double eps = 1e-6;
		double aalpha, ddip, zz, dd1, dd2, dd3;
		double d, p, q;
		double r12, r21, r22;

		int i, j, k;

		//	  if (z > 0.0) fprintf(stderr, "** POSITIVE Z WAS GIVEN IN SUB-DC3D()\n");

		for (i=0; i<12; i++)
		{
			u[i] = 0;
			//	    dua[i] = 0;
			//	    dub[i] = 0;
			//	    duc[i] = 0;
		}

		aalpha = alpha;
		ddip = dip;

		C0 c0 = dccon0(aalpha, ddip);  /* add c0 */

		zz = z;
		dd1 = disl1;
		dd2 = disl2;
		dd3 = disl3;
		xi[0] = x - al1;
		xi[1] = x - al2;
		if (Math.abs(xi[0]) < eps) xi[0] = 0;
		if (Math.abs(xi[1]) < eps) xi[1] = 0;

		/*    
	C======================================                                 05170005
	C=====  REAL-SOURCE CONTRIBUTION  =====                                 05180005
	C======================================                                 05190005
		 */
		d = depth + z;
		p = y*c0.cd + d*c0.sd;
		q = y*c0.sd - d*c0.cd;
		et[0] = p - aw1;
		et[1] = p - aw2;
		if (Math.abs(q) < eps) q = 0;
		if (Math.abs(et[0]) < eps) et[0] = 0;
		if (Math.abs(et[1]) < eps) et[1] = 0;

		/*
	C--------------------------------                                       05280005
	C----- REJECT SINGULAR CASE -----                                       05290005
	C--------------------------------                                       05300005
		 */
		/*
	C----- ON FAULT EDGE                                                    05310014
		 */
		if (q == 0 && ( (xi[0]*xi[1] < 0 && et[0]*et[1] == 0) ||
				(et[0]*et[1] < 0 && xi[0]*xi[1] == 0) ) )
			return null;

		/*  
	C----- ON NEGATIVE EXTENSION OF FAULT EDGE                              05360014
		 */
		kxi[0]=0;                                                         
		kxi[1]=0;                                                         
		ket[0]=0;                                                         
		ket[1]=0;                                                         
		r12 = Math.sqrt(xi[0]*xi[0] + et[1]*et[1] + q*q);                          
		r21 = Math.sqrt(xi[1]*xi[1] + et[0]*et[0] + q*q);                           
		r22 = Math.sqrt(xi[1]*xi[1] + et[1]*et[1] + q*q);                           
		if(xi[0] < 0  &&  r21 + xi[1] < eps) kxi[0]=1;                  
		if(xi[0] < 0  &&  r22 + xi[1] < eps) kxi[1]=1;                  
		if(et[0] < 0  &&  r12 + et[1] < eps) ket[0]=1;                  
		if(et[0] < 0  &&  r22 + et[1] < eps) ket[1]=1;                  


		for (k=0; k<2; k++)
		{
			for (j=0; j<2; j++)
			{
				C2 c2 = dccon2(xi[j], et[k], q, c0.sd, c0.cd, kxi[k], ket[j]); /* add all c's */
				double dua[] = ua(xi[j], et[k], q, dd1, dd2, dd3, c0, c2);
				for (i=0; i<=9; i += 3)
				{
					du[i] = -dua[i];
					du[i+1] = -dua[i+1]*c0.cd + dua[i+2]*c0.sd;
					du[i+2] = -dua[i+1]*c0.sd - dua[i+2]*c0.cd;
					if (i == 9)
					{
						du[i] = -du[i];
						du[i+1] = -du[i+1];
						du[i+2] = -du[i+2];
					}
				}

				for (i=0; i<12; i++)
				{
					if (j + k != 1) u[i] += du[i];
					else u[i] -= du[i];
				}
			}
		}

		/*
	C=======================================                                05700005
	C=====  IMAGE-SOURCE CONTRIBUTION  =====                                05710005
	C=======================================                                05720005
		 */

		d = depth - z;                                                        
		p = y*c0.cd + d*c0.sd;                                                      
		q = y*c0.sd - d*c0.cd;                                                      
		et[0] = p - aw1;                                                      
		et[1] = p - aw2;                                                      
		if(Math.abs(q) < eps)  q = 0;                                         
		if(Math.abs(et[0]) < eps) et[0] = 0;                                 
		if(Math.abs(et[1]) < eps) et[1] = 0;                                  

		/*
	C--------------------------------                                       05810005
	C----- REJECT SINGULAR CASE -----                                       05820005
	C--------------------------------                                       05830005
		 */
		/*
	C----- ON FAULT EDGE                                                    05840015
		 */
		if (q == 0 && ( (xi[0]*xi[1] < 0 && et[0]*et[1] == 0) ||
				(et[0]*et[1] < 0 && xi[0]*xi[1] == 0) ) )
			return null;
		/*
	C----- ON NEGATIVE EXTENSION OF FAULT EDGE                              05890015
		 */
		kxi[0]=0;                                                         
		kxi[1]=0;                                                         
		ket[0]=0;                                                         
		ket[1]=0;                                                         
		r12 = Math.sqrt(xi[0]*xi[0] + et[1]*et[1] + q*q);                          
		r21 = Math.sqrt(xi[1]*xi[1] + et[0]*et[0] + q*q);                           
		r22 = Math.sqrt(xi[1]*xi[1] + et[1]*et[1] + q*q);                           
		if(xi[0] < 0  &&  r21 + xi[1] < eps) kxi[0]=1;                  
		if(xi[0] < 0  &&  r22 + xi[1] < eps) kxi[1]=1;                  
		if(et[0] < 0  &&  r12 + et[1] < eps) ket[0]=1;                  
		if(et[0] < 0  &&  r22 + et[1] < eps) ket[1]=1;                  

		for (k=0; k<2; k++)
		{
			for (j=0; j<2; j++)
			{
				C2 c2 = dccon2(xi[j], et[k], q, c0.sd, c0.cd, kxi[k], ket[j]); /* add c's  */                 
				double[] dua = ua(xi[j], et[k], q, dd1, dd2, dd3, c0, c2);                         
				double[] dub = ub(xi[j], et[k], q, dd1, dd2, dd3, c0, c2);                         
				double[] duc = uc(xi[j], et[k], q, zz, dd1, dd2, dd3, c0, c2);                     

				for (i=0; i<=9; i+=3)
				{
					du[i]=dua[i] + dub[i] + z*duc[i];                                
					du[i+1]=(dua[i+1] + dub[i+1] + z*duc[i+1])*c0.cd - 
							(dua[i+2] + dub[i+2] + z*duc[i+2])*c0.sd;                   
					du[i+2]=(dua[i+1] + dub[i+1] - z*duc[i+1])*c0.sd +
							(dua[i+2] + dub[i+2] - z*duc[i+2])*c0.cd;                   

					if (i == 9)
					{
						du[9] = du[9] + duc[0];                                         
						du[10] = du[10] + duc[1]*c0.cd - duc[2]*c0.sd;                            
						du[11] = du[11] - duc[1]*c0.sd - duc[2]*c0.cd;                            
					}
				}                                                        

				for (i=0; i<12; i++)
				{
					if (j+k != 1) u[i] += du[i];
					else u[i] -= du[i]; 
				}                                                        

			}
		}

		return new Displacement(u);
	}


	/*
	C********************************************************************   06640005
	C*****    DISPLACEMENT AND STRAIN AT DEPTH (PART-A)             *****   06650005
	C*****    DUE TO BURIED FINITE FAULT IN A SEMIINFINITE MEDIUM   *****   06660005
	C********************************************************************   06670005
	C                                                                       06680005
	C***** INPUT                                                            06690005
	C*****   XI,ET,Q : STATION COORDINATES IN FAULT SYSTEM                  06700005
	C*****   DISL1-DISL3 : STRIKE-, DIP-, TENSILE-DISLOCATIONS              06710005
	C***** OUTPUT                                                           06720005
	C*****   U(12) : DISPLACEMENT AND THEIR DERIVATIVES                     06730005
	 */

	static double[] ua(double xi, double et, double q, double disl1, double disl2, double disl3,
			C0 c0, C2 c2) {
		double pi2 = 6.283185307179586;
		double[] u = new double[12];
		double[] du = new double[12];
		double xy, qx, qy;
		int i;

		for (i=0; i<12; i++)
			u[i] = 0;

		xy = xi*c2.y11;                                                         
		qx = q *c2.x11;                                                         
		qy = q *c2.y11;                                                         

		/*
	C======================================                                 06850005
	C=====  STRIKE-SLIP CONTRIBUTION  =====                                 06860005
	C======================================                                 06870005
		 */
		if(disl1 != 0) 
		{                                          
			du[0] =    c2.tt/2 + c0.alp2*xi*qy;                                    
			du[1] =           c0.alp2*q/c2.r;                                      
			du[2] =   c0.alp1*c2.ale - c0.alp2*q*qy;                                     
			du[3] =  -c0.alp1*qy  - c0.alp2*c2.xi2*q*c2.y32;                                
			du[4] =          - c0.alp2*xi*q/c2.r3;                                  
			du[5] =   c0.alp1*xy  + c0.alp2*xi*c2.q2*c2.y32;                                
			du[6] =   c0.alp1*xy*c0.sd + c0.alp2*xi*c2.fy + c2.d/2*c2.x11;                  
			du[7] =                c0.alp2*c2.ey;                              
			du[8] =   c0.alp1*(c0.cd/c2.r + qy*c0.sd) - c0.alp2*q*c2.fy;                            
			du[9] =   c0.alp1*xy*c0.cd        + c0.alp2*xi*c2.fz + c2.y/2*c2.x11;                  
			du[10] =                    c0.alp2*c2.ez;                              
			du[11] = -c0.alp1*(c0.sd/c2.r-qy*c0.cd) - c0.alp2*q*c2.fz;                            

			for (i=0; i<12; i++) u[i] += disl1/pi2*du[i];                                       
		}                                                            


		/*
	C======================================                                 07040005
	C=====    DIP-SLIP CONTRIBUTION   =====                                 07050005
	C======================================                                 07060005
		 */
		if(disl2 != 0) 
		{                                         
			du[0] =           c0.alp2*q/c2.r;                                    
			du[1] =    c2.tt/2 + c0.alp2*et*qx;                                  
			du[2] = c0.alp1*c2.alx - c0.alp2*q*qx;                                   
			du[3] =        - c0.alp2*xi*q/c2.r3;                                  
			du[4] = -qy/2 - c0.alp2*et*q/c2.r3;                                  
			du[5] = c0.alp1/c2.r + c0.alp2*c2.q2/c2.r3;                                    
			du[6] =                      c0.alp2*c2.ey;                          
			du[7] = c0.alp1*c2.d*c2.x11 + xy/2*c0.sd + c0.alp2*et*c2.gy;                       
			du[8] = c0.alp1*c2.y*c2.x11          - c0.alp2*q*c2.gy;                        
			du[9] =                      c0.alp2*c2.ez;                          
			du[10] = c0.alp1*c2.y*c2.x11 + xy/2*c0.cd + c0.alp2*et*c2.gz;                       
			du[11] =-c0.alp1*c2.d*c2.x11          - c0.alp2*q*c2.gz;                        

			for (i=0; i<12; i++) u[i] += disl2/pi2*du[i];                                     
		}                                                           

		/*
	C========================================                               07230005
	C=====  TENSILE-FAULT CONTRIBUTION  =====                               07240005
	C========================================                               07250005
		 */
		if(disl3 != 0)
		{  
			du[0] = -c0.alp1*c2.ale - c0.alp2*q*qy;                                     
			du[1] = -c0.alp1*c2.alx - c0.alp2*q*qx;                                     
			du[2] =    c2.tt/2 - c0.alp2*(et*qx+xi*qy);                            
			du[3] = -c0.alp1*xy  + c0.alp2*xi*c2.q2*c2.y32;                                
			du[4] = -c0.alp1/c2.r   + c0.alp2*c2.q2/c2.r3;                                    
			du[5] = -c0.alp1*qy  - c0.alp2*q*c2.q2*c2.y32;                                 
			du[6] = -c0.alp1*(c0.cd/c2.r + qy*c0.sd)  - c0.alp2*q*c2.fy;                           
			du[7] = -c0.alp1*c2.y*c2.x11         - c0.alp2*q*c2.gy;                           
			du[8] = c0.alp1*(c2.d*c2.x11 + xy*c0.sd) + c0.alp2*q*c2.hy;                           
			du[9] = c0.alp1*(c0.sd/c2.r - qy*c0.cd)  - c0.alp2*q*c2.fz;                           
			du[10] = c0.alp1*c2.d*c2.x11         - c0.alp2*q*c2.gz;                           
			du[11] = c0.alp1*(c2.y*c2.x11+xy*c0.cd) + c0.alp2*q*c2.hz;                           
			for (i=0; i<12; i++) u[i] += disl3/pi2*du[i];                                      
		}    

		return u;
	}


	/*
	C********************************************************************   07480005
	C*****    DISPLACEMENT AND STRAIN AT DEPTH (PART-B)             *****   07490005
	C*****    DUE TO BURIED FINITE FAULT IN A SEMIINFINITE MEDIUM   *****   07500005
	C********************************************************************   07510005
	C                                                                       07520005
	C***** INPUT                                                            07530005
	C*****   XI,ET,Q : STATION COORDINATES IN FAULT SYSTEM                  07540005
	C*****   DISL1-DISL3 : STRIKE-, DIP-, TENSILE-DISLOCATIONS              07550005
	C***** OUTPUT                                                           07560005
	C*****   U(12) : DISPLACEMENT AND THEIR DERIVATIVES                     07570005
	 */

	static double[] ub(double xi, double et, double q, double disl1, double disl2, double disl3,
			C0 c0, C2 c2) {
		double[] u = new double[12];
		double[] du = new double[12];
		double rd, d11, aj1, aj2, aj3, aj4, aj5, aj6, ai1, ai2, ai3, ai4; 
		double ak1, ak2, ak3, ak4, x;
		double rd2, xy, qx, qy;
		double pi2 = 6.283185307179586;
		int i;

		rd = c2.r + c2.d;                                                          
		d11 = 1/(c2.r*rd);                                                   
		aj2 = xi*c2.y/rd*d11;                                                 
		aj5 =  - (c2.d + c2.y*c2.y/rd)*d11;                                             
		if (c0.cd != 0)
		{                                               
			if(xi == 0)
			{                                             
				ai4 = 0;
			}                                                      
			else 
			{                                                         
				x = Math.sqrt(c2.xi2 + c2.q2);                                             
				ai4 = 1/c0.cdcd*( xi/rd*c0.sdcd +                                    
						2*Math.atan((et*(x + q*c0.cd) + x*(c2.r + x)*c0.sd)/(xi*(c2.r + x)*c0.cd)) );      
			}                                                         
			ai3 = (c2.y*c0.cd/rd - c2.ale + c0.sd*Math.log(rd))/c0.cdcd;                            
			ak1 = xi*(d11 - c2.y11*c0.sd)/c0.cd;                                       
			ak3 = (q*c2.y11 - c2.y*d11)/c0.cd;                                          
			aj3 = (ak1 - aj2*c0.sd)/c0.cd;                                           
			aj6 = (ak3 - aj5*c0.sd)/c0.cd;                                           
		}
		else
		{                                                            
			rd2 = rd*rd;                                                     
			ai3 = (et/rd + c2.y*q/rd2 - c2.ale)/2;                                    
			ai4 = xi*c2.y/rd2/2;                                               
			ak1 = xi*q/rd*d11;                                               
			ak3 = c0.sd/rd*(c2.xi2*d11 - 1);                                        
			aj3 =  - xi/rd2*(c2.q2*d11 - 0.5);                                    
			aj6 =  - c2.y/rd2*(c2.xi2*d11 - 0.5);                                    
		}                                                           

		xy = xi*c2.y11;                                                         
		ai1 =  - xi/rd*c0.cd - ai4*c0.sd;                                              
		ai2 =  Math.log(rd) + ai3*c0.sd;                                              
		ak2 =  1/c2.r + ak3*c0.sd;                                                  
		ak4 =  xy*c0.cd - ak1*c0.sd;                                                 
		aj1 =  aj5*c0.cd - aj6*c0.sd;                                                
		aj4 =  - xy - aj2*c0.cd + aj3*c0.sd;                                             

		for (i=0; i<12; i++) u[i] = 0;                                                           
		qx = q*c2.x11;                                                          
		qy = q*c2.y11;                                                          

		/*
	C======================================                                 08030005
	C=====  STRIKE-SLIP CONTRIBUTION  =====                                 08040005
	C======================================                                 08050005
		 */
		if(disl1 != 0)
		{
			du[0] =  -xi*qy - c2.tt  - c0.alp3*ai1*c0.sd;                                   
			du[1] =  -q/c2.r       + c0.alp3*c2.y/rd*c0.sd;                                  
			du[2] =  q*qy      - c0.alp3*ai2*c0.sd;                                   
			du[3] =  c2.xi2*q*c2.y32  - c0.alp3*aj1*c0.sd;                                  
			du[4] =  xi*q/c2.r3    - c0.alp3*aj2*c0.sd;                                  
			du[5] =  -xi*c2.q2*c2.y32  - c0.alp3*aj3*c0.sd;                                  
			du[6] =  -xi*c2.fy - c2.d*c2.x11  + c0.alp3*(xy + aj4)*c0.sd;                           
			du[7] =  -c2.ey           + c0.alp3*(1/c2.r + aj5)*c0.sd;                         
			du[8] =  q*c2.fy         - c0.alp3*(qy - aj6)*c0.sd;                           
			du[9] =  -xi*c2.fz - c2.y*c2.x11  + c0.alp3*ak1*c0.sd;                                
			du[10] =  -c2.ez           + c0.alp3*c2.y*d11*c0.sd;                              
			du[11] =  q*c2.fz         + c0.alp3*ak2*c0.sd;                                
			for (i=0; i<12; i++) u[i] += disl1/pi2*du[i];                                       
		}                                                             


		/*
	C======================================                                 08220005
	C=====    DIP-SLIP CONTRIBUTION   =====                                 08230005
	C======================================                                 08240005
		 */
		if(disl2 != 0)
		{                                            
			du[0] = -q/c2.r      + c0.alp3*ai3*c0.sdcd;                                
			du[1] = -et*qx-c2.tt - c0.alp3*xi/rd*c0.sdcd;                              
			du[2] = q*qx     + c0.alp3*ai4*c0.sdcd;                                
			du[3] = xi*q/c2.r3     + c0.alp3*aj4*c0.sdcd;                             
			du[4] = et*q/c2.r3+qy  + c0.alp3*aj5*c0.sdcd;                             
			du[5] = -c2.q2/c2.r3       + c0.alp3*aj6*c0.sdcd;                             
			du[6] = -c2.ey          + c0.alp3*aj1*c0.sdcd;                             
			du[7] = -et*c2.gy-xy*c0.sd + c0.alp3*aj2*c0.sdcd;                             
			du[8] = q*c2.gy        + c0.alp3*aj3*c0.sdcd;                             
			du[9] = -c2.ez          - c0.alp3*ak3*c0.sdcd;                             
			du[10] = -et*c2.gz-xy*c0.cd - c0.alp3*xi*d11*c0.sdcd;                          
			du[11] = q*c2.gz        - c0.alp3*ak4*c0.sdcd;                             
			for (i=0; i<12; i++) u[i] += disl2/pi2*du[i];                                      
		}                                                            


		/*
	C========================================                               08410005
	C=====  TENSILE-FAULT CONTRIBUTION  =====                               08420005
	C========================================                               08430005
		 */
		if(disl3 != 0)
		{                                            
			du[0] = q*qy           - c0.alp3*ai3*c0.sdsd;                           
			du[1] = q*qx           + c0.alp3*xi/rd*c0.sdsd ;                        
			du[2] = et*qx+xi*qy - c2.tt - c0.alp3*ai4*c0.sdsd;                           
			du[3] = -xi*c2.q2*c2.y32 - c0.alp3*aj4*c0.sdsd;                                
			du[4] = -c2.q2/c2.r3     - c0.alp3*aj5*c0.sdsd;                                
			du[5] = q*c2.q2*c2.y32  - c0.alp3*aj6*c0.sdsd;                                
			du[6] = q*c2.fy - c0.alp3*aj1*c0.sdsd;                                     
			du[7] = q*c2.gy - c0.alp3*aj2*c0.sdsd;                                     
			du[8] = -q*c2.hy - c0.alp3*aj3*c0.sdsd;                                     
			du[9] = q*c2.fz + c0.alp3*ak3*c0.sdsd;                                     
			du[10] = q*c2.gz + c0.alp3*xi*d11*c0.sdsd;                                  
			du[11] = -q*c2.hz + c0.alp3*ak4*c0.sdsd;                                     
			for (i=0; i<12; i++) u[i] += disl3/pi2*du[i];                                       
		}                                                             

		return u;
	}                                                            



	/*
	C********************************************************************   08660005
	C*****    DISPLACEMENT AND STRAIN AT DEPTH (PART-C)             *****   08670005
	C*****    DUE TO BURIED FINITE FAULT IN A SEMIINFINITE MEDIUM   *****   08680005
	C********************************************************************   08690005
	C                                                                       08700005
	C***** INPUT                                                            08710005
	C*****   XI,ET,Q,Z   : STATION COORDINATES IN FAULT SYSTEM              08720005
	C*****   DISL1-DISL3 : STRIKE-, DIP-, TENSILE-DISLOCATIONS              08730005
	C***** OUTPUT                                                           08740005
	C*****   U(12) : DISPLACEMENT AND THEIR DERIVATIVES                     08750005
	 */      

	static double[] uc(double xi, double et, double q, double z, double disl1, double disl2, double disl3,
			C0 c0, C2 c2) {
		double[] u = new double[12];
		double[] du = new double[12];
		double c, x53, y53, z53, h, z32, ppy, ppz, y0, z0;
		double qq, qx, qy, qr, qqy, qqz, xy;
		double cqx, cdr, yy0;
		double pi2 = 6.283185307179586;
		int i;

		c = c2.d + z;                                                             
		x53 = (8*c2.r2 + 9*c2.r*xi + 3*c2.xi2)*c2.x11*c2.x11*c2.x11/c2.r2;                     
		y53 = (8*c2.r2 + 9*c2.r*et + 3*c2.et2)*c2.y11*c2.y11*c2.y11/c2.r2;                     
		h = q*c0.cd - z;                                                          
		z32 = c0.sd/c2.r3 - h*c2.y32;                                                   
		z53 = 3*c0.sd/c2.r5 - h*y53;                                                
		y0 = c2.y11 - c2.xi2*c2.y32;                                                    
		z0 = z32 - c2.xi2*z53;                                                    
		ppy = c0.cd/c2.r3 + q*c2.y32*c0.sd;                                                
		ppz = c0.sd/c2.r3 - q*c2.y32*c0.cd;                                                
		qq = z*c2.y32 + z32 + z0;                                                   
		qqy = 3*c*c2.d/c2.r5 - qq*c0.sd;                                               
		qqz = 3*c*c2.y/c2.r5 - qq*c0.cd + q*c2.y32;                                         
		xy = xi*c2.y11;                                                         
		qx = q*c2.x11;                                                          
		qy = q*c2.y11;                                                          
		qr = 3*q/c2.r5;                                                        
		cqx = c*q*x53;                                                       
		cdr = (c + c2.d)/c2.r3;                                                      
		yy0 = c2.y/c2.r3 - y0*c0.cd;                                                    

		for (i=0; i<12; i++) u[i] = 0;

		/*  
	C======================================                                 09050005
	C=====  STRIKE-SLIP CONTRIBUTION  =====                                 09060005
	C======================================                                 09070005
		 */
		if(disl1 != 0)
		{                                              
			du[0] = c0.alp4*xy*c0.cd           - c0.alp5*xi*q*z32;                     
			du[1] = c0.alp4*(c0.cd/c2.r+2*qy*c0.sd) - c0.alp5*c*q/c2.r3;                       
			du[2] = c0.alp4*qy*c0.cd           - c0.alp5*(c*et/c2.r3 - z*c2.y11+c2.xi2*z32);      
			du[3] = c0.alp4*y0*c0.cd                  - c0.alp5*q*z0;                  
			du[4] =-c0.alp4*xi*(c0.cd/c2.r3+2*q*c2.y32*c0.sd) + c0.alp5*c*xi*qr;               
			du[5] =-c0.alp4*xi*q*c2.y32*c0.cd            + c0.alp5*xi*(3*c*et/c2.r5-qq);    
			du[6] =-c0.alp4*xi*ppy*c0.cd    - c0.alp5*xi*qqy;                          
			du[7] = c0.alp4*2*(c2.d/c2.r3 - y0*c0.sd)*c0.sd - c2.y/c2.r3*c0.cd -                         
					c0.alp5*(cdr*c0.sd - et/c2.r3 - c*c2.y*qr);           
			du[8] =-c0.alp4*q/c2.r3+yy0*c0.sd  + c0.alp5*(cdr*c0.cd + c*c2.d*qr - (y0*c0.cd + q*z0)*c0.sd); 
			du[9] = c0.alp4*xi*ppz*c0.cd    - c0.alp5*xi*qqz;                          
			du[10] = c0.alp4*2*(c2.y/c2.r3 - y0*c0.cd)*c0.sd + c2.d/c2.r3*c0.cd - c0.alp5*(cdr*c0.cd + c*c2.d*qr);   
			du[11] =         yy0*c0.cd    - c0.alp5*(cdr*c0.sd - c*c2.y*qr - y0*c0.sdsd + q*z0*c0.cd); 
			for (i=0; i<12; i++) u[i] += disl1/pi2*du[i];                                       
		}                                                             

		/*  
	C======================================                                 09250005
	C=====    DIP-SLIP CONTRIBUTION   =====                                 09260005
	C======================================                                 09270005
		 */
		if(disl2 != 0)
		{                                            
			du[0] = c0.alp4*c0.cd/c2.r - qy*c0.sd - c0.alp5*c*q/c2.r3;                           
			du[1] = c0.alp4*c2.y*c2.x11       - c0.alp5*c*et*q*c2.x32;                       
			du[2] =     -c2.d*c2.x11 - xy*c0.sd - c0.alp5*c*(c2.x11 - c2.q2*c2.x32);                   
			du[3] = -c0.alp4*xi/c2.r3*c0.cd + c0.alp5*c*xi*qr + xi*q*c2.y32*c0.sd;                
			du[4] = -c0.alp4*c2.y/c2.r3     + c0.alp5*c*et*qr;                             
			du[5] =    c2.d/c2.r3 - y0*c0.sd + c0.alp5*c/c2.r3*(1-3*c2.q2/c2.r2);                  
			du[6] = -c0.alp4*et/c2.r3+y0*c0.sdsd - c0.alp5*(cdr*c0.sd-c*c2.y*qr);                
			du[7] = c0.alp4*(c2.x11-c2.y*c2.y*c2.x32) - c0.alp5*c*((c2.d+2*q*c0.cd)*c2.x32-c2.y*et*q*x53); 
			du[8] =  xi*ppy*c0.sd+c2.y*c2.d*c2.x32 + c0.alp5*c*((c2.y+2*q*c0.sd)*c2.x32-c2.y*c2.q2*x53);   
			du[9] =      -q/c2.r3+y0*c0.sdcd - c0.alp5*(cdr*c0.cd+c*c2.d*qr);                
			du[10] = c0.alp4*c2.y*c2.d*c2.x32       - c0.alp5*c*((c2.y-2*q*c0.sd)*c2.x32+c2.d*et*q*x53); 
			du[11] = -xi*ppz*c0.sd + c2.x11 - c2.d*c2.d*c2.x32 - c0.alp5*c*((c2.d-2*q*c0.cd)*c2.x32-c2.d*c2.q2*x53); 
			for (i=0; i<12; i++) u[i] += disl2/pi2*du[i];                                       
		}                                                             


		/*
	C========================================                               09440005
	C=====  TENSILE-FAULT CONTRIBUTION  =====                               09450005
	C========================================                               09460005
		 */
		if(disl3 != 0)
		{                                                                 
			du[0] = -c0.alp4*(c0.sd/c2.r+qy*c0.cd)   - c0.alp5*(z*c2.y11-c2.q2*z32);                
			du[1] = c0.alp4*2*xy*c0.sd+c2.d*c2.x11 - c0.alp5*c*(c2.x11-c2.q2*c2.x32);                
			du[2] = c0.alp4*(c2.y*c2.x11+xy*c0.cd)  +  c0.alp5*q*(c*et*c2.x32+xi*z32);           
			du[3] = c0.alp4*xi/c2.r3*c0.sd+xi*q*c2.y32*c0.cd + c0.alp5*xi*(3*c*et/c2.r5-2*z32-z0);
			du[4] = c0.alp4*2*y0*c0.sd-c2.d/c2.r3 + c0.alp5*c/c2.r3*(1-3*c2.q2/c2.r2);             
			du[5] = -c0.alp4*yy0           - c0.alp5*(c*et*qr-q*z0);                 
			du[6] = c0.alp4*(q/c2.r3+y0*c0.sdcd)   + c0.alp5*(z/c2.r3*c0.cd+c*c2.d*qr-q*z0*c0.sd);    
			du[7] = -c0.alp4*2*xi*ppy*c0.sd - c2.y*c2.d*c2.x32 +                               
					c0.alp5*c*((c2.y+2*q*c0.sd)*c2.x32 - c2.y*c2.q2*x53);            
			du[8] = -c0.alp4*(xi*ppy*c0.cd - c2.x11 + c2.y*c2.y*c2.x32) +                           
					c0.alp5*(c*((c2.d+2*q*c0.cd)*c2.x32 - c2.y*et*q*x53) + xi*qqy); 
			du[9] =  -et/c2.r3+y0*c0.cdcd - c0.alp5*(z/c2.r3*c0.sd-c*c2.y*qr-y0*c0.sdsd+q*z0*c0.cd);  
			du[10] = c0.alp4*2*xi*ppz*c0.sd - c2.x11+c2.d*c2.d*c2.x32 -                           
					c0.alp5*c*((c2.d-2*q*c0.cd)*c2.x32 - c2.d*c2.q2*x53);            
			du[11] = c0.alp4*(xi*ppz*c0.cd+c2.y*c2.d*c2.x32) +                                
					c0.alp5*(c*((c2.y-2*q*c0.sd)*c2.x32 + c2.d*et*q*x53) + xi*qqz); 
			for (i=0; i<12; i++) u[i] += disl3/pi2*du[i];                                      
		}                                                                 

		return u;
	}                                                     

	/*                                                          
	C*******************************************************************    09720005
	C*****   CALCULATE MEDIUM CONSTANTS AND FAULT-DIP CONSTANTS    *****    09730005
	C*******************************************************************    09740005
	C                                                                       09750005
	C***** INPUT                                                            09760005
	C*****   ALPHA : MEDIUM CONSTANT  (LAMBDA+MYU)/(LAMBDA+2*MYU)           09770005
	C*****   DIP   : DIP-ANGLE (DEGREE)                                     09780005
	C### CAUTION ### IF COS(DIP) IS SUFFICIENTLY SMALL, IT IS SET TO ZERO   09790005
	 */

	static C0 dccon0(double alpha, double dip) {
		C0 c0 = new C0();
		double eps = 1e-6, pi2 = 6.283185307179586;
		double p18;

		c0.alp1=(1-alpha)/2d;                                                
		c0.alp2= alpha/2d;                                                    
		c0.alp3=(1-alpha)/alpha;                                             
		c0.alp4= 1-alpha;                                                    
		c0.alp5= alpha;                                                       

		p18=pi2/360;                                                   
		c0.sd=Math.sin(dip*p18);                                                  
		c0.cd=Math.cos(dip*p18);                                                  

		if(Math.abs(c0.cd) < eps)
		{                                         
			c0.cd=0 ;                                                          
			if(c0.sd > 0) c0.sd= 1;                                             
			if(c0.sd < 0) c0.sd=-1;                                             
		}                                                             
		c0.sdsd=c0.sd*c0.sd;                                                        
		c0.cdcd=c0.cd*c0.cd;                                                        
		c0.sdcd=c0.sd*c0.cd;                                                       
		c0.s2d=2*c0.sdcd;                                                       
		c0.c2d=c0.cdcd-c0.sdsd;                                                     

		return c0;
	}                                                           


	/*
	C********************************************************************** 10090005
	C*****   CALCULATE STATION GEOMETRY CONSTANTS FOR POINT SOURCE    ***** 10100005
	C********************************************************************** 10110005
	C                                                                       10120005
	C***** INPUT                                                            10130005
	C*****   X,Y,D : STATION COORDINATES IN FAULT SYSTEM                    10140005
	C### CAUTION ### IF X,Y,D ARE SUFFICIENTLY SMALL, THc2.ey ARE SET TO ZERO  10150005
	 */

	static C1 dccon1(double x, double y, double d, C0 c0) {
		double eps = 1e-6;
		double r7;

		C1 c1 = new C1();

		if(Math.abs(x) < eps) x=0;                                          
		if(Math.abs(y) < eps) y=0;                                          
		if(Math.abs(d) < eps) d=0;                                          
		c1.p=y*c0.cd+d*c0.sd;                                                      
		c1.q=y*c0.sd-d*c0.cd;                                                      
		c1.s=c1.p*c0.sd+c1.q*c0.cd;                                                      
		c1.t=c1.p*c0.cd-c1.q*c0.sd;                                                      
		c1.xy=x*y;                                                           
		c1.x2=x*x;                                                           
		c1.y2=y*y;                                                           
		c1.d2=d*d;                                                           
		c1.r2=c1.x2+c1.y2+c1.d2;                                                      
		c1.r =Math.sqrt(c1.r2);                                                     
		if (c1.r == 0)
			return c1;                                               
		c1.r3=c1.r *c1.r2;                                                         
		c1.r5=c1.r3*c1.r2;                                                         
		r7=c1.r5*c1.r2;                                                         

		c1.a3=1-3*c1.x2/c1.r2;                                                   
		c1.a5=1-5*c1.x2/c1.r2;                                                   
		c1.b3=1-3*c1.y2/c1.r2;                                                   
		c1.c3=1-3*c1.d2/c1.r2;                                                   

		c1.qr=3*c1.q/c1.r5;                                                       
		c1.qrx=5*c1.qr*x/c1.r2;                                                   

		c1.uy=c0.sd-5*y*c1.q/c1.r2;                                                  
		c1.uz=c0.cd+5*d*c1.q/c1.r2;                                                  
		c1.vy=c1.s -5*y*c1.p*c1.q/c1.r2;                                                
		c1.vz=c1.t +5*d*c1.p*c1.q/c1.r2;                                                
		c1.wy=c1.uy+c0.sd;                                                         
		c1.wz=c1.uz+c0.cd;                                                         

		return c1;                                                           
	}  


	/*
	C********************************************************************** 10590005
	C*****   CALCULATE STATION GEOMETRY CONSTANTS FOR FINITE SOURCE   ***** 10600005
	C********************************************************************** 10610005
	C                                                                       10620005
	C***** INPUT                                                            10630005
	C*****   XI,ET,Q : STATION COORDINATES IN FAULT SYSTEM                  10640005
	C*****   SD,CD   : SIN, COS OF DIP-ANGLE                                10650005
	C*****   KXI,KET : KXI=1, KET=1 MEANS R+XI<EPS, R+ET<EPS, RESPECTIVELY  10660005
	C                                                                       10670005
	C### CAUTION ### IF XI,ET,Q ARE SUFFICIENTLY SMALL, THc2.ey ARE SET TO ZER010680005
	 */

	static C2 dccon2(double xi, double et, double q, double sd, double cd, double kxi, double ket) {

		C2 c2 = new C2();

		double eps = 1e-6;
		double rxi, ret;

		if(Math.abs(xi) < eps) xi=0;                                         
		if(Math.abs(et) < eps) et=0;                                         
		if(Math.abs( q) < eps)  q=0;                                         
		c2.xi2=xi*xi;                                                         
		c2.et2=et*et;                                                         
		c2.q2=q*q;                                                            
		c2.r2=c2.xi2+c2.et2+c2.q2;                                                     
		c2.r =Math.sqrt(c2.r2);                                                      
		if(c2.r == 0)
			return c2;                                                
		c2.r3=c2.r *c2.r2;                                                          
		c2.r5=c2.r3*c2.r2;                                                          
		c2.y =et*cd+q*sd;                                                    
		c2.d =et*sd-q*cd;                                                     

		if(q == 0) c2.tt=0;                                                           
		else c2.tt=Math.atan(xi*et/(q*c2.r));                                           

		if(kxi == 1)
		{                                               
			c2.alx=-Math.log(c2.r-xi);                                                 
			c2.x11=0;                                                          
			c2.x32=0;                                                          
		}
		else 
		{                                                             
			rxi=c2.r+xi;                                                        
			c2.alx=Math.log(rxi);                                                   
			c2.x11=1/(c2.r*rxi);                                                  
			c2.x32=(c2.r+rxi)*c2.x11*c2.x11/c2.r;                                           
		}                                                             

		if(ket == 1)
		{                                               
			c2.ale=-Math.log(c2.r-et);                                                 
			c2.y11=0;                                                          
			c2.y32=0;                                                          
		}
		else 
		{                                                             
			ret=c2.r+et;                                                        
			c2.ale=Math.log(ret);                                                   
			c2.y11=1/(c2.r*ret);                                                  
			c2.y32=(c2.r+ret)*c2.y11*c2.y11/c2.r;                                           
		}                                                             

		c2.ey=sd/c2.r-c2.y*q/c2.r3;                                                    
		c2.ez=cd/c2.r+c2.d*q/c2.r3;                                                    
		c2.fy=c2.d/c2.r3+c2.xi2*c2.y32*sd;                                               
		c2.fz=c2.y/c2.r3+c2.xi2*c2.y32*cd;                                                
		c2.gy=2*c2.x11*sd-c2.y*q*c2.x32;                                              
		c2.gz=2*c2.x11*cd+c2.d*q*c2.x32;                                              
		c2.hy=c2.d*q*c2.x32+xi*q*c2.y32*sd;                                            
		c2.hz=c2.y*q*c2.x32+xi*q*c2.y32*cd ;                                           

		return c2;                                                            
	}                                                       


	/* 
	C************************************************************           11310005
	C*****   CHECK SINGULARITIES RELATED TO R+XI AND R+ET   *****           11320005
	C************************************************************           11330005
	C                                                                       11340005
	C***** INPUT                                                            11350005
	C*****   X,P,Q : STATION COORDINATE                                     11360005
	C*****   AL1,AL2,AW1,AW2 : FAULT DIMENSIONS                             11370005
	C***** OUTPUT                                                           11380005
	C*****   KXI(2) : =1 IF R+XI<EPS                                        11390005
	C*****   KET(2) : =1 IF R+ET<EPS                                        11400005
	 */  
	//	this is not used
	//	
	//	void dccon3(double x, double p, double q, double al1, double al2, double aw1, 
	//	            double aw2, double *kxi, double *ket)
	//	{
	//	  double eps = 1e-6;
	//	  double et, xi, r, rxi, ret;
	//	  int j, k;
	//
	//	      kxi[0] = 0;                                                         
	//	      kxi[1] = 0;                                                         
	//	      ket[0] = 0;                                                         
	//	      ket[1] = 0;                                                         
	//	      if(x > al1 && p > aw2) return;                              
	//
	//	      for (k=0; k<2; k++)
	//	      {                                                     
	//	        if(k == 0) et = p-aw1;                                              
	//	        if(k == 1) et = p-aw2;                                              
	//	        for (j=0; j<2; j++)
	//	        {                                                     
	//	          if(j == 1) xi = x-al1;                                            
	//	          if(j == 2) xi = x-al2;                                            
	//	          r = sqrt(xi*xi+et*et+q*q);                                       
	//	          rxi = r+xi;                                                       
	//	          ret = r+et;                                                       
	//	          if(x < al1  &&  rxi < eps) kxi[k] = 1;                         
	//	          if(p < aw1  &&  ret < eps) ket[j] = 1;                         
	//	        }                                                         
	//	      }                                                         
	//
	//	      return;
	//	}

}
